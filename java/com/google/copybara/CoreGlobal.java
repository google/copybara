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

package com.google.copybara;

import com.google.copybara.doc.annotations.Example;
import com.google.copybara.util.Glob;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkGlobalLibrary;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.Type;
import java.util.List;

/**
 * A module to expose Skylark glob(), parse_message(), etc functions.
 *
 * <p>Don't add functions here and prefer "core" namespace unless it is something really general
 */
@SkylarkGlobalLibrary
public class CoreGlobal {

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "glob",
      doc = "Glob returns a list of every file in the workdir that matches at least one"
          + " pattern in include and does not match any of the patterns in exclude.",
      parameters = {
          @Param(name = "include", type = SkylarkList.class,
              generic1 = String.class, doc = "The list of glob patterns to include"),
          @Param(name = "exclude", type = SkylarkList.class,
              generic1 = String.class, doc = "The list of glob patterns to exclude",
              defaultValue = "[]", named = true, positional = false),
      }, useLocation = true)
  @Example(title = "Simple usage",
      before = "Include all the files under a folder except for `internal` folder files:",
      code = "glob([\"foo/**\"], exclude = [\"foo/internal/**\"])")
  @Example(title = "Multiple folders",
      before = "Globs can have multiple inclusive rules:",
      code = "glob([\"foo/**\", \"bar/**\", \"baz/**.java\"])",
      after = "This will include all files inside `foo` and `bar` folders and Java files"
          + " inside `baz` folder.")
  @Example(title = "Multiple excludes",
      before = "Globs can have multiple exclusive rules:",
      code = "glob([\"foo/**\"], exclude = [\"foo/internal/**\", \"foo/confidential/**\" ])",
      after = "Include all the files of `foo` except the ones in `internal` and `confidential`"
          + " folders")
  @Example(title = "All BUILD files recursively",
      before = "Copybara uses Java globbing. The globbing is very similar to Bash one. This"
          + " means that recursive globbing for a filename is a bit more tricky:",
      code = "glob([\"BUILD\", \"**/BUILD\"])",
      after = "This is the correct way of matching all `BUILD` files recursively, including the"
          + " one in the root. `**/BUILD` would only match `BUILD` files in subdirectories.")
  @Example(title = "Matching multiple strings with one expression",
      before = "While two globs can be used for matching two directories, there is a more"
          + " compact approach:",
      code = "glob([\"{java,javatests}/**\"])",
      after = "This matches any file in `java` and `javatests` folders.")
  public Glob glob(SkylarkList<?> include, SkylarkList<?> exclude, Location location)
      throws EvalException {
    List<String> includeStrings = Type.STRING_LIST.convert(include, "include");
    List<String> excludeStrings = Type.STRING_LIST.convert(exclude, "exclude");
    try {
      return Glob.createGlob(includeStrings, excludeStrings);
    } catch (IllegalArgumentException e) {
      throw new EvalException(location, String.format(
          "Cannot create a glob from: include='%s' and exclude='%s': %s",
          includeStrings, excludeStrings, e.getMessage()), e);
    }
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "parse_message",
      doc = "Returns a ChangeMessage parsed from a well formed string.",
      parameters = {
          @Param(name = "message", named = true, type = String.class,
              doc = "The contents of the change message"),
      }, useLocation = true)
  public ChangeMessage parseMessage(String changeMessage, Location location) throws EvalException {
    try {
      return ChangeMessage.parseMessage(changeMessage);
    } catch (RuntimeException e) {
      throw new EvalException(location, String.format(
          "Cannot parse change message '%s': %s", changeMessage, e.getMessage()), e);
    }
  }
}
