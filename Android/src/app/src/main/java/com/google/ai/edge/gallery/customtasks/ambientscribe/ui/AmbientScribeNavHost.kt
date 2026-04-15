/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.ai.edge.gallery.customtasks.ambientscribe.settings.AmbientScribeSettingsScreen
import java.time.LocalDate

/**
 * Routes for the Ambient Scribe internal navigation graph.
 *
 * `{date}` is an ISO-8601 `LocalDate` string (e.g. `2026-04-14`). [DayDetailViewModel] reads it
 * from `SavedStateHandle` under the key [DayDetailViewModel.ARG_DATE] — which must match the
 * `navArgument` name below.
 */
object AmbientScribeRoutes {
	const val CALENDAR: String = "ambient_scribe/calendar"
	const val DAY_DETAIL: String = "ambient_scribe/day/{date}"
	const val SETTINGS: String = "ambient_scribe/settings"

	/** Builds a concrete [DAY_DETAIL] route path for [date]. */
	fun dayDetail(date: LocalDate): String = "ambient_scribe/day/$date"
}

/**
 * Internal navigation graph hosted inside the Ambient Scribe custom task's `MainScreen`.
 *
 * Each destination owns its own `TopAppBar`, so we hide the outer HUSK custom-task app bar via
 * [setTopBarVisible] for the lifetime of this composable.
 *
 * Back-button handling:
 *  - We register a `customNavigateUpCallback` with the outer framework so both the outer back
 *    button AND the system back gesture (which the outer [CustomTaskScreen]'s `BackHandler`
 *    forwards to the callback) first try to pop our internal back stack.
 *  - When we're already at the root (calendar) and there is nothing to pop, we clear the
 *    callback and re-invoke `onNavigateUp` so the next back press exits the custom task.
 *  - The calendar's own back button calls [exitTask], which has the same effect.
 */
@Composable
fun AmbientScribeNavHost(
	setCustomNavigateUpCallback: ((() -> Unit)?) -> Unit = {},
	setTopBarVisible: (Boolean) -> Unit = {},
) {
	val navController = rememberNavController()

	DisposableEffect(Unit) {
		setTopBarVisible(false)
		onDispose { setTopBarVisible(true) }
	}

	// Install an up-navigation callback that pops the internal stack, and exits the task when the
	// stack is empty.
	DisposableEffect(navController) {
		val callback: () -> Unit = {
			if (!navController.popBackStack()) {
				// Clear the callback so the next up-press (dispatched by the outer framework after
				// it re-reads the callback reference) falls through to the default
				// "navigate out of custom task" behaviour.
				setCustomNavigateUpCallback(null)
			}
		}
		setCustomNavigateUpCallback(callback)
		onDispose { setCustomNavigateUpCallback(null) }
	}

	val exitTask: () -> Unit = { setCustomNavigateUpCallback(null) }

	NavHost(
		navController = navController,
		startDestination = AmbientScribeRoutes.CALENDAR,
	) {
		composable(AmbientScribeRoutes.CALENDAR) {
			CalendarScreen(
				onDayClick = { date ->
					navController.navigate(AmbientScribeRoutes.dayDetail(date))
				},
				onSettingsClick = { navController.navigate(AmbientScribeRoutes.SETTINGS) },
				onBackClick = exitTask,
			)
		}
		composable(
			route = AmbientScribeRoutes.DAY_DETAIL,
			arguments = listOf(
				navArgument(DayDetailViewModel.ARG_DATE) { type = NavType.StringType },
			),
		) {
			DayDetailScreen(
				onBackClick = { navController.popBackStack() },
			)
		}
		composable(AmbientScribeRoutes.SETTINGS) {
			AmbientScribeSettingsScreen(
				onNavigateBack = { navController.popBackStack() },
			)
		}
	}
}
