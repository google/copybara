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

package com.google.copybara.checks;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApiCheckerTest {

  @Test
  public void testCheck() throws CheckerException {
    ApiChecker checker = new ApiChecker(new FakeChecker() {
      @Override
      public void doCheck(ImmutableMap<String, String> fields, Console console) {
      }
    }, new TestingConsole());

    checker.check("foo", new Object());
    checker.check("foo", new Object(), "bar", new Object());
    checker.check("foo", new Object(), "bar", new Object(), "baz", new Object());
  }

  @Test
  public void testCheckError() {
    ApiChecker checker = new ApiChecker(new FakeChecker() {
      @Override
      public void doCheck(ImmutableMap<String, String> fields, Console console)
          throws CheckerException {
        throw new CheckerException("Check failed!");
      }
    }, new TestingConsole());

    ValidationException e =
        assertThrows(ValidationException.class, () -> checker.check("foo", new Object()));
    assertThat(e).hasMessageThat().isEqualTo("Check failed!");
    ValidationException failedCheck =
        assertThrows(
            ValidationException.class,
            () -> checker.check("foo", new Object(), "bar", new Object()));
    assertThat(failedCheck).hasMessageThat().isEqualTo("Check failed!");
    ValidationException failedCheckArgs =
        assertThrows(
            ValidationException.class,
            () -> checker.check("foo", new Object(), "bar", new Object(), "baz", new Object()));
    assertThat(failedCheckArgs).hasMessageThat().isEqualTo("Check failed!");
  }

  @Test
  public void testCheckInternalError() throws CheckerException {
    ApiChecker checker = new ApiChecker(new FakeChecker() {
      @Override
      public void doCheck(ImmutableMap<String, String> fields, Console console)
          throws IOException {
        throw new IOException("Tool error!");
      }
    }, new TestingConsole());

    RuntimeException e1 =
        assertThrows(RuntimeException.class, () -> checker.check("foo", new Object()));
    assertThat(e1).hasMessageThat().isEqualTo("Error running checker");
    RuntimeException e2 =
        assertThrows(
            RuntimeException.class, () -> checker.check("foo", new Object(), "bar", new Object()));
    assertThat(e2).hasMessageThat().isEqualTo("Error running checker");
    RuntimeException e3 =
        assertThrows(
            RuntimeException.class,
            () -> checker.check("foo", new Object(), "bar", new Object(), "baz", new Object()));
    assertThat(e3).hasMessageThat().isEqualTo("Error running checker");
  }

  private abstract static class FakeChecker implements Checker {
    @Override
    public void doCheck(Path target, Console console) {
      throw new UnsupportedOperationException("Not implemented");
    }

  }
}
