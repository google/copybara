// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
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
import java.util.logging.Level;
import java.util.logging.Logger;

final class ReplaceVisitor extends SimpleFileVisitor<Path> {

  private static final Logger logger = Logger.getLogger(Replace.class.getName());

  private final Pattern before;
  private final String after;
  private final PathMatcher pathMatcher;
  private final boolean firstOnly;
  private final boolean multiline;

  boolean somethingWasChanged;

  ReplaceVisitor(
      Pattern before, String after, PathMatcher pathMatcher, boolean firstOnly, boolean multiline) {
    this.before = Preconditions.checkNotNull(before);
    this.after = Preconditions.checkNotNull(after);
    this.pathMatcher = Preconditions.checkNotNull(pathMatcher);
    this.firstOnly = firstOnly;
    this.multiline = multiline;
  }

  @Override
  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
    if (!Files.isRegularFile(file) || !pathMatcher.matches(file)) {
      return FileVisitResult.CONTINUE;
    }
    logger.log(Level.INFO, String.format("apply s/%s/%s/ to %s", before, after, file));

    String originalFileContent = new String(Files.readAllBytes(file), UTF_8);
    List<String> originalRanges = multiline
        ? ImmutableList.of(originalFileContent)
        : Splitter.on('\n').splitToList(originalFileContent);

    List<String> newRanges = new ArrayList<>(originalRanges.size());
    for (String line : originalRanges) {
      Matcher matcher = before.matcher(line);
      if (firstOnly) {
        newRanges.add(matcher.replaceFirst(after));
      } else {
        newRanges.add(matcher.replaceAll(after));
      }
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
