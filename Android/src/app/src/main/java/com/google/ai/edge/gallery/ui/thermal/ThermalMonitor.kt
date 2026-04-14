/*
 * Copyright 2026 Riley Thomason
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Monitors device thermal state using Android public APIs.
 * Uses PowerManager for thermal status/headroom and BatteryManager for battery temp.
 * Estimates skin/CPU temps from headroom since direct sensor access requires system privileges.
 */
object ThermalMonitor {

	private const val TAG = "ThermalMonitor"

	data class ThermalReading(
		val name: String,
		val tempCelsius: Float,
		val type: ThermalType,
	)

	enum class ThermalType(val label: String) {
		CPU("CPU (est.)"),
		BATTERY("Battery"),
		SKIN("Skin (est.)"),
		OTHER("Other"),
	}

	data class ThermalSnapshot(
		val readings: List<ThermalReading>,
		val thermalStatus: Int,
		val headroom: Float,
		val timestampMs: Long = System.currentTimeMillis(),
	) {
		val peakTemp: Float get() = readings.maxOfOrNull { it.tempCelsius } ?: 0f
		val skinTemp: Float? get() = readings.find { it.type == ThermalType.SKIN }?.tempCelsius
		val cpuTemp: Float? get() = readings.find { it.type == ThermalType.CPU }?.tempCelsius
		val batteryTemp: Float? get() = readings.find { it.type == ThermalType.BATTERY }?.tempCelsius

		val statusLabel: String get() = when (thermalStatus) {
			0 -> "None"
			1 -> "Light"
			2 -> "Moderate"
			3 -> "Severe"
			4 -> "Critical"
			5 -> "Emergency"
			6 -> "Shutdown"
			else -> "Unknown"
		}

		val severityLevel: Int get() = when {
			thermalStatus >= 3 -> 3
			thermalStatus >= 2 -> 2
			thermalStatus >= 1 -> 1
			headroom > 0.9f -> 2
			headroom > 0.7f -> 1
			else -> 0
		}

		/** Estimated skin temp from headroom (SKIN throttles at 38-45C range). */
		val estimatedSkinFromHeadroom: Float get() {
			if (headroom.isNaN()) return 0f
			// headroom 0 = at threshold (38C skin), headroom 1.0 = at throttle point
			// Linear interpolation: 25C at headroom 0, 38C at headroom ~0.8, 45C at 1.0+
			return (25f + headroom * 25f).coerceIn(20f, 55f)
		}
	}

	object Recommendations {
		const val IDEAL_MAX_SKIN = 37f
		const val WARNING_SKIN = 40f
		const val THROTTLE_SKIN = 42f
		const val CRITICAL_SKIN = 45f

		const val IDEAL_MAX_BATTERY = 35f
		const val WARNING_BATTERY = 40f
		const val CRITICAL_BATTERY = 45f

		fun getSkinAdvice(headroom: Float): String = when {
			headroom.isNaN() -> "Unable to read thermal headroom"
			headroom < 0.5f -> "Cool — ideal for sustained inference"
			headroom < 0.7f -> "Warm — performance stable"
			headroom < 0.9f -> "Hot — performance may reduce soon"
			headroom < 1.0f -> "Very hot — throttling imminent"
			else -> "Throttling active — reduce workload"
		}

		fun getBatteryAdvice(temp: Float): String = when {
			temp < IDEAL_MAX_BATTERY -> "Battery temperature normal"
			temp < WARNING_BATTERY -> "Battery warm — normal under load"
			temp < CRITICAL_BATTERY -> "Battery hot — consider pausing"
			else -> "Battery critical — stop intensive tasks"
		}
	}

	fun observe(context: Context, intervalMs: Long = 3000): Flow<ThermalSnapshot> = flow {
		val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

		while (true) {
			val snapshot = readThermals(context, powerManager)
			emit(snapshot)
			delay(intervalMs)
		}
	}.flowOn(Dispatchers.IO)

	fun read(context: Context): ThermalSnapshot {
		val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
		return readThermals(context, powerManager)
	}

	private fun readThermals(context: Context, powerManager: PowerManager): ThermalSnapshot {
		val readings = mutableListOf<ThermalReading>()

		// Battery temperature from BatteryManager (always available, no permissions needed).
		try {
			val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
			val batteryTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.let {
				it / 10f // BatteryManager reports in tenths of a degree
			} ?: 0f
			if (batteryTemp > 0f) {
				readings.add(ThermalReading("Battery", batteryTemp, ThermalType.BATTERY))
			}
		} catch (e: Exception) {
			Log.w(TAG, "Failed to read battery temp", e)
		}

		// Thermal status and headroom from PowerManager.
		val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			powerManager.currentThermalStatus
		} else {
			0
		}

		val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			powerManager.getThermalHeadroom(10)
		} else {
			Float.NaN
		}

		// Estimate skin and CPU temperatures from headroom.
		if (!headroom.isNaN() && headroom > 0f) {
			val estimatedSkin = (25f + headroom * 25f).coerceIn(20f, 55f)
			readings.add(ThermalReading("Skin (est.)", estimatedSkin, ThermalType.SKIN))

			// CPU typically runs a few degrees above skin.
			val estimatedCpu = (estimatedSkin + 3f).coerceIn(25f, 60f)
			readings.add(ThermalReading("CPU (est.)", estimatedCpu, ThermalType.CPU))
		}

		return ThermalSnapshot(
			readings = readings,
			thermalStatus = thermalStatus,
			headroom = headroom,
		)
	}
}
