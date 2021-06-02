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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.LocalParallelizer;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.RegexTemplateTokens.Replacer;
import com.google.copybara.treestate.TreeState.FileState;
import com.google.copybara.util.Glob;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.syntax.Location;

/**
 * A source code transformation which replaces a regular expression with some other string.
 *
 * <p>The replacement is defined as two strings with interpolations and a mapping of interpolation
 * names to regular expressions. For instance,
 * <pre>
 * !Replace
 *  before: "foo${x}bar"
 *  after: "bar${x}foo"
 *  regexGroups:
 *   x: "[A-Z]+"
 * </pre>
 *
 * This will replace fooABCDbar with barABCDfoo or vice-versa.
 *
 * <p>This transformation is line-based and only replaces the first instance of the pattern on a
 * line.
 *
 * TODO(copybara-team): Consider making this configurable to replace multiple matches.
 */
public final class Replace implements Transformation {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final RegexTemplateTokens before;
  private final RegexTemplateTokens after;
  private final ImmutableMap<String, Pattern> regexGroups;
  private final boolean firstOnly;
  private final boolean multiline;
  private final boolean repeatedGroups;
  private final Glob paths;
  private final ImmutableList<Pattern> patternsToIgnore;
  private final WorkflowOptions workflowOptions;
  private final Location location;

  private Replace(RegexTemplateTokens before, RegexTemplateTokens after,
      Map<String, Pattern> regexGroups, boolean firstOnly, boolean multiline,
      boolean repeatedGroups,
      Glob paths,
      List<Pattern> patternsToIgnore,
      WorkflowOptions workflowOptions, Location location) {
    this.before = checkNotNull(before);
    this.after = checkNotNull(after);
    this.regexGroups = ImmutableMap.copyOf(regexGroups);
    this.firstOnly = firstOnly;
    this.multiline = multiline;
    this.repeatedGroups = repeatedGroups;
    this.paths = checkNotNull(paths);
    this.patternsToIgnore = ImmutableList.copyOf(patternsToIgnore);
    this.workflowOptions = checkNotNull(workflowOptions);
    this.location = checkNotNull(location);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("before", before)
        .add("after", after)
        .add("regexGroups", regexGroups)
        .add("firstOnly", firstOnly)
        .add("multiline", multiline)
        .add("path", paths)
        .add("patternsToIgnore", patternsToIgnore)
        .add("location", location)
        .toString();
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {

    work.getConsole().verboseFmt("Running Replace %s", this);
    if (before.getBefore().matches("") && !firstOnly) {
      work.getConsole().warnFmt("Replace %s matches the empty String, this is likely to cause"
          + " unintended behavior, unless it is a no-op.", this);
    }
    Path checkoutDir = work.getCheckoutDir();

    Iterable<FileState> files = work.getTreeState().find(
        paths.relativeTo(checkoutDir));
    BatchReplace batchReplace = new BatchReplace(this::createReplacer,
        before.getBefore().toString());
    workflowOptions.parallelizer().run(files, batchReplace);
    List<FileState> changed = batchReplace.getChanged();
    boolean matchedFile = batchReplace.isMatchedFile();
    logger.atInfo().log( "Applied %s to %d files. %d changed.",
        this, Iterables.size(files), changed.size());

    work.getTreeState().notifyModify(changed);
    if (changed.isEmpty()) {
      return TransformationStatus.noop(
          "Transformation '" + toString() + "' was a no-op because it didn't "
              + (matchedFile ? "change any of the matching files" : "match any file"));
    }
    return TransformationStatus.success();
  }

  @Override
  public String describe() {
    // before should be almost always unique so it is good enough for identifying the
    // transform.
    return "Replace " + before;
  }

  @Override
  public Replace reverse() throws NonReversibleValidationException {
    try {
      after.validateUnused();
    } catch (EvalException e) {
      throw new NonReversibleValidationException(
          "The transformation is not automatically reversible. Add an explicit reversal field with "
              + "core.transform: "
              + e.getMessage(),
          e.getCause());
    }
    //TODO remove repeatedGroups boolean?
    return new Replace(after, before, regexGroups, firstOnly, multiline, repeatedGroups,
        paths, patternsToIgnore, workflowOptions, location);
  }

  public static Replace create(Location location, String before, String after,
      Map<String, String> regexGroups, Glob paths, boolean firstOnly, boolean multiline,
      boolean repeatedGroups, List<String> patternsToIgnore,
      WorkflowOptions workflowOptions)
      throws EvalException {
    Map<String, Pattern> parsedGroups = parsePatterns(regexGroups);

    RegexTemplateTokens beforeTokens =
        new RegexTemplateTokens(before, parsedGroups, repeatedGroups, location);
    RegexTemplateTokens afterTokens = new RegexTemplateTokens(after, parsedGroups, repeatedGroups,
        location);

    beforeTokens.validateUnused();

    List<Pattern> parsedIgnorePatterns = new ArrayList<>();
    for (String toIgnore : patternsToIgnore) {
      try {
        parsedIgnorePatterns.add(Pattern.compile(toIgnore));
      } catch (PatternSyntaxException e) {
        throw Starlark.errorf("'patterns_to_ignore' includes invalid regex: %s", toIgnore);
      }
    }

    // Don't validate non-used interpolations in after since they are only relevant for reversable
    // transformations. And those are eagerly validated during config loading, because
    // when asking for the reverse 'after' is used as 'before', and it gets validated
    // with the check above.

    return new Replace(
        beforeTokens, afterTokens, parsedGroups, firstOnly, multiline, repeatedGroups, paths,
        parsedIgnorePatterns, workflowOptions, location);
  }

  public static Map<String, Pattern> parsePatterns(Map<String, String> regexGroups)
      throws EvalException {
    Map<String, Pattern> parsedGroups = new HashMap<>();
    for (Entry<String, String> group : regexGroups.entrySet()) {
      try {
        parsedGroups.put(group.getKey(), Pattern.compile(group.getValue()));
      } catch (PatternSyntaxException e) {
        throw Starlark.errorf(
            "'regex_groups' includes invalid regex for key %s: %s",
            group.getKey(), group.getValue());
      }
    }
    return parsedGroups;
  }

  private final static class BatchReplace
      implements LocalParallelizer.TransformFunc<FileState, Boolean> {


    private final Supplier<Replacer> replacerSupplier;

    private final List<FileState> changed = new ArrayList<>();
    private boolean matchedFile = false;
    private final boolean emptyBefore;

    BatchReplace(Supplier<Replacer> replacerSupplier, String before) {
      this.replacerSupplier = checkNotNull(replacerSupplier);
      emptyBefore = before.equals("");
    }

    public List<FileState> getChanged() {
      return changed;
    }

    boolean isMatchedFile() {
      return matchedFile;
    }

    @Override
    public Boolean run(Iterable<FileState> elements) throws IOException, ValidationException {
      Replacer replacer = replacerSupplier.get();
      List<FileState> changed = new ArrayList<>();
      boolean matchedFile = false;
      for (FileState file : elements) {
        if (Files.isSymbolicLink(file.getPath())) {
          continue;
        }
        matchedFile = true;
        String originalFileContent = new String(Files.readAllBytes(file.getPath()), UTF_8);

        if (!replacer.isFirstOnly() && emptyBefore && originalFileContent.length() > 10_000) {
          throw new ValidationException(
              "Error trying to replace empty string with text on a big file, this usually"
                  + " happens if you use the transform"
                  + " core.replace(before = '', after = 'some text') or, more commonly, when"
                  + " a you have a transform like core.replace(before = 'some text', after = '')"
                  + " and is reversed in another workflow. The effect of this transform is not"
                  + " what you want, as it will replace every single character with 'some text'."
                  + " In the case of the reverse, the fix is to either wrap the core.replace in:"
                  + " core.transform([core.replace(...)], reversal =[]) so that it doesn't do"
                  + " anything on the reversal or, even better, to use a reversible scrubber like"
                  + " core.replace(before = 'confidential text', after = 'some text that is safe"
                  + " to be public'): " + replacer.getLocation());
        }
        String transformed = replacer.replace(originalFileContent);
        if (!originalFileContent.equals(transformed)) {
          synchronized (this) {
            changed.add(file);
          }
          Files.write(file.getPath(), transformed.getBytes(UTF_8));
        }
      }
      synchronized (this) {
        this.matchedFile |= matchedFile;
        this.changed.addAll(changed);
      }
      // We cannot return null here.
      return true;
    }

  }

  public Replacer createReplacer() {
    return before.replacer(after, firstOnly, multiline, patternsToIgnore);
  }

  public Glob getPaths() {
    return paths;
  }

  @Override
  public Location location() {
    return location;
  }
}
