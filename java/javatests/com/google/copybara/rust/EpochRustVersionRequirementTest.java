/*
 * Copyright (C) 2025 Google LLC.
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
public class EpochRustVersionRequirementTest {
  @Test
  public void testGetCorrectRustVersionRequirementObject() throws Exception {
    assertThat(getVersionRequirement("2", true)).isInstanceOf(EpochRustVersionRequirement.class);
  }

  @Test
  public void testRequirementsForMajorEpoch() throws Exception {
    assertThat(getVersionRequirement("2", true).fulfills("2.0.0")).isTrue();
    assertThat(getVersionRequirement("2", true).fulfills("2.0.1")).isTrue();
    assertThat(getVersionRequirement("2", true).fulfills("2.4.1")).isTrue();
    assertThat(getVersionRequirement("2", true).fulfills("2.0.0-beta2")).isTrue();
    assertThat(getVersionRequirement("2", true).fulfills("2.4.0-beta2")).isTrue();


    assertThat(getVersionRequirement("2", true).fulfills("3.0.0")).isFalse();
    assertThat(getVersionRequirement("2", true).fulfills("1.0.0")).isFalse();
    assertThat(getVersionRequirement("2", true).fulfills("3.0.0-pre")).isFalse();
  }

  @Test
  public void testRequirementsForMinorEpoch() throws Exception {
    assertThat(getVersionRequirement("0.2", true).fulfills("0.2.0")).isTrue();
    assertThat(getVersionRequirement("0.2", true).fulfills("0.2.1")).isTrue();
    assertThat(getVersionRequirement("0.2", true).fulfills("0.2.0-pre")).isTrue();


    assertThat(getVersionRequirement("0.2", true).fulfills("0.3.0")).isFalse();
    assertThat(getVersionRequirement("0.2", true).fulfills("0.1.5")).isFalse();
  }

  @Test
  public void testRequirementsForPatchEpoch() throws Exception {
    assertThat(getVersionRequirement("0.0.2", true).fulfills("0.0.2")).isTrue();
    assertThat(getVersionRequirement("0.0.2", true).fulfills("0.0.2-pre")).isTrue();


    assertThat(getVersionRequirement("0.0.2", true).fulfills("0.0.3")).isFalse();
    assertThat(getVersionRequirement("0.0.2", true).fulfills("0.0.1")).isFalse();
  }


  @Test
  public void testBadVersionRequirementString() {
    ValidationException e =
        assertThrows(ValidationException.class, () -> DefaultRustVersionRequirement.create("foo"));
    assertThat(e.getMessage())
        .contains("The string foo is not a valid default or caret version requirement.");
  }
}
