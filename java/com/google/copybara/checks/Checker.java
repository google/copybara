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

package com.google.copybara.checks;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;

/** A generic interface for performing checks on string contents and files. */
@StarlarkBuiltin(
    name = "checker",
    doc = "A checker to be run on arbitrary data and files",
    documented = false)
public interface Checker extends StarlarkValue {

  /**
   * Performs a check on the given contents.
   *
   * @throws CheckerException if the check produced errors
   * @throws IOException if the checker could not be run
   */
  void doCheck(ImmutableMap<String, String> fields, Console console)
      throws CheckerException, IOException;

  /**
   * Performs a check on the files inside a given path.
   *
   * @throws CheckerException if the check produced errors
   * @throws IOException if the checker could not be run
   */
  void doCheck(Path target, Console console) throws CheckerException, IOException;
}
