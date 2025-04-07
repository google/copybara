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

package com.google.copybara.onboard;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Option;
import com.google.copybara.jcommander.MapConverter;
import com.google.copybara.onboard.core.AskInputProvider.Mode;

/**
 * Options for the generator command
 */
@Parameters(separators = "=")
public class GeneratorOptions implements Option {

  @Parameter(
      names = {"--generator-ask"},
      description =
          "Config generator mode when a value is not found. Valid modes:" + "auto, confirm, fail")
  public Mode askMode = Mode.CONFIRM;

  @Parameter(
      names = {"--template"},
      description = "Name of the template to use for generating the config")
  @VisibleForTesting
  public String template;

  @Parameter(
      names = {"--inputs"},
      description = "Inputs for code generation",
      converter = MapConverter.class)
  public ImmutableMap<String, String> inputs = ImmutableMap.of();

  @Parameter(
      names = "--new-package",
      description = "Whether or not files from this package exist in the destination.")
  public boolean newPackage;

  @Parameter(
      names = "--compute-glob-ignore-carriage-return",
      description =
          "Whether to ignore carriage return characters in file content comparisons during glob"
              + " generation.",
      arity = 1)
  public boolean computeGlobIgnoreCarriageReturn = true;

  @Parameter(
      names = "--compute-glob-ignore-whitespace",
      description =
          "Whether to ignore whitespace in file content comparisons during glob generation.",
      arity = 1)
  public boolean computeGlobIgnoreWhitespace = true;
}
