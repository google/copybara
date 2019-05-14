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
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.RegexTemplateTokens.Replacer;
import com.google.copybara.treestate.TreeState.FileState;
import com.google.copybara.util.Glob;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
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

  private Replace(RegexTemplateTokens before, RegexTemplateTokens after,
      Map<String, Pattern> regexGroups, boolean firstOnly, boolean multiline,
      boolean repeatedGroups,
      Glob paths,
      List<Pattern> patternsToIgnore,
      WorkflowOptions workflowOptions) {
    this.before = checkNotNull(before);
    this.after = checkNotNull(after);
    this.regexGroups = ImmutableMap.copyOf(regexGroups);
    this.firstOnly = firstOnly;
    this.multiline = multiline;
    this.repeatedGroups = repeatedGroups;
    this.paths = checkNotNull(paths);
    this.patternsToIgnore = ImmutableList.copyOf(patternsToIgnore);
    this.workflowOptions = checkNotNull(workflowOptions);
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
        .toString();
  }

  @Override
  public void transform(TransformWork work)
      throws IOException, ValidationException {
    Path checkoutDir = work.getCheckoutDir();

    Iterable<FileState> files = work.getTreeState().find(
        paths.relativeTo(checkoutDir));
    BatchReplace batchReplace = new BatchReplace(this::createReplacer);
    workflowOptions.parallelizer().run(files, batchReplace);
    List<FileState> changed = batchReplace.getChanged();
    boolean matchedFile = batchReplace.isMatchedFile();
    logger.atInfo().log( "Applied %s to %d files. %d changed.",
        this, Iterables.size(files), changed.size());

    work.getTreeState().notifyModify(changed);
    if (changed.isEmpty()) {
      workflowOptions.reportNoop(
          work.getConsole(),
          "Transformation '" + toString() + "' was a no-op because it didn't "
              + (matchedFile ? "change any of the matching files" : "match any file"),
          work.getIgnoreNoop());
    }
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
      throw new NonReversibleValidationException(e.getLocation(),
          "The transformation is not automatically reversible. Add an explicit reversal field with "
              + "core.transform.", e.getCause());
    }
    //TODO remove repeatedGroups boolean?
    return new Replace(after, before, regexGroups, firstOnly, multiline, repeatedGroups,
        paths, patternsToIgnore, workflowOptions);
  }

  public static Replace create(Location location, String before, String after,
      Map<String, String> regexGroups, Glob paths, boolean firstOnly, boolean multiline,
      boolean repeatedGroups, List<String> patternsToIgnore,
      WorkflowOptions workflowOptions)
      throws EvalException {
    Map<String, Pattern> parsedGroups = parsePatterns(location, regexGroups);

    RegexTemplateTokens beforeTokens =
        new RegexTemplateTokens(location, before, parsedGroups, repeatedGroups);
    RegexTemplateTokens afterTokens =
        new RegexTemplateTokens(location, after, parsedGroups, repeatedGroups);

    beforeTokens.validateUnused();

    List<Pattern> parsedIgnorePatterns = new ArrayList<>();
    for (String toIgnore : patternsToIgnore) {
      try {
        parsedIgnorePatterns.add(Pattern.compile(toIgnore));
      } catch (PatternSyntaxException e) {
        throw new EvalException(
            location, "'patterns_to_ignore' includes invalid regex: " + toIgnore, e);
      }
    }

    // Don't validate non-used interpolations in after since they are only relevant for reversable
    // transformations. And those are eagerly validated during config loading, because
    // when asking for the reverse 'after' is used as 'before', and it gets validated
    // with the check above.

    return new Replace(
        beforeTokens, afterTokens, parsedGroups, firstOnly, multiline, repeatedGroups, paths,
        parsedIgnorePatterns, workflowOptions);
  }

  public static Map<String, Pattern> parsePatterns(Location location,
      Map<String, String> regexGroups) throws EvalException {
    Map<String, Pattern> parsedGroups = new HashMap<>();
    for (Entry<String, String> group : regexGroups.entrySet()) {
      try {
        parsedGroups.put(group.getKey(), Pattern.compile(group.getValue()));
      } catch (PatternSyntaxException e) {
        throw new EvalException(location, "'regex_groups' includes invalid regex for key "
            + group.getKey() + ": " + group.getValue(), e);
      }
    }
    return parsedGroups;
  }

  private final static class BatchReplace
      implements LocalParallelizer.TransformFunc<FileState, Boolean> {


    private final Supplier<Replacer> replacerSupplier;

    private final List<FileState> changed = new ArrayList<>();
    private boolean matchedFile = false;

    BatchReplace(Supplier<Replacer> replacerSupplier) {
      this.replacerSupplier = checkNotNull(replacerSupplier);
    }

    public List<FileState> getChanged() {
      return changed;
    }

    boolean isMatchedFile() {
      return matchedFile;
    }

    @Override
    public Boolean run(Iterable<FileState> elements) throws IOException {
      Replacer replacer = replacerSupplier.get();
      List<FileState> changed = new ArrayList<>();
      boolean matchedFile = false;
      for (FileState file : elements) {
        if (Files.isSymbolicLink(file.getPath())) {
          continue;
        }
        matchedFile = true;
        String originalFileContent = new String(Files.readAllBytes(file.getPath()), UTF_8);
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
}
