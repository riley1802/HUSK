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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AmbientScribeDatabaseTest {

	private lateinit var db: AmbientScribeDatabase
	private lateinit var transcriptDao: TranscriptSegmentDao
	private lateinit var audioEventDao: AudioEventDao
	private lateinit var dailyMetadataDao: DailyMetadataDao

	@Before
	fun setUp() {
		val context = ApplicationProvider.getApplicationContext<android.content.Context>()
		db = Room.inMemoryDatabaseBuilder(context, AmbientScribeDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		transcriptDao = db.transcriptSegmentDao()
		audioEventDao = db.audioEventDao()
		dailyMetadataDao = db.dailyMetadataDao()
	}

	@After
	fun tearDown() {
		db.close()
	}

	@Test
	fun transcriptSegment_roundTrip() = runBlocking {
		val date = LocalDate.of(2026, 4, 14)
		val segment = TranscriptSegment(
			date = date,
			startTimestamp = 1_000L,
			endTimestamp = 5_000L,
			text = "hello world",
			confidence = 0.95f,
			durationMs = 4_000L,
			wordCount = 2,
		)
		val id = transcriptDao.insert(segment)
		assertTrue(id > 0)

		val fetched = transcriptDao.getByDate(date)
		assertEquals(1, fetched.size)
		val row = fetched.first()
		assertEquals(date, row.date)
		assertEquals("hello world", row.text)
		assertEquals(0.95f, row.confidence, 0.0001f)
		assertEquals(false, row.isBookmarked)
		assertNull(row.audioFilePath)
	}

	@Test
	fun audioEvent_enumRoundTrip() = runBlocking {
		val date = LocalDate.of(2026, 4, 14)
		val event = AudioEvent(
			date = date,
			timestamp = 2_000L,
			durationMs = 500L,
			label = "Dog bark",
			confidence = 0.8f,
		)
		val id = audioEventDao.insert(event)
		val fetched = audioEventDao.getByDate(date)
		assertEquals(1, fetched.size)
		assertEquals(RewriteState.PENDING, fetched.first().rewriteState)

		val pending = audioEventDao.getPendingEvents(date)
		assertEquals(1, pending.size)
		assertEquals(id, pending.first().id)

		val allPending = audioEventDao.getAllPendingEvents()
		assertEquals(1, allPending.size)
	}

	@Test
	fun getDatesWithData_returnsDistinctSortedDesc() = runBlocking {
		val d1 = LocalDate.of(2026, 4, 10)
		val d2 = LocalDate.of(2026, 4, 14)
		val d3 = LocalDate.of(2026, 4, 12)
		listOf(d1, d2, d2, d3).forEach {
			transcriptDao.insert(
				TranscriptSegment(
					date = it,
					startTimestamp = 0L,
					endTimestamp = 1L,
					text = "x",
					confidence = 1f,
					durationMs = 1L,
					wordCount = 1,
				)
			)
		}
		val dates = transcriptDao.getDatesWithData()
		assertEquals(listOf(d2, d3, d1), dates)
	}

	@Test
	fun updateRewrite_transitionsPendingToDone() = runBlocking {
		val date = LocalDate.of(2026, 4, 14)
		val id = audioEventDao.insert(
			AudioEvent(
				date = date,
				timestamp = 1L,
				durationMs = 1L,
				label = "Dog bark",
				confidence = 0.9f,
			)
		)
		audioEventDao.updateRewrite(id, "A dog barked nearby.", RewriteState.DONE)
		val fetched = audioEventDao.getByDate(date).first()
		assertEquals(RewriteState.DONE, fetched.rewriteState)
		assertEquals("A dog barked nearby.", fetched.naturalDescription)
		assertTrue(audioEventDao.getPendingEvents(date).isEmpty())
	}

	@Test
	fun dailyMetadata_upsertReplacesOnConflict() = runBlocking {
		val date = LocalDate.of(2026, 4, 14)
		dailyMetadataDao.upsert(
			DailyMetadata(
				date = date,
				totalDurationMs = 1000L,
				totalWordCount = 10,
				totalSegments = 1,
			)
		)
		dailyMetadataDao.upsert(
			DailyMetadata(
				date = date,
				totalDurationMs = 2000L,
				totalWordCount = 20,
				totalSegments = 2,
				summary = "updated",
			)
		)
		val row = dailyMetadataDao.getByDate(date)
		assertNotNull(row)
		assertEquals(2000L, row!!.totalDurationMs)
		assertEquals(20, row.totalWordCount)
		assertEquals(2, row.totalSegments)
		assertEquals("updated", row.summary)
	}

	@Test
	fun getMonth_filtersByYearMonth() = runBlocking {
		dailyMetadataDao.upsert(DailyMetadata(LocalDate.of(2026, 4, 1), 1L, 1, 1))
		dailyMetadataDao.upsert(DailyMetadata(LocalDate.of(2026, 4, 30), 1L, 1, 1))
		dailyMetadataDao.upsert(DailyMetadata(LocalDate.of(2026, 3, 31), 1L, 1, 1))
		dailyMetadataDao.upsert(DailyMetadata(LocalDate.of(2026, 5, 1), 1L, 1, 1))

		val april = dailyMetadataDao.getMonth("2026-04")
		assertEquals(2, april.size)
		assertTrue(april.all { it.date.monthValue == 4 })
	}
}
