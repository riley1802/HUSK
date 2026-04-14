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
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Monitors device thermal state using Android APIs and shell commands.
 * Provides live temperature readings for CPU, battery, skin, and other sensors.
 */
object ThermalMonitor {

	private const val TAG = "ThermalMonitor"

	data class ThermalReading(
		val name: String,
		val tempCelsius: Float,
		val type: ThermalType,
	)

	enum class ThermalType(val label: String) {
		CPU("CPU"),
		BATTERY("Battery"),
		SKIN("Skin"),
		USB("USB"),
		PA("Power Amp"),
		MODEM("Modem"),
		OTHER("Other"),
	}

	data class ThermalSnapshot(
		val readings: List<ThermalReading>,
		val thermalStatus: Int,
		val headroom: Float,
		val timestampMs: Long = System.currentTimeMillis(),
	) {
		/** Highest temperature across all sensors. */
		val peakTemp: Float get() = readings.maxOfOrNull { it.tempCelsius } ?: 0f

		/** Skin temperature (most relevant for throttling). */
		val skinTemp: Float? get() = readings.find { it.type == ThermalType.SKIN }?.tempCelsius

		/** CPU temperature. */
		val cpuTemp: Float? get() = readings.find { it.type == ThermalType.CPU }?.tempCelsius

		/** Battery temperature. */
		val batteryTemp: Float? get() = readings.find { it.type == ThermalType.BATTERY }?.tempCelsius

		/** Thermal status label. */
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

		/** Color hint: 0=green, 1=yellow, 2=orange, 3=red */
		val severityLevel: Int get() = when {
			thermalStatus >= 3 -> 3
			thermalStatus >= 2 -> 2
			thermalStatus >= 1 -> 1
			(skinTemp ?: 0f) >= 40f -> 2
			(skinTemp ?: 0f) >= 37f -> 1
			else -> 0
		}
	}

	/**
	 * Recommended temperature ranges for on-device AI inference.
	 */
	object Recommendations {
		const val IDEAL_MAX_SKIN = 37f
		const val WARNING_SKIN = 40f
		const val THROTTLE_SKIN = 42f
		const val CRITICAL_SKIN = 45f

		const val IDEAL_MAX_BATTERY = 35f
		const val WARNING_BATTERY = 40f
		const val CRITICAL_BATTERY = 45f

		fun getSkinAdvice(temp: Float): String = when {
			temp < IDEAL_MAX_SKIN -> "Ideal for sustained inference"
			temp < WARNING_SKIN -> "Warm — performance may reduce soon"
			temp < THROTTLE_SKIN -> "Hot — CPU throttling likely active"
			temp < CRITICAL_SKIN -> "Very hot — heavy throttling, reduce workload"
			else -> "Critical — device may shut down tasks"
		}

		fun getBatteryAdvice(temp: Float): String = when {
			temp < IDEAL_MAX_BATTERY -> "Battery temperature normal"
			temp < WARNING_BATTERY -> "Battery warm — normal under load"
			temp < CRITICAL_BATTERY -> "Battery hot — consider pausing"
			else -> "Battery critical — stop intensive tasks"
		}
	}

	/**
	 * Emit thermal snapshots at the given interval.
	 */
	fun observe(context: Context, intervalMs: Long = 3000): Flow<ThermalSnapshot> = flow {
		val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

		while (true) {
			val snapshot = readThermals(powerManager)
			emit(snapshot)
			delay(intervalMs)
		}
	}.flowOn(Dispatchers.IO)

	/**
	 * Single-shot thermal reading.
	 */
	fun read(context: Context): ThermalSnapshot {
		val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
		return readThermals(powerManager)
	}

	private fun readThermals(powerManager: PowerManager): ThermalSnapshot {
		val readings = mutableListOf<ThermalReading>()

		// Read from dumpsys thermalservice via shell.
		try {
			val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys thermalservice 2>/dev/null"))
			val reader = BufferedReader(InputStreamReader(process.inputStream))
			var inCurrentSection = false

			reader.forEachLine { line ->
				if (line.contains("Current temperatures from HAL:")) {
					inCurrentSection = true
				} else if (inCurrentSection) {
					if (line.contains("Temperature{")) {
						parseThermalLine(line)?.let { readings.add(it) }
					} else if (line.isNotBlank() && !line.startsWith("\t") && !line.startsWith(" ")) {
						inCurrentSection = false
					}
				}
			}
			process.waitFor()
		} catch (e: Exception) {
			Log.w(TAG, "Failed to read thermalservice", e)
		}

		val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			powerManager.currentThermalStatus
		} else {
			0
		}

		val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			powerManager.getThermalHeadroom(10) // 10 second forecast
		} else {
			Float.NaN
		}

		return ThermalSnapshot(
			readings = readings,
			thermalStatus = thermalStatus,
			headroom = headroom,
		)
	}

	private fun parseThermalLine(line: String): ThermalReading? {
		// Temperature{mValue=30.7, mType=0, mName=AP, mStatus=0}
		try {
			val valueMatch = Regex("mValue=([\\d.]+)").find(line) ?: return null
			val typeMatch = Regex("mType=(\\d+)").find(line) ?: return null
			val nameMatch = Regex("mName=(\\w+)").find(line) ?: return null

			val temp = valueMatch.groupValues[1].toFloat()
			val typeInt = typeMatch.groupValues[1].toInt()
			val name = nameMatch.groupValues[1]

			val type = when (name.uppercase()) {
				"AP" -> ThermalType.CPU
				"BAT", "SUBBAT" -> ThermalType.BATTERY
				"SKIN" -> ThermalType.SKIN
				"USB" -> ThermalType.USB
				"PA" -> ThermalType.PA
				"CP" -> ThermalType.MODEM
				else -> ThermalType.OTHER
			}

			return ThermalReading(name = name, tempCelsius = temp, type = type)
		} catch (e: Exception) {
			return null
		}
	}
}
