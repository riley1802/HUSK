/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Returns the current grant state of [permission] and recomputes whenever the hosting lifecycle
 * comes back to [Lifecycle.Event.ON_RESUME]. This is how we notice that the user flipped a
 * permission from the system Settings screen while the app was in the background.
 */
@Composable
fun rememberHasPermission(permission: String): Boolean {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	var granted by remember(permission) { mutableStateOf(checkPermission(context, permission)) }
	DisposableEffect(lifecycleOwner, permission) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				granted = checkPermission(context, permission)
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}
	return granted
}

/**
 * Returns whether this app is currently excluded from battery optimizations (i.e. allowed to
 * keep the foreground service alive on aggressive OEM skins). Recomputes on resume so changes
 * made in system Settings are reflected immediately.
 */
@Composable
fun rememberIsIgnoringBatteryOptimizations(): Boolean {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	var ignoring by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				ignoring = isIgnoringBatteryOptimizations(context)
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}
	return ignoring
}

private fun checkPermission(context: Context, permission: String): Boolean =
	ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
	val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
	return pm.isIgnoringBatteryOptimizations(context.packageName)
}
