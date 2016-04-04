// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.util.FileUtil;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A source code transformation which replaces a regular expression with some other string.
 */
public final class ReplaceRegex implements Transformation {

  private static final Logger logger = Logger.getLogger(ReplaceRegex.class.getName());

  private final Pattern regex;
  private final String replacement;
  private final PathMatcher fileMatcher;

  private ReplaceRegex(PathMatcher fileMatcher, Pattern regex, String replacement) {
    this.fileMatcher = fileMatcher;
    this.regex = Preconditions.checkNotNull(regex);
    this.replacement = Preconditions.checkNotNull(replacement);
  }

  @Override
  public String toString() {
    return String.format("ReplaceRegex{regex: %s, replacement: %s}", regex, replacement);
  }

  @Override
  public void transform(Path workdir) throws IOException {

    Files.walkFileTree(workdir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (Files.isRegularFile(file) && fileMatcher.matches(file)) {
          logger.log(Level.INFO, String.format("apply s/%s/%s/ to %s", regex, replacement, file));
          String original = new String(Files.readAllBytes(file), UTF_8);
          Matcher matcher = regex.matcher(original);
          String replacement = matcher.replaceAll(ReplaceRegex.this.replacement);
          if (!original.equals(replacement)) {
            Files.write(file, replacement.getBytes());
          }
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public final static class Yaml implements Transformation.Yaml {

    private String path;
    private String regex;
    private String replacement;

    public void setPath(String path) {
      this.path = path;
    }

    public void setRegex(String regex) {
      this.regex = regex;
    }

    public void setReplacement(String replacement) {
      this.replacement = replacement;
    }

    @Override
    public Transformation withOptions(Options options) {
      Pattern compiledRegex;
      try {
        compiledRegex = Pattern.compile(
            ConfigValidationException.checkNotMissing(regex, "regex"));
      } catch (PatternSyntaxException e) {
        throw new ConfigValidationException("'regex' field is not a valid regex: " + regex, e);
      }

      PathMatcher pathMatcher = FileUtil.ALL_FILES_MATCHER;
      if (path != null) {
        Path workdir = options.getOption(GeneralOptions.class).getWorkdir();
        pathMatcher = workdir.getFileSystem().getPathMatcher("glob:" + workdir.resolve(path));
      }

      return new ReplaceRegex(pathMatcher,
          compiledRegex, ConfigValidationException.checkNotMissing(replacement, "replacement"));
    }
  }
}
