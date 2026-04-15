/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.llm

import android.content.Context
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import java.io.File
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Unit tests for [GemmaModelResolver]. These verify the resolver's core contract:
 *   - returns null when no Gemma model file exists on disk
 *   - returns the downloaded Gemma model when one is present
 *   - prefers the smaller-by-sizeInBytes Gemma when multiple are downloaded
 */
class GemmaModelResolverTest {

	@get:Rule
	val tempFolder = TemporaryFolder()

	private lateinit var context: Context
	private lateinit var externalFilesDir: File

	@Before
	fun setUp() {
		externalFilesDir = tempFolder.newFolder("externalFiles")
		context = mock(Context::class.java)
		`when`(context.getExternalFilesDir(null)).thenReturn(externalFilesDir)
	}

	@After
	fun tearDown() {
		// TemporaryFolder cleans itself; nothing else to do.
	}

	@Test
	fun `returns null when no gemma model file exists on disk`() {
		val undownloadedGemma = gemmaModel(name = "gemma-3n-e2b.task", sizeInBytes = 2_000_000_000L)
		val resolver = resolverFor(tasksWithModels(BuiltInTaskId.LLM_CHAT, undownloadedGemma))

		assertNull(resolver.currentGemmaModel())
	}

	@Test
	fun `returns null when downloaded models are not gemma family`() {
		val downloadedNonGemma = createAndWriteModel(
			name = "llama-3-1b.task",
			sizeInBytes = 1_000_000_000L,
		)
		val resolver = resolverFor(tasksWithModels(BuiltInTaskId.LLM_CHAT, downloadedNonGemma))

		assertNull(resolver.currentGemmaModel())
	}

	@Test
	fun `returns the downloaded gemma model when present`() {
		val downloadedGemma = createAndWriteModel(
			name = "gemma-3n-e4b.task",
			sizeInBytes = 4_000_000_000L,
		)
		val resolver = resolverFor(tasksWithModels(BuiltInTaskId.LLM_CHAT, downloadedGemma))

		val resolved = resolver.currentGemmaModel()
		assertEquals(downloadedGemma.name, resolved?.name)
	}

	@Test
	fun `prefers the smaller gemma when multiple are downloaded`() {
		val smaller = createAndWriteModel(name = "gemma-3n-e2b.task", sizeInBytes = 2_000_000_000L)
		val larger = createAndWriteModel(name = "gemma-3n-e4b.task", sizeInBytes = 4_000_000_000L)
		val resolver = resolverFor(tasksWithModels(BuiltInTaskId.LLM_CHAT, larger, smaller))

		val resolved = resolver.currentGemmaModel()
		assertEquals(smaller.name, resolved?.name)
	}

	@Test
	fun `ignores case when matching gemma substring`() {
		val downloaded = createAndWriteModel(name = "Gemma-3N-E2B.task", sizeInBytes = 2_000_000_000L)
		val resolver = resolverFor(tasksWithModels(BuiltInTaskId.LLM_CHAT, downloaded))

		assertEquals(downloaded.name, resolver.currentGemmaModel()?.name)
	}

	private fun gemmaModel(name: String, sizeInBytes: Long): Model {
		val model = Model(
			name = name,
			url = "",
			downloadFileName = name,
			sizeInBytes = sizeInBytes,
		)
		model.preProcess()
		return model
	}

	/**
	 * Creates a [Model] whose download file physically exists under the mocked external
	 * files dir, so [Model.getPath] returns a real, existing path.
	 */
	private fun createAndWriteModel(name: String, sizeInBytes: Long): Model {
		val model = gemmaModel(name = name, sizeInBytes = sizeInBytes)
		val path = File(model.getPath(context))
		path.parentFile?.mkdirs()
		path.writeBytes(ByteArray(0))
		return model
	}

	private fun tasksWithModels(taskId: String, vararg models: Model): List<CustomTask> {
		val task = Task(
			id = taskId,
			label = "Chat",
			category = Category.LLM,
			models = models.toMutableList(),
			description = "",
		)
		return listOf(TestCustomTask(task))
	}

	private fun resolverFor(tasks: List<CustomTask>): GemmaModelResolver {
		val provider = Provider<Set<CustomTask>> { tasks.toSet() }
		return GemmaModelResolver(context = context, customTasksProvider = provider)
	}

	/** Minimal [CustomTask] stub for tests — the resolver only touches [task]. */
	private class TestCustomTask(override val task: Task) : CustomTask {
		override fun initializeModelFn(
			context: Context,
			coroutineScope: CoroutineScope,
			model: Model,
			onDone: (String) -> Unit,
		) = Unit

		override fun cleanUpModelFn(
			context: Context,
			coroutineScope: CoroutineScope,
			model: Model,
			onDone: () -> Unit,
		) = Unit

		@Composable
		override fun MainScreen(data: Any) = Unit
	}
}
