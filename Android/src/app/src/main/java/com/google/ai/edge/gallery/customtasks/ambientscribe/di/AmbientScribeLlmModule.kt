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
import com.google.ai.edge.gallery.customtasks.ambientscribe.llm.EventDescriptionRewriter
import com.google.ai.edge.gallery.customtasks.ambientscribe.llm.GemmaClient
import com.google.ai.edge.gallery.customtasks.ambientscribe.llm.GemmaEventDescriptionRewriter
import com.google.ai.edge.gallery.customtasks.ambientscribe.llm.GemmaModelResolver
import com.google.ai.edge.gallery.customtasks.ambientscribe.llm.LlmChatGemmaClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the Ambient Scribe event-rewrite pipeline.
 *
 * Binds [EventDescriptionRewriter] to its Gemma-backed implementation and provides a
 * [GemmaClient] that resolves the currently-downloaded Gemma model through
 * [GemmaModelResolver] (shared with the app's Model Manager). When no Gemma model has
 * been downloaded the client reports not-ready and the rewriter is a no-op.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AmbientScribeLlmModule {

	@Binds
	@Singleton
	abstract fun bindEventDescriptionRewriter(
		impl: GemmaEventDescriptionRewriter,
	): EventDescriptionRewriter

	companion object {
		@Provides
		@Singleton
		fun provideGemmaClient(
			@ApplicationContext context: Context,
			resolver: GemmaModelResolver,
		): GemmaClient = LlmChatGemmaClient(
			context = context,
			modelProvider = { resolver.currentGemmaModel() },
		)
	}
}
