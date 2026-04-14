/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
	entities = [TranscriptSegment::class, AudioEvent::class, DailyMetadata::class],
	version = 1,
	exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AmbientScribeDatabase : RoomDatabase() {
	abstract fun transcriptSegmentDao(): TranscriptSegmentDao
	abstract fun audioEventDao(): AudioEventDao
	abstract fun dailyMetadataDao(): DailyMetadataDao
}
