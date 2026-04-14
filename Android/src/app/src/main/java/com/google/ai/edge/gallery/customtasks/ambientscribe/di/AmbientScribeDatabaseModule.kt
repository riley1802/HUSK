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
import androidx.room.Room
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AmbientScribeDatabase
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEventDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadataDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegmentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AmbientScribeDatabaseModule {

	@Provides
	@Singleton
	fun provideAmbientScribeDatabase(
		@ApplicationContext context: Context,
	): AmbientScribeDatabase {
		return Room.databaseBuilder(
			context,
			AmbientScribeDatabase::class.java,
			"ambient_scribe.db",
		).build()
	}

	@Provides
	fun provideTranscriptSegmentDao(db: AmbientScribeDatabase): TranscriptSegmentDao =
		db.transcriptSegmentDao()

	@Provides
	fun provideAudioEventDao(db: AmbientScribeDatabase): AudioEventDao =
		db.audioEventDao()

	@Provides
	fun provideDailyMetadataDao(db: AmbientScribeDatabase): DailyMetadataDao =
		db.dailyMetadataDao()
}
