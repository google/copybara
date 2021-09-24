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

import com.google.copybara.authoring.Author;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.Library;
import com.google.copybara.util.Glob;
import java.util.List;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

/**
 * A module to expose Skylark glob(), parse_message(), etc functions.
 *
 * <p>Don't add functions here and prefer "core" namespace unless it is something really general
 */
@Library
public class CoreGlobal implements StarlarkValue {

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "glob",
      doc =
          "Returns an object which matches every file in the workdir that matches at least one"
              + " pattern in include and does not match any of the patterns in exclude.",
      parameters = {
        @Param(
            name = "include",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            named = true,
            doc = "The list of glob patterns to include"),
        @Param(
            name = "exclude",
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            doc = "The list of glob patterns to exclude",
            defaultValue = "[]",
            named = true,
            positional = false),
      })
  @Example(
      title = "Simple usage",
      before = "Include all the files under a folder except for `internal` folder files:",
      code = "glob([\"foo/**\"], exclude = [\"foo/internal/**\"])")
  @Example(
      title = "Multiple folders",
      before = "Globs can have multiple inclusive rules:",
      code = "glob([\"foo/**\", \"bar/**\", \"baz/**.java\"])",
      after =
          "This will include all files inside `foo` and `bar` folders and Java files"
              + " inside `baz` folder.")
  @Example(
      title = "Multiple excludes",
      before = "Globs can have multiple exclusive rules:",
      code = "glob([\"foo/**\"], exclude = [\"foo/internal/**\", \"foo/confidential/**\" ])",
      after =
          "Include all the files of `foo` except the ones in `internal` and `confidential`"
              + " folders")
  @Example(
      title = "All BUILD files recursively",
      before =
          "Copybara uses Java globbing. The globbing is very similar to Bash one. This"
              + " means that recursive globbing for a filename is a bit more tricky:",
      code = "glob([\"BUILD\", \"**/BUILD\"])",
      after =
          "This is the correct way of matching all `BUILD` files recursively, including the"
              + " one in the root. `**/BUILD` would only match `BUILD` files in subdirectories.")
  @Example(
      title = "Matching multiple strings with one expression",
      before =
          "While two globs can be used for matching two directories, there is a more"
              + " compact approach:",
      code = "glob([\"{java,javatests}/**\"])",
      after = "This matches any file in `java` and `javatests` folders.")
  @Example(
      title = "Glob union",
      before =
          "This is useful when you want to exclude a broad subset of files but you want to"
              + " still include some of those files.",
      code =
          "glob([\"folder/**\"], exclude = [\"folder/**.excluded\"])"
              + " + glob([\'folder/includeme.excluded\'])",
      after =
          "This matches all the files in `folder`, excludes all files in that folder that"
              + " ends with `.excluded` but keeps `folder/includeme.excluded`<br><br>"
              + "`+` operator for globs is equivalent to `OR` operation.")
  @Example(
      title = "Glob difference",
      before =
          "This is another way to exclude a broad subset of files, but still include some of those"
              + " files.",
      code =
          "glob([\"folder/**\"]) - glob([\"folder/**.excluded\"],"
              + " exclude=[\"folder/includeme.excluded\"])",
      after =
          "This matches the same file as in the previous example.<br><br>"
              + "`-` operator for globs is equivalent to a set difference operation.")
  public Glob glob(Sequence<?> include, Sequence<?> exclude) throws EvalException {
    List<String> includeStrings = SkylarkUtil.convertStringList(include, "include");
    List<String> excludeStrings = SkylarkUtil.convertStringList(exclude, "exclude");
    try {
      return Glob.createGlob(includeStrings, excludeStrings);
    } catch (IllegalArgumentException e) {
      throw Starlark.errorf(
          "Cannot create a glob from: include='%s' and exclude='%s': %s",
          includeStrings, excludeStrings, e.getMessage());
    }
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "parse_message",
      doc = "Returns a ChangeMessage parsed from a well formed string.",
      parameters = {
        @Param(name = "message", named = true, doc = "The contents of the change message"),
      })
  public ChangeMessage parseMessage(String changeMessage) throws EvalException {
    try {
      return ChangeMessage.parseMessage(changeMessage);
    } catch (RuntimeException e) {
      throw Starlark.errorf("Cannot parse change message '%s': %s", changeMessage, e.getMessage());
    }
  }

  @StarlarkMethod(
      name = "new_author",
      doc = "Create a new author from a string with the form 'name <foo@bar.com>'",
      parameters = {
        @Param(
            name = "author_string",
            named = true,
            doc = "A string representation of the author with the form 'name <foo@bar.com>'"),
      })
  @Example(
      title = "Create a new author",
      before = "",
      code = "new_author('Foo Bar <foobar@myorg.com>')")
  public Author newAuthor(String authorString) throws EvalException {
    return Author.parse(authorString);
    }
}
