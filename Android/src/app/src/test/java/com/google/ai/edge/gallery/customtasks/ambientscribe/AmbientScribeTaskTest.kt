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

import com.google.ai.edge.gallery.data.Model
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Stability guardrails for [AmbientScribeTask]'s public identity and no-op model lifecycle hooks.
 *
 * The task ID and label are persisted across app versions (used as preference keys, notification
 * channel categories, and hub tile keys downstream) so the asserts below double as change
 * detectors.
 */
class AmbientScribeTaskTest {

	private lateinit var task: AmbientScribeTask

	@Before
	fun setUp() {
		task = AmbientScribeTask()
	}

	@Test
	fun `task id is ambient_scribe`() {
		assertEquals("ambient_scribe", task.task.id)
		assertEquals(AmbientScribeTask.TASK_ID, task.task.id)
	}

	@Test
	fun `task label is Ambient Scribe`() {
		assertEquals("Ambient Scribe", task.task.label)
		assertEquals(AmbientScribeTask.TASK_LABEL, task.task.label)
	}

	@Test
	fun `task models is empty list`() {
		assertTrue(
			"Ambient Scribe does not use the model-manager flow; models must be empty.",
			task.task.models.isEmpty(),
		)
	}

	@Test
	fun `task category id is ambient_scribe`() {
		assertEquals("ambient_scribe", task.task.category.id)
		assertEquals("Ambient", task.task.category.label)
	}

	@Test
	fun `task icon is set`() {
		assertNotNull("Icon must be set so the hub tile renders.", task.task.icon)
	}

	@Test
	fun `initializeModelFn invokes onDone with empty string`() {
		var captured: String? = null
		task.initializeModelFn(
			context = FakeContext,
			coroutineScope = CoroutineScope(Dispatchers.Unconfined),
			model = dummyModel(),
			onDone = { captured = it },
		)
		assertEquals("", captured)
	}

	@Test
	fun `cleanUpModelFn invokes onDone`() {
		var called = false
		task.cleanUpModelFn(
			context = FakeContext,
			coroutineScope = CoroutineScope(Dispatchers.Unconfined),
			model = dummyModel(),
			onDone = { called = true },
		)
		assertTrue("cleanUpModelFn must invoke onDone synchronously (no-op).", called)
	}

	private fun dummyModel(): Model = Model(name = "unused")

	/**
	 * The no-op lifecycle hooks never touch [android.content.Context]; we only need a non-null
	 * placeholder that satisfies the signature. Using `null!!` would pollute the stack trace on
	 * failure, so we use a dedicated uninitialised proxy and rely on the contract (no-op) to never
	 * dereference it.
	 */
	private object FakeContext : android.content.ContextWrapper(null)
}
