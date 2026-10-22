/*
 * Copyright (C) 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.kotlin

import com.google.common.flogger.FluentLogger
import com.google.copybara.shell.Command

/** Dummy Kotlin class for testing Kotlin support and Copybara transformations. */
class DummyKotlin(val prefix: String = "Hello") {

  fun greet(name: String): String {
    logger.atInfo().log("Greeting %s with prefix %s", name, prefix)
    return "$prefix, $name!"
  }

  fun createCommand(vararg args: String): Command = Command(args)

  companion object {
    private val logger = FluentLogger.forEnclosingClass()
  }
}
