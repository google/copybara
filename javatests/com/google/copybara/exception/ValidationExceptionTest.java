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

import static org.junit.Assert.fail;

import com.google.common.truth.Truth;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ValidationExceptionTest {

  @Test
  public void testCheckExceptionFormat(){
    try {
      ValidationException.checkCondition(false, "foo");
      fail();
    } catch (ValidationException e) {
      Truth.assertThat(e).hasMessageThat().isEqualTo("foo");
    }
    try {
      ValidationException.checkCondition(false, "%F is foo");
      fail();
    } catch (ValidationException e) {
      Truth.assertThat(e).hasMessageThat().isEqualTo("%F is foo");
    }
    try {
      ValidationException.checkCondition(false, "%s is foo");
      fail();
    } catch (ValidationException e) {
      Truth.assertThat(e).hasMessageThat().isEqualTo("%s is foo");
    }
    try {
      ValidationException.checkCondition(false, "%s is foo", "bar");
      fail();
    } catch (ValidationException e) {
      Truth.assertThat(e).hasMessageThat().isEqualTo("bar is foo");
    }
  }
}
