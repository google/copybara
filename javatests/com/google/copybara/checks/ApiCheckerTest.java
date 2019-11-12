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

import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
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
  public void testCheckError() throws CheckerException {
    ApiChecker checker = new ApiChecker(new FakeChecker() {
      @Override
      public void doCheck(ImmutableMap<String, String> fields, Console console)
          throws CheckerException {
        throw new CheckerException("Check failed!");
      }
    }, new TestingConsole());

    try {
      checker.check("foo", new Object());
      fail();
    } catch (ValidationException e) {
      Truth.assertThat(e).hasMessageThat().isEqualTo("Check failed!");
    }
    try {
      checker.check("foo", new Object(), "bar", new Object());
      fail();
    } catch (ValidationException e) {
      Truth.assertThat(e).hasMessageThat().isEqualTo("Check failed!");
    }
    try {
      checker.check("foo", new Object(), "bar", new Object(), "baz", new Object());
      fail();
    } catch (ValidationException e) {
      Truth.assertThat(e).hasMessageThat().isEqualTo("Check failed!");
    }
  }

  private abstract static class FakeChecker implements Checker {
    @Override
    public void doCheck(Path target, Console console) {
      throw new UnsupportedOperationException("Not implemented");
    }

  }
}
