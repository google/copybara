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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;
import com.google.copybara.LocalParallelizer;
import com.google.copybara.LocalParallelizer.TransformFunc;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.treestate.TreeState.FileState;
import com.google.copybara.util.Glob;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.syntax.Location;

/**
 * A source code pseudo-transformation which verifies that all specified files satisfy a RegEx.
 * Does not actually transform any code, but will throw errors on failure. Not applied in reversals.
 */
public final class VerifyMatch implements Transformation {

  private final Pattern pattern;
  private final boolean verifyNoMatch;
  private final boolean alsoOnReversal;
  private final Glob fileMatcherBuilder;
  private final LocalParallelizer parallelizer;
  private final Location location;

  private VerifyMatch(Pattern pattern, boolean verifyNoMatch, boolean alsoOnReversal,
      Glob fileMatcherBuilder, LocalParallelizer parallelizer, Location location) {
    this.pattern = checkNotNull(pattern);
    this.verifyNoMatch = verifyNoMatch;
    this.alsoOnReversal = alsoOnReversal;
    this.fileMatcherBuilder = checkNotNull(fileMatcherBuilder);
    this.parallelizer = parallelizer;
    this.location = checkNotNull(location);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("Pattern", pattern)
        .add("verifyNoMatch", verifyNoMatch)
        .add("also_on_reversal", alsoOnReversal)
        .add("path", fileMatcherBuilder)
        .toString();
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    Path checkoutDir = work.getCheckoutDir();
    Iterable<FileState> files = work.getTreeState().find(
        fileMatcherBuilder.relativeTo(checkoutDir));

    Iterable<String> errors = Iterables.concat(
        parallelizer.run(files, new BatchRun(work.getCheckoutDir())));

    int size = 0;
    for (String error : errors) {
      size++;
      work.getConsole().error(String.format("File '%s' failed validation '%s'.", error,
                                            describe()));
    }
    work.getTreeState().notifyNoChange();

    ValidationException.checkCondition(
        size == 0,
        "%d file(s) failed the validation of %s, located at %s.", size, describe(), location);

    return TransformationStatus.success();
  }

  private class BatchRun implements TransformFunc<FileState, List<String>> {

    private final Path checkoutDir;

    private BatchRun(Path checkoutDir) {
      this.checkoutDir = checkNotNull(checkoutDir);
    }

    @Override
    public List<String> run(Iterable<FileState> files)
        throws IOException, ValidationException {
      List<String> errors = new ArrayList<>();
      // TODO(malcon): Remove reconstructing pattern once RE2J doesn't synchronize on matching.
      Pattern batchPattern = Pattern.compile(pattern.pattern(), pattern.flags());
      for (FileState file : files) {
        if (Files.isSymbolicLink(file.getPath())) {
          continue;
        }
        String originalFileContent = new String(Files.readAllBytes(file.getPath()), UTF_8);
        if (verifyNoMatch == batchPattern.matcher(originalFileContent).find()) {
          errors.add(checkoutDir.relativize(file.getPath()).toString());
        }
      }
      return errors;
    }
  }

  @Override
  public String describe() {
    return String.format("Verify match '%s'", pattern);
  }

  @Override
  public Location location() {
    return location;
  }

  @Override
  public Transformation reverse() {
    if (alsoOnReversal) {
      return new ExplicitReversal(this, this);
    }
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  public static VerifyMatch create(Location location, String regEx, Glob paths,
      boolean verifyNoMatch, boolean alsoOnReversal, LocalParallelizer parallelizer)
      throws EvalException {
    Pattern parsed;
    try {
      parsed = Pattern.compile(regEx, Pattern.MULTILINE);
    } catch (PatternSyntaxException ex) {
      throw Starlark.errorf("Regex '%s' is invalid: %s", regEx, ex.getMessage());
    }
    return new VerifyMatch(parsed, verifyNoMatch, alsoOnReversal, paths, parallelizer, location);
  }
}
