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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.util.console.Console;
import java.io.IOException;

/**
 * A checker for API clients that delegates on a {@link Checker} and provides convenience methods
 * for checking one or more pairs of field names and values, plus error handling.
 */
public class ApiChecker {

  private final Checker checker;
  private final Console console;

  public ApiChecker(Checker checker, Console console) {
    this.checker = Preconditions.checkNotNull(checker);
    this.console = Preconditions.checkNotNull(console);
  }

  /** Performs a check on the given request field. */
  public void check(String field, Object value) throws CheckerException {
    doCheck(ImmutableMap.of(field, value.toString()));
  }

  /** Performs a check on the given request fields. */
  public void check(String field1, Object value1, String field2, Object value2)
      throws CheckerException {
    doCheck(ImmutableMap.of(field1, value1.toString(), field2, value2.toString()));
  }

  /** Performs a check on the given request fields. */
  public void check(
      String field1, Object value1, String field2, Object value2, String field3, Object value3)
      throws CheckerException {
    doCheck(
        ImmutableMap.of(
            field1, value1.toString(), field2, value2.toString(), field3, value3.toString()));
  }

  private void doCheck(ImmutableMap<String, String> data) throws CheckerException {
    try {
      checker.doCheck(data, console);
    } catch (IOException e) {
      throw new RuntimeException("Error running checker", e);
    }
  }
}
