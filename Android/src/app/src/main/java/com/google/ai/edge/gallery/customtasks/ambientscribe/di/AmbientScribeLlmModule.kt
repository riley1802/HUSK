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

import com.google.ai.edge.gallery.customtasks.ambientscribe.llm.EventDescriptionRewriter
import com.google.ai.edge.gallery.customtasks.ambientscribe.llm.GemmaClient
import com.google.ai.edge.gallery.customtasks.ambientscribe.llm.GemmaEventDescriptionRewriter
import com.google.ai.edge.gallery.customtasks.ambientscribe.llm.LlmChatGemmaClient
import com.google.ai.edge.gallery.data.Model
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the Ambient Scribe event-rewrite pipeline.
 *
 * Binds [EventDescriptionRewriter] to its Gemma-backed implementation and provides a
 * default [GemmaClient] whose model provider currently returns null — this means the
 * client reports not-ready and the rewriter is a no-op until the model-picker integration
 * lands. Matching the Moonshine stub pattern avoids blocking Ambient Scribe on a model
 * download + selection flow that isn't plumbed yet.
 *
 * When model selection is wired, replace [provideGemmaClient]'s provider lambda with one
 * that returns the configured Gemma [Model] (e.g. via `DataStoreRepository.readImportedModels`
 * or a dedicated SelectedModelRepository).
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
		fun provideGemmaClient(): GemmaClient =
			// TODO(ambient-scribe): wire to the Gemma Model selected by the download/picker
			// flow. Returning null keeps the rewriter in a safe not-ready state.
			LlmChatGemmaClient(modelProvider = { null as Model? })
	}
}
