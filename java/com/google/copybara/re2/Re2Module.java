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

import com.google.copybara.doc.annotations.Example;
import com.google.re2j.Pattern;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** Regex functions to work with re2 like regexes in Starlark */
@StarlarkBuiltin(name = "re2", doc = "Set of functions to work with regexes in Copybara.")
public class Re2Module implements StarlarkValue {

  @StarlarkMethod(
      name = "compile",
      doc = "Create a regex pattern",
      parameters = {
          @Param(name = "regex")})
  @Example(title = "Simple regex",
      before = "Patterns need to be compiled before using them:",
      code = "re2.compile(\"a(.*)b\").matches('accccb')")
  public StarlarkPattern compile(String regex) {
    return new StarlarkPattern(Pattern.compile(regex));
  }
}
