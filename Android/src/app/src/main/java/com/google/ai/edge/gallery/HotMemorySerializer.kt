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

package com.google.ai.edge.gallery

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.ai.edge.gallery.proto.HotMemory
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object HotMemorySerializer : Serializer<HotMemory> {
	override val defaultValue: HotMemory = HotMemory.getDefaultInstance()

	override suspend fun readFrom(input: InputStream): HotMemory {
		try {
			return HotMemory.parseFrom(input)
		} catch (exception: InvalidProtocolBufferException) {
			throw CorruptionException("Cannot read proto.", exception)
		}
	}

	override suspend fun writeTo(t: HotMemory, output: OutputStream) = t.writeTo(output)
}
