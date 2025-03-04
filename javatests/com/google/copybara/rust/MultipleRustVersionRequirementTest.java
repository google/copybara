/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.rust;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.rust.RustVersionRequirement.getVersionRequirement;
import static org.junit.Assert.assertThrows;

import com.google.copybara.exception.ValidationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MultipleRustVersionRequirementTest {
  @Test
  public void testGetCorrectRustVersionRequirementObject() throws Exception {
    assertThat(getVersionRequirement("> 0.4, <= 0.6", false))
        .isInstanceOf(MultipleRustVersionRequirement.class);
  }

  @Test
  public void testMultipleVersionRequirements() throws Exception {
    assertThat(MultipleRustVersionRequirement.create("> 0.4, <= 0.6").fulfills("0.5")).isTrue();
    assertThat(MultipleRustVersionRequirement.create("> 0.4, <= 0.6").fulfills("0.4")).isFalse();
    assertThat(MultipleRustVersionRequirement.create("> 0.4, <= 0.6").fulfills("0.6")).isTrue();
    assertThat(MultipleRustVersionRequirement.create("> 0.4, <= 0.6").fulfills("1.2")).isFalse();
    assertThat(MultipleRustVersionRequirement.create("> 0.4, <= 0.6").fulfills("0")).isFalse();

    assertThat(MultipleRustVersionRequirement.create("> 1.0.0, < 2").fulfills("1.5.0")).isTrue();
    assertThat(MultipleRustVersionRequirement.create("> 1.0.0, < 2").fulfills("1.0.0")).isFalse();
    assertThat(MultipleRustVersionRequirement.create("> 2.0.0, < 2").fulfills("2.0.0")).isFalse();
  }

  @Test
  public void testInvalidMultipleVersionRequirement() {
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () -> MultipleRustVersionRequirement.create("> 1.0.1, <= foo"));
    assertThat(e)
        .hasMessageThat()
        .contains("The requirement > 1.0.1, <= foo is not a valid multiple version requirement.");
  }
}
