/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.buildozer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import net.starlark.java.eval.EvalException;

/**
 * Specifies a target, including the package and name of target.
 */
final class Target {

  private static final Pattern TARGET_NAME_PATTERN = Pattern.compile("[^:]*:[^:]+");

  private final String pkg;
  private final String name;

  private Target(String[] components) {
    checkArgument(components.length == 2, "%s", Arrays.asList(components));

    this.pkg = checkNotNull(components[0], "pkg");
    this.name = checkNotNull(components[1], "name");
  }

  public String getPackage() {
    return pkg;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return pkg + ":" + name;
  }

  /**
   * Parses a target specified in configuration.
   *
   * @param configString target specified in the form {@code PKG:TARGET_NAME}
   * @throws EvalException if {@code configString} is not formatted correctly
   */
  static Target fromConfig(String configString) throws EvalException {
    if (configString.startsWith("/")) {
      throw new EvalException("target must be relative and not start with '/' or '//'");
    }
    if (!TARGET_NAME_PATTERN.matcher(configString).matches()) {
      throw new EvalException(
          "target must be in the form of {PKG}:{TARGET_NAME}, e.g. foo/bar:baz");
    }
    return new Target(configString.split(":", 2));
  }

  static ImmutableList<String> asStringList(List<Target> targets) {
    return targets.stream().map(t -> t.toString()).collect(ImmutableList.toImmutableList());
  }
}
