package com.google.ai.edge.gallery.data.rag

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Type converters for Room to handle IngestionStatus enum.
 */
class RagTypeConverters {
	@TypeConverter
	fun fromIngestionStatus(status: IngestionStatus): String = status.name

	@TypeConverter
	fun toIngestionStatus(value: String): IngestionStatus = IngestionStatus.valueOf(value)
}

/**
 * Room database for RAG document storage.
 * Separate from MemoryDatabase — RAG manages document knowledge,
 * memory manages episodic/behavioral knowledge.
 */
@Database(
	entities = [Document::class, DocumentChunk::class],
	version = 1,
	exportSchema = false,
)
@TypeConverters(RagTypeConverters::class)
abstract class RagDatabase : RoomDatabase() {
	abstract fun ragDao(): RagDao
}
