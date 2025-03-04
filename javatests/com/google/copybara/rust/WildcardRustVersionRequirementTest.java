/*
 * Copyright (C) 2023 Google LLC
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
public class WildcardRustVersionRequirementTest {
  @Test
  public void testGetCorrectRustVersionRequirementObject() throws Exception {
    assertThat(getVersionRequirement("1.2.*", false)).isInstanceOf(WildcardRustVersionRequirement.class);
  }

  @Test
  public void testMultipleVersionRequirements() throws Exception {
    assertThat(MultipleRustVersionRequirement.create("1.2.*").fulfills("1.2.5")).isTrue();
    assertThat(MultipleRustVersionRequirement.create("1.2.*").fulfills("1.3.0")).isFalse();
    assertThat(MultipleRustVersionRequirement.create("1.2.*").fulfills("1.0.1")).isFalse();
    assertThat(MultipleRustVersionRequirement.create("1.2.*").fulfills("0.2.5")).isFalse();
    assertThat(MultipleRustVersionRequirement.create("1.2.*").fulfills("2.0.0")).isFalse();
  }

  @Test
  public void testInvalidMultipleVersionRequirement() {
    ValidationException e =
        assertThrows(
            ValidationException.class, () -> WildcardRustVersionRequirement.create("-6.2.3"));
    assertThat(e)
        .hasMessageThat()
        .contains("The string -6.2.3 is not a valid wildcard version requirement.");
  }
}
