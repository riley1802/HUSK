#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_google_ai_edge_gallery_runtime_WhisperJni_initModel(
		JNIEnv *env, jobject thiz, jstring model_path) {
	const char *path = env->GetStringUTFChars(model_path, nullptr);
	LOGI("Loading Whisper model from: %s", path);

	struct whisper_context_params cparams = whisper_context_default_params();
	struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);

	env->ReleaseStringUTFChars(model_path, path);

	if (ctx == nullptr) {
		LOGE("Failed to load Whisper model");
		return 0;
	}

	LOGI("Whisper model loaded successfully");
	return (jlong) ctx;
}

JNIEXPORT jobjectArray JNICALL
Java_com_google_ai_edge_gallery_runtime_WhisperJni_transcribe(
		JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray samples,
		jstring language, jint n_threads) {
	struct whisper_context *ctx = (struct whisper_context *) context_ptr;
	if (ctx == nullptr) {
		LOGE("Whisper context is null");
		return nullptr;
	}

	jfloat *audio_data = env->GetFloatArrayElements(samples, nullptr);
	jsize audio_length = env->GetArrayLength(samples);
	const char *lang = env->GetStringUTFChars(language, nullptr);

	// Configure transcription parameters.
	struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
	params.print_realtime = false;
	params.print_progress = false;
	params.print_timestamps = true;
	params.print_special = false;
	params.translate = false;
	params.language = lang;
	params.n_threads = n_threads;
	params.offset_ms = 0;
	params.no_context = true;
	params.single_segment = false;

	LOGI("Starting transcription: %d samples, %d threads, language=%s",
		 audio_length, n_threads, lang);

	whisper_reset_timings(ctx);

	int result = whisper_full(ctx, params, audio_data, audio_length);

	env->ReleaseFloatArrayElements(samples, audio_data, JNI_ABORT);
	env->ReleaseStringUTFChars(language, lang);

	if (result != 0) {
		LOGE("Whisper transcription failed with code %d", result);
		return nullptr;
	}

	whisper_print_timings(ctx);

	// Build array of Segment objects.
	int n_segments = whisper_full_n_segments(ctx);
	LOGI("Transcription complete: %d segments", n_segments);

	// Find the Segment class.
	jclass segmentClass = env->FindClass(
			"com/google/ai/edge/gallery/runtime/WhisperJni$Segment");
	if (segmentClass == nullptr) {
		LOGE("Failed to find Segment class");
		return nullptr;
	}

	jmethodID segmentCtor = env->GetMethodID(segmentClass, "<init>",
											  "(Ljava/lang/String;JJ)V");
	if (segmentCtor == nullptr) {
		LOGE("Failed to find Segment constructor");
		return nullptr;
	}

	jobjectArray segmentArray = env->NewObjectArray(n_segments, segmentClass, nullptr);

	for (int i = 0; i < n_segments; i++) {
		const char *text = whisper_full_get_segment_text(ctx, i);
		int64_t t0 = whisper_full_get_segment_t0(ctx, i); // in centiseconds (10ms units)
		int64_t t1 = whisper_full_get_segment_t1(ctx, i);

		// Convert centiseconds to milliseconds.
		jlong startMs = t0 * 10;
		jlong endMs = t1 * 10;

		jstring jtext = env->NewStringUTF(text);
		jobject segment = env->NewObject(segmentClass, segmentCtor, jtext, startMs, endMs);
		env->SetObjectArrayElement(segmentArray, i, segment);

		env->DeleteLocalRef(jtext);
		env->DeleteLocalRef(segment);
	}

	return segmentArray;
}

JNIEXPORT void JNICALL
Java_com_google_ai_edge_gallery_runtime_WhisperJni_freeModel(
		JNIEnv *env, jobject thiz, jlong context_ptr) {
	struct whisper_context *ctx = (struct whisper_context *) context_ptr;
	if (ctx != nullptr) {
		whisper_free(ctx);
		LOGI("Whisper model freed");
	}
}

JNIEXPORT jstring JNICALL
Java_com_google_ai_edge_gallery_runtime_WhisperJni_getSystemInfo(
		JNIEnv *env, jobject thiz) {
	const char *info = whisper_print_system_info();
	return env->NewStringUTF(info);
}

} // extern "C"
