// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.NonReversibleValidationException;
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
import java.util.HashMap;
import java.util.Map;

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
 * TODO(matvore): Consider making this configurable to replace multiple matches.
 */
public final class Replace implements Transformation {

  private final TemplateTokens before;
  private final TemplateTokens after;
  private final ImmutableMap<String, Pattern> regexGroups;
  private final boolean firstOnly;
  private final boolean multiline;
  private final Glob fileMatcherBuilder;
  private final WorkflowOptions workflowOptions;

  private Replace(TemplateTokens before, TemplateTokens after,
      Map<String, Pattern> regexGroups, boolean firstOnly, boolean multiline,
      Glob fileMatcherBuilder,
      WorkflowOptions workflowOptions) {
    this.before = Preconditions.checkNotNull(before);
    this.after = Preconditions.checkNotNull(after);
    this.regexGroups = ImmutableMap.copyOf(regexGroups);
    this.firstOnly = firstOnly;
    this.multiline = multiline;
    this.fileMatcherBuilder = Preconditions.checkNotNull(fileMatcherBuilder);
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("before", before)
        .add("after", after)
        .add("regexGroups", regexGroups)
        .add("firstOnly", firstOnly)
        .add("multiline", multiline)
        .add("path", fileMatcherBuilder)
        .toString();
  }

  @Override
  public void transform(TransformWork work, Console console)
      throws IOException, ValidationException {
    Path checkoutDir = work.getCheckoutDir();
    ReplaceVisitor visitor = new ReplaceVisitor(
        before.getBefore(), after.after(before),
        fileMatcherBuilder.relativeTo(checkoutDir),
        firstOnly, multiline);
    Files.walkFileTree(checkoutDir, visitor);
    if (!visitor.somethingWasChanged) {
      workflowOptions.reportNoop(
          console,
          "Transformation '" + toString() + "' was a no-op. It didn't affect the workdir.");
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
      throw new NonReversibleValidationException(e.getLocation(), e.getMessage());
    }
    return new Replace(
        after, before, regexGroups, firstOnly, multiline, fileMatcherBuilder, workflowOptions);
  }

  public static Replace create(Location location, String before, String after,
      Map<String, String> regexGroups, Glob paths,
      boolean firstOnly, boolean multiline, WorkflowOptions workflowOptions)
      throws EvalException {
    Map<String, Pattern> parsed = new HashMap<>();
    for (Map.Entry<String, String> group : regexGroups.entrySet()) {
      try {
        parsed.put(group.getKey(), Pattern.compile(group.getValue()));
      } catch (PatternSyntaxException e) {
        throw new EvalException(location, "'regex_groups' includes invalid regex for key "
            + group.getKey() + ": " + group.getValue(), e);
      }
    }

    TemplateTokens beforeTokens = new TemplateTokens(location, before, parsed);
    TemplateTokens afterTokens = new TemplateTokens(location, after, parsed);

    beforeTokens.validateUnused();

    // Don't validate non-used interpolations in after since they are only relevant for reversable
    // transformations. And those are eagerly validated during config loading, because
    // when asking for the reverse 'after' is used as 'before', and it gets validated
    // with the check above.

    return new Replace(
        beforeTokens, afterTokens, parsed, firstOnly, multiline, paths, workflowOptions);
  }
}
