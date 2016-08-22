// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Author;
import com.google.copybara.Destination;
import com.google.copybara.EmptyChangeException;
import com.google.copybara.Origin.Reference;
import com.google.copybara.TransformResult;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A destination for testing which doesn't write the workdir anywhere and simply records when
 * {@link Destination.Writer#write(TransformResult, Console)} is called and with what arguments.
 */
public class RecordsProcessCallDestination implements Destination {

  private final ArrayDeque<WriterResult> programmedResults;

  public final List<ProcessedChange> processed = new ArrayList<>();

  public RecordsProcessCallDestination(WriterResult... results) {
    this.programmedResults = new ArrayDeque<>(Arrays.asList(results));
  }

  public boolean failOnEmptyChange = false;

  private class WriterImpl implements Writer {
    @Override
    public WriterResult write(TransformResult transformResult, Console console)
        throws EmptyChangeException {
      if (failOnEmptyChange
          && !processed.isEmpty()
          && processed.get(processed.size() - 1).workdir
          .equals(copyWorkdir(transformResult.getPath()))) {
        throw new EmptyChangeException("Change did not produce a result");
      }
      processed.add(new ProcessedChange(transformResult, copyWorkdir(transformResult.getPath()),
          transformResult.getBaseline()));
      return programmedResults.isEmpty()
          ? WriterResult.OK
          : programmedResults.removeFirst();
    }
  }

  private ImmutableMap<String, String> copyWorkdir(final Path workdir) {
    final ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
    try {
      Files.walkFileTree(workdir, new SimpleFileVisitor<Path>() {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          result.put(workdir.relativize(file).toString(), new String(Files.readAllBytes(file)));
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return result.build();
  }

  @Override
  public Writer newWriter() {
    return new WriterImpl();
  }

  @Nullable
  @Override
  public String getPreviousRef(String labelName) {
    return processed.isEmpty()
        ? null
        : processed.get(processed.size() - 1).getOriginRef().asString();
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return "Destination-RevId";
  }

  public static class ProcessedChange {

    private final TransformResult transformResult;
    private final ImmutableMap<String, String> workdir;
    private final String baseline;

    private ProcessedChange(TransformResult transformResult, ImmutableMap<String, String> workdir,
        String baseline) {
      this.transformResult = Preconditions.checkNotNull(transformResult);
      this.workdir = Preconditions.checkNotNull(workdir);
      this.baseline = baseline;
    }

    public long getTimestamp() {
      return transformResult.getTimestamp();
    }

    public Reference getOriginRef() {
      return transformResult.getOriginRef();
    }

    public Author getAuthor() {
      return transformResult.getAuthor();
    }

    public String getChangesSummary() {
      return transformResult.getSummary();
    }

    public int numFiles() {
      return workdir.size();
    }

    public String getContent(String fileName) {
      return Preconditions.checkNotNull(
          workdir.get(fileName), "Cannot find content for %s", fileName);
    }

    public String getBaseline() {
      return baseline;
    }

    public boolean filePresent(String fileName) {
      return workdir.containsKey(fileName);
    }

    public PathMatcherBuilder getExcludedDestinationPaths() {
      return transformResult.getExcludedDestinationPaths();
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("timestamp", getTimestamp())
          .add("originRef", getOriginRef())
          .add("changesSummary", getChangesSummary())
          .add("workdir", workdir)
          .toString();
    }
  }
}
