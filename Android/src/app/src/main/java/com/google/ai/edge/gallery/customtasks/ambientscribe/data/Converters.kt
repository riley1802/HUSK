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

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {

	@TypeConverter
	fun fromLocalDate(value: LocalDate?): String? = value?.toString()

	@TypeConverter
	fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

	@TypeConverter
	fun fromRewriteState(value: RewriteState?): String? = value?.name

	@TypeConverter
	fun toRewriteState(value: String?): RewriteState? = value?.let { RewriteState.valueOf(it) }
}
