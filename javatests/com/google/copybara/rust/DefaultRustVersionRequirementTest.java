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
public class DefaultRustVersionRequirementTest {
  @Test
  public void testGetCorrectRustVersionRequirementObject() throws Exception {
    assertThat(getVersionRequirement("^1.2.3", false)).isInstanceOf(DefaultRustVersionRequirement.class);
  }

  @Test
  public void testRequirementsWithNonZeroMajorVersion() throws Exception {
    assertThat(getVersionRequirement("1.2.3", false).fulfills("1.2.3")).isTrue();
    assertThat(getVersionRequirement("1.2.3", false).fulfills("1.5.0")).isTrue();
    assertThat(getVersionRequirement("1.2.3", false).fulfills("2.0.0")).isFalse();
    assertThat(getVersionRequirement("1.2.3", false).fulfills("0.5.0")).isFalse();
    assertThat(getVersionRequirement("1.2", false).fulfills("1.2.0")).isTrue();
    assertThat(getVersionRequirement("1.2", false).fulfills("1.2.1")).isTrue();
    assertThat(getVersionRequirement("1.2", false).fulfills("2.0.0")).isFalse();
    assertThat(getVersionRequirement("1.2", false).fulfills("0.8.0")).isFalse();
    assertThat(getVersionRequirement("1", false).fulfills("1.0.0")).isTrue();
    assertThat(getVersionRequirement("1", false).fulfills("1.1.0")).isTrue();
    assertThat(getVersionRequirement("1", false).fulfills("2.0.0")).isFalse();
    assertThat(getVersionRequirement("1", false).fulfills("0.7.0")).isFalse();
  }

  @Test
  public void testRequirementsWithNonZeroMinorVersion() throws Exception {
    assertThat(getVersionRequirement("0.2.3", false).fulfills("0.2.3")).isTrue();
    assertThat(getVersionRequirement("0.2.3", false).fulfills("0.2.5")).isTrue();
    assertThat(getVersionRequirement("0.2.3", false).fulfills("0.3.0")).isFalse();
    assertThat(getVersionRequirement("0.2.3", false).fulfills("0.2.2")).isFalse();
    assertThat(getVersionRequirement("0.2", false).fulfills("0.2.0")).isTrue();
    assertThat(getVersionRequirement("0.2", false).fulfills("0.2.1")).isTrue();
    assertThat(getVersionRequirement("0.2", false).fulfills("0.3")).isFalse();
    assertThat(getVersionRequirement("0.2", false).fulfills("0.1.0")).isFalse();
  }

  @Test
  public void testRequirementsWithNonZeroPatchVersion() throws Exception {
    assertThat(getVersionRequirement("0.0.3", false).fulfills("0.0.3")).isTrue();
    assertThat(getVersionRequirement("0.0.3", false).fulfills("0.0.4")).isFalse();
    assertThat(getVersionRequirement("0.0.3", false).fulfills("0.0.2")).isFalse();
  }

  @Test
  public void testSpecialCaseRequirements() throws Exception {
    assertThat(getVersionRequirement("0", false).fulfills("0.5.0")).isTrue();
    assertThat(getVersionRequirement("0", false).fulfills("0.2.1")).isTrue();
    assertThat(getVersionRequirement("0", false).fulfills("1.0.0")).isFalse();
  }

  @Test
  public void testPreReleaseVersionRequirements() throws Exception {
    assertThat(getVersionRequirement("1.2.3-alpha.1", false).fulfills("1.2.3-alpha.1")).isTrue();
    assertThat(getVersionRequirement("1.2.3-alpha.1", false).fulfills("1.2.3")).isTrue();
    assertThat(getVersionRequirement("1.2.3-alpha.1", false).fulfills("1.5")).isTrue();
    assertThat(getVersionRequirement("1.2.3-alpha.2", false).fulfills("1.2.3-alpha.1")).isFalse();
    assertThat(getVersionRequirement("1.2.3-rc.1", false).fulfills("1.2.3-beta.1")).isFalse();
  }

  @Test
  public void testBadVersionRequirementString() {
    ValidationException e =
        assertThrows(ValidationException.class, () -> DefaultRustVersionRequirement.create("foo"));
    assertThat(e.getMessage())
        .contains("The string foo is not a valid default or caret version requirement.");
  }
}
