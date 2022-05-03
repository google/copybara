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

import com.google.re2j.Matcher;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

/** An Starlark wrapper of re2j Matcher class */
@StarlarkBuiltin(
    name = "re2_matcher",
    doc = "A RE2 regex pattern matcher object to perform regexes in Starlark")
public class StarlarkMatcher implements StarlarkValue {

  private final Matcher matcher;

  public StarlarkMatcher(Matcher matcher) {
    this.matcher = matcher;
  }

  @StarlarkMethod(name = "matches", doc = "Return true if the string matches the regex pattern.")
  public boolean matches() {
    return matcher.matches();
  }

  @StarlarkMethod(
      name = "find",
      doc = "Return true if the string matches the regex pattern.",
      parameters = {
          @Param(name = "start",
              doc = "The input position where the search begins",
              named = true,
              allowedTypes = {
                  @ParamType(type = StarlarkInt.class),
                  @ParamType(type = NoneType.class),
              },
              defaultValue = "None")})
  public boolean find(Object start) throws EvalException {
    return Starlark.isNullOrNone(start)
        ? matcher.find()
        : matcher.find(((StarlarkInt) start).toInt("start"));
  }

  @StarlarkMethod(
      name = "start",
      doc = "Return the start position of a matching group",
      parameters = {
          @Param(name = "group", named = true,
              allowedTypes = {
                  @ParamType(type = StarlarkInt.class),
                  @ParamType(type = String.class),
              },
              defaultValue = "0")})
  public int start(Object group) throws EvalException {
    try {
      return group instanceof String
          ? matcher.start(((String) group))
          : matcher.start(((StarlarkInt) group).toInt("group"));

    } catch (IllegalStateException e) {
      throw new EvalException(
          "Call to start() is not allowed before calling matches()", e);
    }
  }

  @StarlarkMethod(
      name = "group",
      doc = "Return a matching group",
      parameters = {
          @Param(name = "group", named = true,
              allowedTypes = {
                  @ParamType(type = StarlarkInt.class),
                  @ParamType(type = String.class),
              },
              defaultValue = "0")})
  public String group(Object group) throws EvalException {
    try {

      return group instanceof String
          ? matcher.group(((String) group))
          : matcher.group(((StarlarkInt) group).toInt("group"));
    } catch (IllegalStateException e) {
      throw new EvalException(
          "Call to group() is not allowed before calling matches()", e);
    }
  }

  @StarlarkMethod(
      name = "end",
      doc = "Return the end position of a matching group",
      parameters = {
          @Param(name = "group", named = true,
              allowedTypes = {
                  @ParamType(type = StarlarkInt.class),
                  @ParamType(type = String.class),
              },
              defaultValue = "0")})
  public int end(Object group) throws EvalException {
    try {
      return group instanceof String
          ? matcher.end(((String) group))
          : matcher.end(((StarlarkInt) group).toInt("group"));

    } catch (IllegalStateException e) {
      throw new EvalException(
          "Call to end() is not allowed before calling matches()", e);
    }
  }

  @StarlarkMethod(
      name = "group_count",
      doc = "Return the number of groups found for a match")
  public int groupCount() {
    return matcher.groupCount();
  }

  @StarlarkMethod(
      name = "replace_all",
      doc = "Replace all instances matching the regex",
      parameters = {
          @Param(name = "replacement", named = true, defaultValue = "0")})
  public String replaceAll(String replacement) {
    return matcher.replaceAll(replacement);
  }

  @StarlarkMethod(
      name = "replace_first",
      doc = "Replace the first instance matching the regex",
      parameters = {
          @Param(name = "replacement", named = true, defaultValue = "0")})
  public String replaceFirst(String replacement) {
    return matcher.replaceFirst(replacement);
  }
}
