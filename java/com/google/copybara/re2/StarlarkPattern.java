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

package com.google.copybara.re2;

import com.google.re2j.Pattern;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** An Starlark wrapper of re2j Pattern class */
@StarlarkBuiltin(
    name = "re2_pattern",
    doc = "A RE2 regex pattern object to perform regexes in Starlark")
public class StarlarkPattern implements StarlarkValue {

  private final Pattern pattern;

  public StarlarkPattern(Pattern pattern) {
    this.pattern = pattern;
  }

  @StarlarkMethod(
      name = "matches",
      doc = "Return true if the string matches the regex pattern",
      parameters = {@Param(name = "input", named = true)})
  public boolean matches(String input) {
    return pattern.matches(input);
  }

  @StarlarkMethod(
      name = "matcher",
      doc = "Return a Matcher for the given input.",
      parameters = {@Param(name = "input", named = true)})
  public StarlarkMatcher matcher(String input) {
    return new StarlarkMatcher(pattern.matcher(input));
  }
}
