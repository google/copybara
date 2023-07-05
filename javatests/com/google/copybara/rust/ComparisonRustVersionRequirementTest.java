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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ComparisonRustVersionRequirementTest {
  @Test
  public void testGetCorrectRustVersionRequirementObject() throws Exception {
    assertThat(getVersionRequirement(">= 1.2.3"))
        .isInstanceOf(ComparisonRustVersionRequirement.class);
  }

  @Test
  public void testEqualsTo() throws Exception {
    assertThat(getVersionRequirement("= 1.2.0").fulfills("1.2.0")).isTrue();
    assertThat(getVersionRequirement("= 1.2.0").fulfills("2.3.1")).isFalse();
    assertThat(getVersionRequirement("= 1.3").fulfills("1.3.0")).isTrue();
    assertThat(getVersionRequirement("= 1.3").fulfills("1.3.2")).isTrue();
    assertThat(getVersionRequirement("= 1.3").fulfills("1.4")).isFalse();
    assertThat(getVersionRequirement("= 2").fulfills("2.0.0")).isTrue();
    assertThat(getVersionRequirement("= 2").fulfills("2.4.3")).isTrue();
    assertThat(getVersionRequirement("= 2").fulfills("3.0.0")).isFalse();
  }

  @Test
  public void testGreaterThan() throws Exception {
    assertThat(getVersionRequirement("> 1.2.0").fulfills("2.0.5")).isTrue();
    assertThat(getVersionRequirement("> 1.2.0").fulfills("1.2.0")).isFalse();
  }

  @Test
  public void testGreaterThanOrEqualTo() throws Exception {
    assertThat(getVersionRequirement(">= 1.2.0").fulfills("2.0.5")).isTrue();
    assertThat(getVersionRequirement(">= 1.2.0").fulfills("1.2.0")).isTrue();
    assertThat(getVersionRequirement(">= 1.2.0").fulfills("0.2.4")).isFalse();
  }

  @Test
  public void testLessThan() throws Exception {
    assertThat(getVersionRequirement("< 0.8.5").fulfills("0.1.0")).isTrue();
    assertThat(getVersionRequirement("< 0.8.5").fulfills("0.8.5")).isFalse();
  }

  @Test
  public void testLessThanOrEqualTo() throws Exception {
    assertThat(getVersionRequirement("<= 3.0.0").fulfills("2.5.4")).isTrue();
    assertThat(getVersionRequirement("<= 3.0.0").fulfills("3.0.0")).isTrue();
    assertThat(getVersionRequirement("<= 3.0.0").fulfills("3.9.2")).isFalse();
    assertThat(getVersionRequirement("<= 0.6").fulfills("0.6.4")).isTrue();
    assertThat(getVersionRequirement("<= 0.6").fulfills("0.7.0")).isFalse();
  }
}
