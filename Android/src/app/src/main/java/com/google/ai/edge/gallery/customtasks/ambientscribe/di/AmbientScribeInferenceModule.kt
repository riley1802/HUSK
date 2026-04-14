/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.di

import android.content.Context
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.MoonshineTfliteEngine
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.SileroVad
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.TranscriptionEngine
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.VoiceActivityDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AmbientScribeInferenceModule {

	@Provides
	@Singleton
	fun provideTranscriptionEngine(
		@ApplicationContext context: Context,
	): TranscriptionEngine = MoonshineTfliteEngine(context)

	@Provides
	@Singleton
	fun provideVoiceActivityDetector(
		@ApplicationContext context: Context,
	): VoiceActivityDetector = SileroVad(context)
}
