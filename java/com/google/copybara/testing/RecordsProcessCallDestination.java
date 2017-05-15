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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.copybara.Destination;
import com.google.copybara.EmptyChangeException;
import com.google.copybara.RepoException;
import com.google.copybara.Revision;
import com.google.copybara.TransformResult;
import com.google.copybara.ValidationException;
import com.google.copybara.authoring.Author;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
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
  public final ListMultimap<String, ProcessedChange> pending = ArrayListMultimap.create();

  public RecordsProcessCallDestination(WriterResult... results) {
    this.programmedResults = new ArrayDeque<>(Arrays.asList(results));
  }

  public boolean failOnEmptyChange = false;

  public class WriterImpl implements Writer {

    final Glob destinationFiles;
    private final boolean dryRun;

    public WriterImpl(Glob destinationFiles, boolean dryRun) {
      this.destinationFiles = destinationFiles;
      this.dryRun = dryRun;
    }

    @Nullable
    @Override
    public DestinationStatus getDestinationStatus(String labelName, @Nullable String groupId)
        throws RepoException {
      if (processed.isEmpty()) {
        return null;
      }
      // Find latest change without group
      String baseline = null;
      for (int i = processed.size() - 1; i >= 0; i--) {
        ProcessedChange processedChange = processed.get(i);
        if (processedChange.groupIdentity == null) {
          baseline = processedChange.getOriginRef().asString();
          break;
        }
      }
      Preconditions.checkNotNull(baseline);
      if (groupId == null || !pending.containsKey(groupId)) {
        return new DestinationStatus(baseline, ImmutableList.of());
      }
      return new DestinationStatus(
          baseline,
          pending.get(groupId)
              .stream()
              .map(processedChange -> processedChange.getOriginRef().asString())
              .collect(ImmutableList.toImmutableList()));
    }

    @Override
    public WriterResult write(TransformResult transformResult, Console console)
        throws ValidationException, RepoException, IOException {
      if (failOnEmptyChange
          && !processed.isEmpty()
          && processed.get(processed.size() - 1).workdir
          .equals(copyWorkdir(transformResult.getPath()))) {
        throw new EmptyChangeException("Change did not produce a result");
      }
      ProcessedChange change =
          new ProcessedChange(transformResult, copyWorkdir(transformResult.getPath()),
                              transformResult.getBaseline(), destinationFiles,
                              dryRun, transformResult.getGroupIdentity());
      processed.add(change);
      if (transformResult.getGroupIdentity() != null) {
        pending.put(transformResult.getGroupIdentity(), change);
      }
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
          result.put(workdir.relativize(file).toString(),
                     new String(Files.readAllBytes(file), UTF_8));
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return result.build();
  }

  @Override
  public Writer newWriter(Glob destinationFiles, boolean dryRun) {
    return new WriterImpl(destinationFiles, dryRun);
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
    private final boolean dryRun;
    private final String groupIdentity;

    private ProcessedChange(TransformResult transformResult, ImmutableMap<String, String> workdir,
        String baseline, Glob destinationFiles, boolean dryRun, String groupIdentity) {
      this.transformResult = Preconditions.checkNotNull(transformResult);
      this.workdir = Preconditions.checkNotNull(workdir);
      this.baseline = baseline;
      this.destinationFiles = destinationFiles;
      this.dryRun = dryRun;
      this.groupIdentity = groupIdentity;
    }

    public ZonedDateTime getTimestamp() {
      return transformResult.getTimestamp();
    }

    public Revision getOriginRef() {
      return transformResult.getCurrentRevision();
    }

    public Author getAuthor() {
      return transformResult.getAuthor();
    }

    public String getChangesSummary() {
      return transformResult.getSummary();
    }

    public Revision getRequestedRevision() {
      return transformResult.getRequestedRevision();
    }

    public int numFiles() {
      return workdir.size();
    }

    public boolean isDryRun() {
      return dryRun;
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
