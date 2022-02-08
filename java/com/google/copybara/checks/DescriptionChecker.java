/*
 * Copyright (C) 2022 Google Inc.
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

import com.google.copybara.util.console.Console;
import com.google.errorprone.annotations.CheckReturnValue;
import java.io.IOException;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;

/** An interface for performing checks and transformations on change descriptions. */
@StarlarkBuiltin(
    name = "description_checker",
    doc = "A checker to be run on change descriptions",
    documented = false)
public interface DescriptionChecker extends StarlarkValue {

  /**
   * Process the passed description, validates it and optionally transform to an acceptable
   * description
   *
   * @param description current description of the change
   * @return returns the description that should be used for the change
   */
  @CheckReturnValue
  String processDescription(String description, Console console)
      throws CheckerException, IOException;
}
