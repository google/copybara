// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.TransformWork;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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

  private static final Logger logger = Logger.getLogger(Replace.class.getName());

  private final TemplateTokens before;
  private final TemplateTokens after;
  private final ImmutableMap<String, Pattern> regexGroups;
  private final boolean multiline;
  private final PathMatcherBuilder fileMatcherBuilder;
  private final WorkflowOptions workflowOptions;

  private Replace(TemplateTokens before, TemplateTokens after,
      ImmutableMap<String, Pattern> regexGroups, boolean multiline,
      PathMatcherBuilder fileMatcherBuilder,
      WorkflowOptions workflowOptions) {
    this.before = Preconditions.checkNotNull(before);
    this.after = Preconditions.checkNotNull(after);
    this.regexGroups = Preconditions.checkNotNull(regexGroups);
    this.multiline = multiline;
    this.fileMatcherBuilder = Preconditions.checkNotNull(fileMatcherBuilder);
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("before", before.template())
        .add("after", after.template())
        .add("regexGroups", regexGroups)
        .add("multiline", multiline)
        .add("path", fileMatcherBuilder)
        .toString();
  }

  private final class TransformVisitor extends SimpleFileVisitor<Path> {
    final Pattern beforeRegex = before.toRegex(regexGroups);
    final Pattern afterRegex = after.toRegex(regexGroups);
    private final PathMatcher pathMatcher;
    boolean somethingWasChanged;
    TransformVisitor(PathMatcher pathMatcher) {

      this.pathMatcher = pathMatcher;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      if (!Files.isRegularFile(file) || !pathMatcher.matches(file)) {
        return FileVisitResult.CONTINUE;
      }
      logger.log(
          Level.INFO, String.format("apply s/%s/%s/ to %s", beforeRegex, after.template(), file));

      String originalFileContent = new String(Files.readAllBytes(file), UTF_8);
      List<String> originalRanges = multiline
          ? ImmutableList.of(originalFileContent)
          : Splitter.on('\n').splitToList(originalFileContent);

      List<String> newRanges = new ArrayList<>(originalRanges.size());
      for (String line : originalRanges) {
        newRanges.add(beforeRegex.matcher(line).replaceAll(after.template()));
      }
      if (!originalRanges.equals(newRanges)) {
        somethingWasChanged = true;
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file), UTF_8)) {
          Joiner.on('\n').appendTo(writer, newRanges);
        }
      }

      return FileVisitResult.CONTINUE;
    }
  }

  @Override
  public void transform(TransformWork work, Console console)
      throws IOException, ValidationException {
    Path checkoutDir = work.getCheckoutDir();
    TransformVisitor visitor = new TransformVisitor(fileMatcherBuilder.relativeTo(checkoutDir));
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
    return "Replace " + before.template();
  }

  @Override
  public Replace reverse() {
    return new Replace(after, before, regexGroups, multiline, fileMatcherBuilder, workflowOptions);
  }

  public static Replace create(Location location, String before, String after,
      Map<String, String> regexGroups, PathMatcherBuilder paths,
      boolean multiline, WorkflowOptions workflowOptions)
      throws EvalException {
    TemplateTokens beforeTokens;
    // TODO(team): Revisit these ugly try/catchs and see if those functions can throw EvalException
    try {
      beforeTokens = TemplateTokens.parse(before);
    } catch (ConfigValidationException e) {
      throw new EvalException(location, "'before' field:" + e.getMessage());
    }
    TemplateTokens afterTokens;
    try {
      afterTokens = TemplateTokens.parse(after);
    } catch (ConfigValidationException e) {
      throw new EvalException(location, "'after' field:" + e.getMessage());
    }

    ImmutableMap.Builder<String, Pattern> parsed = new ImmutableMap.Builder<>();
    for (Map.Entry<String, String> group : regexGroups.entrySet()) {
      try {
        parsed.put(group.getKey(), Pattern.compile(group.getValue()));
      } catch (PatternSyntaxException e) {
        throw new EvalException(location, "'regex_groups' includes invalid regex for key "
            + group.getKey() + ": " + group.getValue(), e);
      }
    }

    try {
      beforeTokens.validateInterpolations(regexGroups.keySet());
      afterTokens.validateInterpolations(regexGroups.keySet());
    } catch (ConfigValidationException e) {
      throw new EvalException(location, "'regex_groups' field:" + e.getMessage());
    }

    return new Replace(beforeTokens, afterTokens, parsed.build(), multiline, paths,
        workflowOptions);
  }
}
