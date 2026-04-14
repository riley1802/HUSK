/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.service

import android.util.Log
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEvent
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEventDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadata
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadataDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.RewriteState
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegment
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegmentDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.AudioEventClassifier
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.AudioEventLabel
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.ClassifierNotReadyException
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.EngineNotReadyException
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.TranscriptionEngine
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.TranscriptionResult
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.VoiceActivityDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Accumulates the capture-loop audio into fixed-duration windows and dispatches each one
 * through VAD, YAMNet, and (conditionally) the ASR engine, then persists the results.
 *
 * Processing per window happens on a supervised child coroutine so a failure in one window
 * doesn't tear down the outer capture flow. Errors inside engines are logged and swallowed;
 * the next window proceeds.
 *
 * @param clock injected time source (defaults to wall-clock). Tests drive window boundaries
 *   by computing chunk end timestamps via the incoming audio-sample math, not via the clock,
 *   so the clock is used only to stamp `start/endTimestamp` on persisted rows.
 */
class ChunkDispatcher(
	private val vad: VoiceActivityDetector,
	private val classifier: AudioEventClassifier,
	private val transcriber: TranscriptionEngine,
	private val transcriptDao: TranscriptSegmentDao,
	private val audioEventDao: AudioEventDao,
	private val metadataDao: DailyMetadataDao,
	private val clock: () -> Long = System::currentTimeMillis,
	private val zoneId: ZoneId = ZoneId.systemDefault(),
	private val chunkDurationMs: Long = 30_000L,
	private val sampleRate: Int = 16_000,
	private val yamnetTopK: Int = 3,
	private val yamnetMinConfidence: Float = 0.3f,
) {

	private val windowSamples: Int = (chunkDurationMs * sampleRate / 1000L).toInt()

	/**
	 * Collects the upstream audio flow, builds 30s windows, and dispatches each completed
	 * window to [processWindow] on a supervised child coroutine.
	 *
	 * Suspends until the upstream flow completes or the caller cancels the enclosing scope.
	 */
	suspend fun run(audio: Flow<FloatArray>) = supervisorScope {
		val accumulator = FloatArray(windowSamples)
		var filled = 0
		var windowStartTs = clock()

		audio.collect { chunk ->
			var offset = 0
			while (offset < chunk.size) {
				val remainingInWindow = windowSamples - filled
				val copyLen = minOf(remainingInWindow, chunk.size - offset)
				System.arraycopy(chunk, offset, accumulator, filled, copyLen)
				filled += copyLen
				offset += copyLen

				if (filled == windowSamples) {
					val windowCopy = accumulator.copyOf()
					val startTs = windowStartTs
					val endTs = clock()
					launch { processWindow(windowCopy, startTs, endTs) }
					filled = 0
					windowStartTs = endTs
				}
			}
		}
	}

	private suspend fun processWindow(window: FloatArray, startTs: Long, endTs: Long) {
		try {
			val speech = try {
				vad.detectSpeech(window)
			} catch (e: Throwable) {
				if (e is CancellationException) throw e
				Log.w(TAG, "VAD failed for window startTs=$startTs", e)
				false
			}

			val (transcription, labels) = coroutineScope {
				val transcriptionDeferred = async {
					if (!speech) {
						null
					} else {
						try {
							transcriber.transcribe(window, "en")
						} catch (e: EngineNotReadyException) {
							Log.w(TAG, "Transcriber not ready; skipping transcript write", e)
							null
						} catch (e: Throwable) {
							if (e is CancellationException) throw e
							Log.w(TAG, "Transcription failed for window startTs=$startTs", e)
							null
						}
					}
				}
				val labelsDeferred = async {
					try {
						classifier.classify(window, topK = yamnetTopK, minConfidence = yamnetMinConfidence)
					} catch (e: ClassifierNotReadyException) {
						Log.w(TAG, "Classifier not ready; skipping event writes", e)
						emptyList()
					} catch (e: Throwable) {
						if (e is CancellationException) throw e
						Log.w(TAG, "Classifier failed for window startTs=$startTs", e)
						emptyList()
					}
				}
				transcriptionDeferred.await() to labelsDeferred.await()
			}

			val date = Instant.ofEpochMilli(startTs).atZone(zoneId).toLocalDate()
			val durationMs = endTs - startTs

			if (transcription != null && transcription.text.isNotBlank()) {
				writeTranscript(date, startTs, endTs, durationMs, transcription)
			}
			if (labels.isNotEmpty()) {
				writeAudioEvents(date, startTs, durationMs, labels)
			}
			updateDailyMetadata(date, startTs, endTs, durationMs, transcription)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Throwable) {
			Log.e(TAG, "processWindow failed for window startTs=$startTs; dropping window", e)
		}
	}

	private suspend fun writeTranscript(
		date: LocalDate,
		startTs: Long,
		endTs: Long,
		durationMs: Long,
		result: TranscriptionResult,
	) {
		val wordCount = result.text.trim().split(WHITESPACE_REGEX).count { it.isNotBlank() }
		transcriptDao.insert(
			TranscriptSegment(
				date = date,
				startTimestamp = startTs,
				endTimestamp = endTs,
				text = result.text,
				confidence = result.confidence,
				durationMs = durationMs,
				wordCount = wordCount,
			)
		)
	}

	private suspend fun writeAudioEvents(
		date: LocalDate,
		startTs: Long,
		durationMs: Long,
		labels: List<AudioEventLabel>,
	) {
		for (label in labels) {
			audioEventDao.insert(
				AudioEvent(
					date = date,
					timestamp = startTs,
					durationMs = durationMs,
					label = label.displayName,
					confidence = label.confidence,
					rewriteState = RewriteState.PENDING,
				)
			)
		}
	}

	private suspend fun updateDailyMetadata(
		date: LocalDate,
		startTs: Long,
		endTs: Long,
		durationMs: Long,
		transcription: TranscriptionResult?,
	) {
		val existing = metadataDao.getByDate(date)
		val addedWords = if (transcription != null && transcription.text.isNotBlank()) {
			transcription.text.trim().split(WHITESPACE_REGEX).count { it.isNotBlank() }
		} else {
			0
		}
		val addedSegments = if (transcription != null && transcription.text.isNotBlank()) 1 else 0
		val updated = if (existing == null) {
			DailyMetadata(
				date = date,
				totalDurationMs = durationMs,
				totalWordCount = addedWords,
				totalSegments = addedSegments,
				firstSegmentTime = if (addedSegments > 0) startTs else null,
				lastSegmentTime = if (addedSegments > 0) endTs else null,
			)
		} else {
			existing.copy(
				totalDurationMs = existing.totalDurationMs + durationMs,
				totalWordCount = existing.totalWordCount + addedWords,
				totalSegments = existing.totalSegments + addedSegments,
				firstSegmentTime = when {
					addedSegments == 0 -> existing.firstSegmentTime
					existing.firstSegmentTime == null -> startTs
					else -> minOf(existing.firstSegmentTime, startTs)
				},
				lastSegmentTime = when {
					addedSegments == 0 -> existing.lastSegmentTime
					existing.lastSegmentTime == null -> endTs
					else -> maxOf(existing.lastSegmentTime, endTs)
				},
			)
		}
		metadataDao.upsert(updated)
	}

	companion object {
		private const val TAG = "ChunkDispatcher"
		private val WHITESPACE_REGEX = Regex("\\s+")
	}
}
