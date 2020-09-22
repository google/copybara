/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.testing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.checks.Checker;
import com.google.copybara.checks.CheckerException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map.Entry;
import net.starlark.java.annot.StarlarkBuiltin;

/**
 * A dummy, not very efficient, checker for tests.
 *
 * <p>TODO(danielromero): Promote to a real transform that uses regex
 */
@StarlarkBuiltin(name = "dummy_checker", doc = "A dummy checker for tests")
public class DummyChecker implements Checker {

  private final ImmutableSet<String> badWords;

  /**
   * Creates a new checker.
   *
   * @param badWords Case-insensitive set of bad words
   */
  public DummyChecker(ImmutableSet<String> badWords) {
    this.badWords = badWords.stream().map(String::toLowerCase).collect(ImmutableSet.toImmutableSet());
  }

  /**
   * Fails on first bad word found.
   */
  @Override
  public void doCheck(ImmutableMap<String, String> fields, Console console)
      throws CheckerException {
    for (Entry<String, String> entry : fields.entrySet()) {
      for (String badWord : badWords) {
        if (entry.getValue().toLowerCase().contains(badWord)) {
          throw new CheckerException(
              String.format("Bad word '%s' found: field '%s'", badWord, entry.getKey()));
        }
      }
    }
  }

  /**
   * Does a line by line check. Does not detect bad words if multi-line. Fails on first bad word
   * found.
   */
  @Override
  public void doCheck(Path target, Console console) throws CheckerException, IOException {
    int lineNum = 0;
    for (String line : Files.readAllLines(target)) {
      lineNum++;
      for (String badWord : badWords) {
        if (line.toLowerCase().contains(badWord)) {
          throw new CheckerException(
              String.format("Bad word '%s' found: %s:%d", badWord, target, lineNum));
        }
      }
    }
  }
}
