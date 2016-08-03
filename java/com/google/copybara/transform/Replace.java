// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.FileSystems;
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
  private final TransformOptions transformOptions;

  private Replace(TemplateTokens before, TemplateTokens after,
      ImmutableMap<String, Pattern> regexGroups, boolean multiline,
      PathMatcherBuilder fileMatcherBuilder,
      TransformOptions transformOptions) {
    this.before = Preconditions.checkNotNull(before);
    this.after = Preconditions.checkNotNull(after);
    this.regexGroups = Preconditions.checkNotNull(regexGroups);
    this.multiline = multiline;
    this.fileMatcherBuilder = Preconditions.checkNotNull(fileMatcherBuilder);
    this.transformOptions = Preconditions.checkNotNull(transformOptions);
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
    ValidationException error = null;
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
  public void transform(Path workdir, Console console) throws IOException, ValidationException {
    TransformVisitor visitor = new TransformVisitor(fileMatcherBuilder.relativeTo(workdir));
    Files.walkFileTree(workdir, visitor);
    if (visitor.error != null) {
      throw visitor.error;
    } else if (!visitor.somethingWasChanged) {
      transformOptions.reportNoop(
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
    return new Replace(after, before, regexGroups, multiline, fileMatcherBuilder, transformOptions);
  }

  @DocElement(yamlName = "!Replace", description = "Replace a text with another text using optional regex groups. This tranformer can be automatically reversed with !Reverse.", elementKind = Transformation.class)
  public final static class Yaml implements Transformation.Yaml {

    private TemplateTokens before;
    private TemplateTokens after;
    private ImmutableMap<String, Pattern> regexGroups = ImmutableMap.of();
    private boolean multiline;
    private String path = "**";

    @DocField(description = "The text before the transformation. Can contain references to regex groups. For example \"foo${x}text\"")
    public void setBefore(String before) throws ConfigValidationException {
      this.before = TemplateTokens.parse(before);
    }

    @DocField(description = "The text after the transformation. Should contain the same references to regex groups than the before field. For example \"bar${x}text\"")
    public void setAfter(String after) throws ConfigValidationException {
      this.after = TemplateTokens.parse(after);
    }

    @DocField(description = "A set of named regexes that can be used to match part of the replaced text. For example x: \"[A-Za-z]+\"", required = false)
    public void setRegexGroups(Map<String, String> regexGroups) throws ConfigValidationException {
      ImmutableMap.Builder<String, Pattern> parsed = new ImmutableMap.Builder<>();
      for (Map.Entry<String, String> group : regexGroups.entrySet()) {
        try {
          parsed.put(group.getKey(), Pattern.compile(group.getValue()));
        } catch (PatternSyntaxException e) {
          throw new ConfigValidationException("'regexGroups' includes invalid regex for key "
              + group.getKey() + ": " + group.getValue(), e);
        }
      }

      this.regexGroups = parsed.build();
    }

    @DocField(description = "A glob expression relative to the workdir representing the files to apply the transformation. For example \"**.java\", all java files recursively",
        required = false, defaultValue = "** (All the files)")
    public void setPath(String path) {
      this.path = path;
    }

    /**
     * Sets multiline behavior. Turning this off causes lines to be split before processing them.
     *
     * <p>Splitting the lines by default is slightly less flexible, but it is more performant,
     * because complement group expressions like {@code [^xyz]} will not include {@code \n}, which
     * will limit the amount of backtracking that occurs.
     */
    @DocField(description = "Whether to replace text that spans more than one line.",
        required = false, defaultValue = "false")
    public void setMultiline(boolean multiline) {
      this.multiline = multiline;
    }

    @Override
    public Replace withOptions(Options options) throws ConfigValidationException {
      ConfigValidationException.checkNotMissing(before, "before");
      ConfigValidationException.checkNotMissing(after, "after");

      before.validateInterpolations(regexGroups.keySet());
      after.validateInterpolations(regexGroups.keySet());

      return new Replace(before, after, regexGroups, multiline,
          PathMatcherBuilder.create(
              FileSystems.getDefault(), ImmutableList.of(path), ImmutableList.<String>of()),
          options.get(TransformOptions.class));
    }

    @Override
    public void checkReversible() throws ConfigValidationException {

    }
  }

  public static Replace create(Location location, String before, String after,
      Map<String, String> regexGroups, PathMatcherBuilder paths,
      boolean multiline, TransformOptions transformOptions)
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
        transformOptions);
  }
}
