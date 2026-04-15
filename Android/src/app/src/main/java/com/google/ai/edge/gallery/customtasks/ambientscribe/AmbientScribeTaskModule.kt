/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe

import com.google.ai.edge.gallery.customtasks.common.CustomTask
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module that registers [AmbientScribeTask] into the application-wide `Set<CustomTask>`.
 *
 * Uses `@Binds` (rather than `@Provides`) because [AmbientScribeTask] is an `@Inject`-constructed
 * `@Singleton` — Hilt can construct it directly, and we only need to tell it which interface to
 * multi-bind under.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AmbientScribeTaskModule {
	@Binds
	@IntoSet
	abstract fun bindAmbientScribeTask(impl: AmbientScribeTask): CustomTask
}
