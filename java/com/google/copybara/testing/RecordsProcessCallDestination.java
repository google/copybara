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

package com.google.copybara.testing;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Author;
import com.google.copybara.Destination;
import com.google.copybara.EmptyChangeException;
import com.google.copybara.Origin.Reference;
import com.google.copybara.TransformResult;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
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

    final Glob destinationFiles;

    WriterImpl(Glob destinationFiles) {
      this.destinationFiles = destinationFiles;
    }

    @Nullable
    @Override
    public String getPreviousRef(String labelName) {
      return processed.isEmpty()
          ? null
          : processed.get(processed.size() - 1).getOriginRef().asString();
    }

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
          transformResult.getBaseline(), destinationFiles));
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
  public Writer newWriter(Glob destinationFiles) {
    return new WriterImpl(destinationFiles);
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return "Destination-RevId";
  }

  public static class ProcessedChange {

    private final TransformResult transformResult;
    private final ImmutableMap<String, String> workdir;
    private final String baseline;
    private final Glob destinationFiles;

    private ProcessedChange(TransformResult transformResult, ImmutableMap<String, String> workdir,
        String baseline, Glob destinationFiles) {
      this.transformResult = Preconditions.checkNotNull(transformResult);
      this.workdir = Preconditions.checkNotNull(workdir);
      this.baseline = baseline;
      this.destinationFiles = destinationFiles;
    }

    public Instant getTimestamp() {
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

    /**
     * A map from file path to content
     */
    public ImmutableMap<String, String> getWorkdir() {
      return workdir;
    }

    public String getBaseline() {
      return baseline;
    }

    public boolean filePresent(String fileName) {
      return workdir.containsKey(fileName);
    }

    public Glob getDestinationFiles() {
      return destinationFiles;
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
