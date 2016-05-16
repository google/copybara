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
import java.util.regex.Matcher;
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
 * TODO(matvore): Consider making this configurable to non-line-based and multiple matches.
 */
public final class Replace implements Transformation {

  private static final Logger logger = Logger.getLogger(Replace.class.getName());

  private final TemplateTokens before;
  private final TemplateTokens after;
  private final ImmutableMap<String, Pattern> regexGroups;
  private final PathMatcherBuilder fileMatcherBuilder;

  private Replace(TemplateTokens before, TemplateTokens after,
      ImmutableMap<String, Pattern> regexGroups, PathMatcherBuilder fileMatcherBuilder) {
    this.before = Preconditions.checkNotNull(before);
    this.after = Preconditions.checkNotNull(after);
    this.regexGroups = Preconditions.checkNotNull(regexGroups);
    this.fileMatcherBuilder = Preconditions.checkNotNull(fileMatcherBuilder);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("before", before.template())
        .add("after", after.template())
        .add("regexGroup", regexGroups)
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

    /**
     * Transforms a single line which confirming that the current transformation can be applied in
     * reverse to get the original line back.
     */
    private String transformLine(String originalLine) throws NotRoundtrippableException {
      Matcher matcher = beforeRegex.matcher(originalLine);
      String newLine = matcher.replaceAll(after.template());
      matcher = afterRegex.matcher(newLine);
      String roundTrippedLine = matcher.replaceAll(before.template());
      if (!roundTrippedLine.equals(originalLine)) {
        throw new NotRoundtrippableException(String.format(
            "Reverse-transform didn't generate the original text:\n"
                + "    Expected : %s\n"
                + "    Actual   : %s\n",
            originalLine, roundTrippedLine));
      }
      return newLine;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      if (!Files.isRegularFile(file) || !pathMatcher.matches(file)) {
        return FileVisitResult.CONTINUE;
      }
      logger.log(
          Level.INFO, String.format("apply s/%s/%s/ to %s", beforeRegex, after.template(), file));

      List<String> originalLines = ImmutableList.copyOf(
          Splitter.on('\n').split(new String(Files.readAllBytes(file), UTF_8)));
      List<String> newLines = new ArrayList<>(originalLines.size());
      for (String line : originalLines) {
        newLines.add(transformLine(line));
      }
      if (!originalLines.equals(newLines)) {
        somethingWasChanged = true;
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file), UTF_8)) {
          Joiner.on('\n').appendTo(writer, newLines);
        }
      }

      return FileVisitResult.CONTINUE;
    }
  }

  @Override
  public void transform(Path workdir) throws IOException {
    TransformVisitor visitor = new TransformVisitor(fileMatcherBuilder.relativeTo(workdir));
    Files.walkFileTree(workdir, visitor);
    if (!visitor.somethingWasChanged) {
      throw new TransformationDoesNothingException(
          "Transformation '" + toString() + "' was a no-op. It didn't affect the workdir.");
    }
  }

  @Override
  public String describe() {
    // before should be almost always unique so it is good enough for identifying the
    // transform.
    return "Replace " + before.template();
  }

  @DocElement(yamlName = "!Replace", description = "Replace a text with another text using optional regex groups. This tranformer is designed so that it can be reversible (Used in another workflow in the other direction).", elementKind = Transformation.class)
  public final static class Yaml implements Transformation.Yaml {

    private TemplateTokens before;
    private TemplateTokens after;
    private ImmutableMap<String, Pattern> regexGroups = ImmutableMap.of();
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

    @Override
    public Transformation withOptions(Options options) throws ConfigValidationException {
      ConfigValidationException.checkNotMissing(before, "before");
      ConfigValidationException.checkNotMissing(after, "after");

      before.validateInterpolations(regexGroups.keySet());
      after.validateInterpolations(regexGroups.keySet());

      return new Replace(before, after, regexGroups,
          PathMatcherBuilder.create(FileSystems.getDefault(), ImmutableList.of(path)));
    }
  }
}
