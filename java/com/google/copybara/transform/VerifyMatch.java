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

package com.google.copybara.transform;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A source code pseudo-transformation which verifies that all specified files satisfy a RegEx.
 * Does not actually transform any code, but will throw errors on failure. Not applied in reversals.
 */
public final class VerifyMatch implements Transformation {

  private final Pattern pattern;
  private final boolean verifyNoMatch;
  private final Glob fileMatcherBuilder;
  private final WorkflowOptions workflowOptions;

  private VerifyMatch(Pattern pattern, boolean verifyNoMatch, Glob fileMatcherBuilder,
      WorkflowOptions workflowOptions) {
    this.pattern = Preconditions.checkNotNull(pattern);
    this.verifyNoMatch = verifyNoMatch;
    this.fileMatcherBuilder = Preconditions.checkNotNull(fileMatcherBuilder);
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("Pattern", pattern)
        .add("verifyNoMatch", verifyNoMatch)
        .add("path", fileMatcherBuilder)
        .toString();
  }

  @Override
  public void transform(TransformWork work, Console console)
      throws IOException, ValidationException {
    Path checkoutDir = work.getCheckoutDir();
    VerifyMatchVisitor visitor = new VerifyMatchVisitor(pattern,
        fileMatcherBuilder.relativeTo(checkoutDir), verifyNoMatch);
    Files.walkFileTree(checkoutDir, visitor);
    List<String> errors = visitor.getErrors();
    for (String error : errors) {
      console.error(String.format("File '%s' failed validation '%s'.", error, describe()));
    }
    if (errors.size() != 0) {
      throw new ValidationException(
          String.format("%d file(s) failed the validation of %s.", errors.size(), describe()));
    }
  }

  @Override
  public String describe() {
    return String.format("Verify match '%s'", pattern);
  }

  @Override
  public Transformation reverse() {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  public static VerifyMatch create(Location location, String regEx, Glob paths,
      boolean verifyNoMatch, WorkflowOptions workflowOptions)
      throws EvalException {
    Pattern parsed;
    try {
      parsed = Pattern.compile(regEx, Pattern.MULTILINE);
    } catch (PatternSyntaxException e) {
      throw new EvalException(location, String.format("Regex '%s' is invalid.", regEx), e);
    }
    return new VerifyMatch(parsed, verifyNoMatch, paths, workflowOptions);
  }
}
