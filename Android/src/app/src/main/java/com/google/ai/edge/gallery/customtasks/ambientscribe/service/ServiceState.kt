/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.service

/**
 * Lifecycle state of the Ambient Scribe foreground service. Exposed via
 * [AmbientScribeServiceController.state] for UI observers.
 */
enum class ServiceState { Idle, Initializing, Running, Paused, Stopping }
