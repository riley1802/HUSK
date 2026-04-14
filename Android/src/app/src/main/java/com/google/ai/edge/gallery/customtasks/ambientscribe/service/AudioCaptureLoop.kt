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

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Captures 16kHz mono PCM float audio from the device microphone and emits ~100ms chunks
 * as a cold [Flow].
 *
 * The flow opens [AudioRecord] on collection, drains samples in tight loops, and tears the
 * recorder down in a terminal [kotlinx.coroutines.flow.onCompletion]-like finally block.
 *
 * This class performs no buffering beyond a single read-sized [FloatArray] per emission; the
 * downstream [ChunkDispatcher] is responsible for window accumulation.
 */
class AudioCaptureLoop(
	private val sampleRate: Int = 16_000,
	private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
	private val audioFormat: Int = AudioFormat.ENCODING_PCM_FLOAT,
) {

	companion object {
		private const val TAG = "AudioCaptureLoop"

		/** Target emission period: 100ms worth of samples per chunk. */
		private const val EMISSION_MS = 100L
	}

	/**
	 * Opens [AudioRecord] and emits raw float PCM chunks as they arrive. Emits roughly every
	 * [EMISSION_MS] milliseconds (1600 samples at 16kHz).
	 *
	 * Completes when the collecting coroutine is cancelled. Non-fatal read errors are logged
	 * and the loop continues so transient device issues do not kill the capture pipeline.
	 *
	 * @throws MicrophoneUnavailableException if the RECORD_AUDIO permission is missing or the
	 *   underlying [AudioRecord] cannot be initialized.
	 */
	fun capture(): Flow<FloatArray> = flow {
		val samplesPerEmission = (sampleRate * EMISSION_MS / 1000L).toInt()
		val minBufferBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
		if (minBufferBytes <= 0) {
			throw MicrophoneUnavailableException(
				"AudioRecord.getMinBufferSize returned $minBufferBytes"
			)
		}
		// Oversize the native buffer so small scheduling hiccups don't drop samples.
		val nativeBufferBytes = minBufferBytes * 4

		val record = try {
			AudioRecord(
				MediaRecorder.AudioSource.MIC,
				sampleRate,
				channelConfig,
				audioFormat,
				nativeBufferBytes,
			)
		} catch (e: SecurityException) {
			throw MicrophoneUnavailableException("RECORD_AUDIO permission missing", e)
		} catch (e: IllegalArgumentException) {
			throw MicrophoneUnavailableException("Invalid AudioRecord params", e)
		}

		if (record.state != AudioRecord.STATE_INITIALIZED) {
			record.release()
			throw MicrophoneUnavailableException("AudioRecord did not initialize")
		}

		try {
			record.startRecording()
			if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
				throw MicrophoneUnavailableException("AudioRecord failed to start recording")
			}
			val buffer = FloatArray(samplesPerEmission)
			while (true) {
				val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
				when {
					read == AudioRecord.ERROR_INVALID_OPERATION -> {
						Log.w(TAG, "AudioRecord.read ERROR_INVALID_OPERATION; continuing")
						continue
					}
					read == AudioRecord.ERROR_BAD_VALUE -> {
						Log.w(TAG, "AudioRecord.read ERROR_BAD_VALUE; continuing")
						continue
					}
					read == AudioRecord.ERROR_DEAD_OBJECT -> {
						Log.w(TAG, "AudioRecord.read ERROR_DEAD_OBJECT; stopping capture")
						break
					}
					read < 0 -> {
						Log.w(TAG, "AudioRecord.read returned $read; continuing")
						continue
					}
					read == 0 -> continue
					else -> {
						// Emit only the valid prefix; safer than assuming the full buffer filled.
						val chunk = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
						emit(chunk)
					}
				}
			}
		} finally {
			try {
				if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
					record.stop()
				}
			} catch (e: IllegalStateException) {
				Log.w(TAG, "AudioRecord.stop() failed", e)
			}
			record.release()
		}
	}.flowOn(Dispatchers.IO)
}

/** Thrown when [AudioCaptureLoop] cannot open the microphone. */
class MicrophoneUnavailableException(message: String, cause: Throwable? = null) :
	RuntimeException(message, cause)
