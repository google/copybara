/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.copybara.exception;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ValidationExceptionTest {

  @Test
  public void testCheckExceptionFormat(){
    ValidationException e1 =
        assertThrows(
            ValidationException.class, () -> ValidationException.checkCondition(false, "foo"));
    assertThat(e1).hasMessageThat().isEqualTo("foo");
    ValidationException e2 =
        assertThrows(
            ValidationException.class,
            () -> ValidationException.checkCondition(false, "%F is foo"));
    assertThat(e2).hasMessageThat().isEqualTo("%F is foo");
    ValidationException e3 =
        assertThrows(
            ValidationException.class,
            () -> ValidationException.checkCondition(false, "%s is foo"));
    assertThat(e3).hasMessageThat().isEqualTo("%s is foo");
    ValidationException e4 =
        assertThrows(
            ValidationException.class,
            () -> ValidationException.checkCondition(false, "%s is foo", "bar"));
    assertThat(e4).hasMessageThat().isEqualTo("bar is foo");
  }
}
