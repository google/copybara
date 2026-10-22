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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DummyKotlinTest {

  @Test
  fun greet_returnsFormattedMessage() {
    val dummy = DummyKotlin()
    assertThat(dummy.greet("Copybara")).isEqualTo("Hello, Copybara!")
  }

  @Test
  fun createCommand_returnsCommandWithArgs() {
    val dummy = DummyKotlin()
    val cmd = dummy.createCommand("echo", "kotlin")
    assertThat(cmd.commandLineElements).asList().containsExactly("echo", "kotlin").inOrder()
  }

  @Test
  fun testDataPath_matchesExpectedPrefix() {
    val testPath =
      "javatests/com/google/copybara/kotlin/DummyKotlinTest.kt"
    assertThat(testPath).endsWith("com/google/copybara/kotlin/DummyKotlinTest.kt")
  }
}
