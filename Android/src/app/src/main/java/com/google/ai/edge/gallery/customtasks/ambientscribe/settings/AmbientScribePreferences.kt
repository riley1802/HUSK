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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences-DataStore-backed storage for Ambient Scribe user toggles.
 *
 * Kept deliberately small (two booleans) so we don't need proto here; this lives alongside
 * (not instead of) the proto DataStore in [com.google.ai.edge.gallery.data.DataStoreRepository].
 */
@Singleton
class AmbientScribePreferences @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	val serviceEnabled: Flow<Boolean> = context.ambientScribeDataStore.data
		.map { it[KEY_SERVICE_ENABLED] ?: false }

	val hasAcceptedConsent: Flow<Boolean> = context.ambientScribeDataStore.data
		.map { it[KEY_CONSENT_ACCEPTED] ?: false }

	suspend fun setServiceEnabled(enabled: Boolean) {
		context.ambientScribeDataStore.edit { it[KEY_SERVICE_ENABLED] = enabled }
	}

	suspend fun setHasAcceptedConsent(accepted: Boolean) {
		context.ambientScribeDataStore.edit { it[KEY_CONSENT_ACCEPTED] = accepted }
	}

	companion object {
		private val KEY_SERVICE_ENABLED = booleanPreferencesKey("ambient_scribe_service_enabled")
		private val KEY_CONSENT_ACCEPTED = booleanPreferencesKey("ambient_scribe_consent_accepted")
	}
}

private val Context.ambientScribeDataStore: DataStore<Preferences> by preferencesDataStore(
	name = "ambient_scribe_prefs",
)
