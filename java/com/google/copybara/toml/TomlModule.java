/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.toml;

import com.google.copybara.doc.annotations.Example;
import com.google.copybara.exception.ValidationException;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/**
 * Module for parsing TOML in Starlark
 */
@StarlarkBuiltin(name = "toml", doc = "Module for parsing TOML in Copybara.")
public class TomlModule implements StarlarkValue {
  @StarlarkMethod(
      name = "parse",
      doc = "Parse the TOML content. Returns a toml object.",
      parameters = {
          @Param(
              name = "content",
              doc = "TOML content to be parsed",
              allowedTypes = {@ParamType(type = String.class)},
              named = true),
      })
  @Example(
      title = "Parsing a TOML string",
      before = "To parse a TOML string, pass the string into the parser.",
      code = "toml.parse(\"foo = 42\")")
  public TomlContent parse(String tomlContent) throws ValidationException {
    TomlParseResult result = Toml.parse(tomlContent);

    ValidationException.checkCondition(
        !result.hasErrors(),
        "There were errors parsing the TOML string. Errors: %s",
        result.errors().toString());

    return new TomlContent(result);
  }
}
