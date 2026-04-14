/*
 * Copyright 2026 Riley Thomason
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.common

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.min

/**
 * Decodes any audio or video file to 16kHz mono PCM float samples
 * using Android's native MediaExtractor + MediaCodec APIs.
 *
 * For large files, decoded PCM is spooled to a temp file to avoid OOM.
 * The final FloatArray is read back in a memory-efficient way.
 *
 * Supported audio formats: M4A/AAC, MP3, OGG/Vorbis, FLAC, WAV, AMR, OPUS
 * Supported video formats (audio extraction): MP4, MKV, 3GP, WebM, MOV
 */
class AudioDecoder(private val context: Context) {

	companion object {
		private const val TAG = "AudioDecoder"
		private const val TARGET_SAMPLE_RATE = 16000
		private const val TIMEOUT_US = 10_000L
		// Decode to temp file if estimated PCM would exceed this (50MB).
		private const val MEMORY_THRESHOLD_BYTES = 50 * 1024 * 1024L
	}

	data class DecodedAudio(
		val samples: FloatArray,
		val sampleRate: Int,
		val durationMs: Long,
	)

	/**
	 * Decode audio from any supported audio/video URI to 16kHz mono float PCM.
	 *
	 * @param uri Content URI of the audio or video file.
	 * @param onProgress Optional callback with progress [0.0, 1.0].
	 * @return DecodedAudio with normalized float samples, or null if decoding fails.
	 */
	suspend fun decode(
		uri: Uri,
		onProgress: ((Float) -> Unit)? = null,
	): DecodedAudio? = withContext(Dispatchers.Default) {
		val extractor = MediaExtractor()
		var tempFile: File? = null
		try {
			extractor.setDataSource(context, uri, null)

			val audioTrackIndex = findAudioTrack(extractor)
			if (audioTrackIndex < 0) {
				Log.e(TAG, "No audio track found in $uri")
				return@withContext null
			}

			extractor.selectTrack(audioTrackIndex)
			val format = extractor.getTrackFormat(audioTrackIndex)
			val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
			val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
			val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
			val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
				format.getLong(MediaFormat.KEY_DURATION)
			} else {
				0L
			}

			Log.d(TAG, "Audio track: mime=$mime, sampleRate=$sourceSampleRate, channels=$sourceChannels, durationUs=$durationUs")

			// Estimate decoded PCM size to decide memory vs file strategy.
			val estimatedPcmBytes = if (durationUs > 0) {
				(durationUs / 1_000_000.0 * sourceSampleRate * sourceChannels * 2).toLong()
			} else {
				MEMORY_THRESHOLD_BYTES + 1 // Unknown duration — use file strategy.
			}
			val useFileStrategy = estimatedPcmBytes > MEMORY_THRESHOLD_BYTES

			if (useFileStrategy) {
				Log.d(TAG, "Large file detected (~${estimatedPcmBytes / 1024 / 1024}MB PCM), using temp file strategy")
			}

			val codec = MediaCodec.createDecoderByType(mime)
			codec.configure(format, null, null, 0)
			codec.start()

			val rawPcmBytes: Long
			if (useFileStrategy) {
				tempFile = File.createTempFile("audiodecode_", ".pcm", context.cacheDir)
				rawPcmBytes = decodeToFile(extractor, codec, tempFile, durationUs, onProgress)
			} else {
				val rawPcm = decodeToMemory(extractor, codec, durationUs, onProgress)
				codec.stop()
				codec.release()

				if (rawPcm.isEmpty()) {
					Log.e(TAG, "Decoded zero samples")
					return@withContext null
				}

				return@withContext convertToDecodedAudio(rawPcm, sourceSampleRate, sourceChannels)
			}

			codec.stop()
			codec.release()

			if (rawPcmBytes == 0L) {
				Log.e(TAG, "Decoded zero bytes")
				return@withContext null
			}

			// Read from temp file and convert.
			val result = convertTempFileToDecodedAudio(tempFile!!, rawPcmBytes, sourceSampleRate, sourceChannels)
			result
		} catch (e: Exception) {
			Log.e(TAG, "Failed to decode audio from $uri", e)
			null
		} finally {
			extractor.release()
			tempFile?.delete()
		}
	}

	private fun findAudioTrack(extractor: MediaExtractor): Int {
		for (i in 0 until extractor.trackCount) {
			val format = extractor.getTrackFormat(i)
			val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
			if (mime.startsWith("audio/")) return i
		}
		return -1
	}

	/**
	 * Decode to memory — for small files (< 50MB PCM).
	 */
	private suspend fun decodeToMemory(
		extractor: MediaExtractor,
		codec: MediaCodec,
		durationUs: Long,
		onProgress: ((Float) -> Unit)?,
	): ByteArray = withContext(Dispatchers.Default) {
		val outputChunks = mutableListOf<ByteArray>()
		val bufferInfo = MediaCodec.BufferInfo()
		var inputDone = false
		var outputDone = false

		while (!outputDone && isActive) {
			if (!inputDone) {
				val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
				if (inputBufferIndex >= 0) {
					val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
					val sampleSize = extractor.readSampleData(inputBuffer, 0)
					if (sampleSize < 0) {
						codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
						inputDone = true
					} else {
						val pts = extractor.sampleTime
						codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, pts, 0)
						extractor.advance()
						if (onProgress != null && durationUs > 0) {
							onProgress((pts.toFloat() / durationUs).coerceIn(0f, 1f))
						}
					}
				}
			}

			val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
			if (outputBufferIndex >= 0) {
				if (bufferInfo.size > 0) {
					codec.getOutputBuffer(outputBufferIndex)?.let { outputBuffer ->
						val chunk = ByteArray(bufferInfo.size)
						outputBuffer.position(bufferInfo.offset)
						outputBuffer.get(chunk)
						outputChunks.add(chunk)
					}
				}
				codec.releaseOutputBuffer(outputBufferIndex, false)
				if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
			}
		}

		onProgress?.invoke(1.0f)

		val totalSize = outputChunks.sumOf { it.size }
		val result = ByteArray(totalSize)
		var offset = 0
		for (chunk in outputChunks) {
			System.arraycopy(chunk, 0, result, offset, chunk.size)
			offset += chunk.size
		}
		result
	}

	/**
	 * Decode to temp file — for large files to avoid OOM.
	 * Returns total bytes written.
	 */
	private suspend fun decodeToFile(
		extractor: MediaExtractor,
		codec: MediaCodec,
		file: File,
		durationUs: Long,
		onProgress: ((Float) -> Unit)?,
	): Long = withContext(Dispatchers.Default) {
		val raf = RandomAccessFile(file, "rw")
		val bufferInfo = MediaCodec.BufferInfo()
		var inputDone = false
		var outputDone = false
		var totalBytes = 0L

		while (!outputDone && isActive) {
			if (!inputDone) {
				val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
				if (inputBufferIndex >= 0) {
					val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
					val sampleSize = extractor.readSampleData(inputBuffer, 0)
					if (sampleSize < 0) {
						codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
						inputDone = true
					} else {
						val pts = extractor.sampleTime
						codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, pts, 0)
						extractor.advance()
						if (onProgress != null && durationUs > 0) {
							onProgress((pts.toFloat() / durationUs).coerceIn(0f, 0.9f))
						}
					}
				}
			}

			val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
			if (outputBufferIndex >= 0) {
				if (bufferInfo.size > 0) {
					codec.getOutputBuffer(outputBufferIndex)?.let { outputBuffer ->
						val chunk = ByteArray(bufferInfo.size)
						outputBuffer.position(bufferInfo.offset)
						outputBuffer.get(chunk)
						raf.write(chunk)
						totalBytes += chunk.size
					}
				}
				codec.releaseOutputBuffer(outputBufferIndex, false)
				if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
			}
		}

		raf.close()
		onProgress?.invoke(1.0f)
		totalBytes
	}

	private fun convertToDecodedAudio(rawPcm: ByteArray, sourceSampleRate: Int, sourceChannels: Int): DecodedAudio {
		val shortSamples = bytesToShorts(rawPcm)
		val monoSamples = downmixToMono(shortSamples, sourceChannels)
		val resampledSamples = resampleToTarget(monoSamples, sourceSampleRate)
		val floatSamples = shortsToFloats(resampledSamples)
		val durationMs = (floatSamples.size.toLong() * 1000L) / TARGET_SAMPLE_RATE
		Log.d(TAG, "Decoded ${floatSamples.size} samples, duration=${durationMs}ms")
		return DecodedAudio(samples = floatSamples, sampleRate = TARGET_SAMPLE_RATE, durationMs = durationMs)
	}

	/**
	 * Convert a temp file of raw PCM to DecodedAudio.
	 * Processes in chunks to avoid loading everything into memory at once.
	 */
	private fun convertTempFileToDecodedAudio(
		file: File,
		totalBytes: Long,
		sourceSampleRate: Int,
		sourceChannels: Int,
	): DecodedAudio? {
		val raf = RandomAccessFile(file, "r")

		// Calculate output size.
		val totalShorts = totalBytes / 2
		val totalMonoSamples = totalShorts / sourceChannels
		val ratio = TARGET_SAMPLE_RATE.toDouble() / sourceSampleRate
		val outputSamples = (totalMonoSamples * ratio).toInt()

		Log.d(TAG, "Converting temp file: ${totalBytes / 1024 / 1024}MB PCM -> $outputSamples float samples")

		// Process in chunks: read 30 seconds of source at a time.
		val chunkSrcSamples = sourceSampleRate * 30 * sourceChannels // 30 sec of source interleaved
		val chunkBytes = chunkSrcSamples * 2
		val outputFloats = FloatArray(outputSamples)
		var outputOffset = 0
		var fileOffset = 0L

		while (fileOffset < totalBytes) {
			val remaining = totalBytes - fileOffset
			val readSize = min(chunkBytes.toLong(), remaining).toInt()
			val buffer = ByteArray(readSize)
			raf.seek(fileOffset)
			raf.readFully(buffer)
			fileOffset += readSize

			val shortSamples = bytesToShorts(buffer)
			val monoSamples = downmixToMono(shortSamples, sourceChannels)
			val resampledSamples = resampleToTarget(monoSamples, sourceSampleRate)

			val copyLen = min(resampledSamples.size, outputSamples - outputOffset)
			for (i in 0 until copyLen) {
				outputFloats[outputOffset + i] = resampledSamples[i].toFloat() / 32768.0f
			}
			outputOffset += copyLen
		}

		raf.close()

		val durationMs = (outputOffset.toLong() * 1000L) / TARGET_SAMPLE_RATE
		Log.d(TAG, "Temp file conversion done: $outputOffset samples, ${durationMs}ms")

		// Trim to actual output size if needed.
		val finalSamples = if (outputOffset < outputSamples) {
			outputFloats.copyOfRange(0, outputOffset)
		} else {
			outputFloats
		}

		return DecodedAudio(samples = finalSamples, sampleRate = TARGET_SAMPLE_RATE, durationMs = durationMs)
	}

	private fun bytesToShorts(bytes: ByteArray): ShortArray {
		val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer()
		val shorts = ShortArray(shortBuffer.remaining())
		shortBuffer.get(shorts)
		return shorts
	}

	private fun downmixToMono(samples: ShortArray, channels: Int): ShortArray {
		if (channels <= 1) return samples
		val monoLength = samples.size / channels
		val mono = ShortArray(monoLength)
		for (i in mono.indices) {
			var sum = 0L
			for (ch in 0 until channels) {
				val idx = i * channels + ch
				if (idx < samples.size) sum += samples[idx].toLong()
			}
			mono[i] = (sum / channels).toInt().toShort()
		}
		return mono
	}

	private fun resampleToTarget(samples: ShortArray, sourceSampleRate: Int): ShortArray {
		if (sourceSampleRate == TARGET_SAMPLE_RATE) return samples
		val ratio = TARGET_SAMPLE_RATE.toDouble() / sourceSampleRate
		val outputLength = (samples.size * ratio).toInt()
		val resampled = ShortArray(outputLength)
		for (i in resampled.indices) {
			val position = i / ratio
			val index1 = floor(position).toInt()
			val index2 = index1 + 1
			val fraction = position - index1
			val sample1 = if (index1 < samples.size) samples[index1].toDouble() else 0.0
			val sample2 = if (index2 < samples.size) samples[index2].toDouble() else 0.0
			resampled[i] = (sample1 * (1 - fraction) + sample2 * fraction).toInt().toShort()
		}
		return resampled
	}

	private fun shortsToFloats(samples: ShortArray): FloatArray {
		val floats = FloatArray(samples.size)
		for (i in samples.indices) {
			floats[i] = samples[i].toFloat() / 32768.0f
		}
		return floats
	}
}
