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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.copybara.Change;
import com.google.copybara.Destination;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.DestinationRef;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.Endpoint;
import com.google.copybara.Revision;
import com.google.copybara.TransformResult;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.DiffUtil.DiffFile;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * A destination for testing which doesn't write the workdir anywhere and simply records when
 * {@link Destination.Writer#write(TransformResult, Console)} is called and with what arguments.
 */
public class RecordsProcessCallDestination implements Destination<Revision> {

  private final ArrayDeque<ImmutableList<String>> programmedErrors;

  public final List<ProcessedChange> processed = new ArrayList<>();

  @Nullable
  private Consumer<TransformResult> hook;

  public RecordsProcessCallDestination() {
    this(ImmutableList.of());
  }
  public RecordsProcessCallDestination(ImmutableList<ImmutableList<String>> errors) {
    this.programmedErrors = new ArrayDeque<>(errors);
  }

  private final DummyEndpoint endpoint = new DummyEndpoint();

  public boolean failOnEmptyChange = false;

  public DummyEndpoint getEndpoint() {
    return endpoint;
  }

  /**
   * Execute this consumer everytime write is called.
   */
  public void onWrite(Consumer<TransformResult> hook) {
    this.hook = hook;
  }

  public class WriterImpl implements Writer<Revision> {

    final Glob destinationFiles;
    private final boolean dryRun;
    @Nullable
    private final String groupId;
    private final int state;


    public WriterImpl(Glob destinationFiles, boolean dryRun) {
      this.destinationFiles = destinationFiles;
      this.dryRun = dryRun;
      this.state = 0;
      this.groupId = null;
    }

    public WriterImpl(Glob destinationFiles, boolean dryRun, @Nullable String groupId,
        @Nullable WriterImpl old) {
      this.destinationFiles = destinationFiles;
      this.dryRun = dryRun;
      this.groupId = groupId;
      this.state = old != null && old.dryRun == dryRun ? old.state + 1 : 0;
    }

    @Nullable
    @Override
    public DestinationStatus getDestinationStatus(String labelName) throws RepoException {
      ProcessedChange lastSubmitted = Lists.reverse(processed).stream()
          .filter(c -> !c.pending)
          .findFirst().orElse(null);

      if (lastSubmitted == null) {
        return null;
      }

      if (groupId == null) {
        return new DestinationStatus(lastSubmitted.getOriginRef().asString(), ImmutableList.of());
      }

      ImmutableList<String> pending = processed.stream()
          .filter(c -> c.pending && groupId.equals(c.groupIdentity))
          .map(c -> c.getOriginRef().asString())
          .collect(ImmutableList.toImmutableList());
      return new DestinationStatus(lastSubmitted.getOriginRef().asString(), pending);
    }

    @Override
    public Endpoint getFeedbackEndPoint(Console console) {
      return endpoint;
    }

    @Override
    public boolean supportsHistory() {
      return true;
    }

    @Override
    public ImmutableList<DestinationEffect> write(TransformResult transformResult, Console console)
        throws ValidationException, RepoException, IOException {
      if (hook != null) {
        hook.accept(transformResult);
      }
      if (failOnEmptyChange
          && !processed.isEmpty()
          && processed.get(processed.size() - 1).workdir
          .equals(copyWorkdir(transformResult.getPath()))) {
        throw new EmptyChangeException("Change did not produce a result");
      }
      ProcessedChange change =
          new ProcessedChange(transformResult, copyWorkdir(transformResult.getPath()),
                              transformResult.getBaseline(), destinationFiles,
                              dryRun, groupId, state);
      processed.add(change);
      return ImmutableList.of(
          new DestinationEffect(
              Type.CREATED,
              "Change created",
              transformResult.getChanges().getCurrent(),
              new DestinationRef("destination/" + processed.size(), "commit", /*url=*/ null),
              programmedErrors.isEmpty() ? ImmutableList.of() : programmedErrors.removeFirst()));
    }

    @Override
    public void visitChangesWithAnyLabel(Revision start, ImmutableCollection<String> labels,
        ChangesLabelVisitor visitor) throws RepoException, ValidationException {

      for (ProcessedChange processedChange : Lists.reverse(processed)) {

        VisitResult result =
            visitor.visit(
                new Change<>(
                    processedChange.getOriginRef(),
                    processedChange.getAuthor(),
                    processedChange.getChangesSummary(),
                    processedChange.getTimestamp(),
                    ImmutableListMultimap.of()),
                ImmutableMap.copyOf(labels
                    .stream()
                    .collect(Collectors.toMap(
                            Function.identity(), e -> processedChange.getOriginRef().asString()))));
        if (result == VisitResult.TERMINATE) {
          return;
        }
      }

    }

    @Override
    public void visitChanges(Revision start, ChangesVisitor visitor)
        throws RepoException, CannotResolveRevisionException {
      for (ProcessedChange processedChange : Lists.reverse(processed)) {
        VisitResult result =
            visitor.visit(
                new Change<>(
                    processedChange.getOriginRef(),
                    processedChange.getAuthor(),
                    processedChange.getChangesSummary(),
                    processedChange.getTimestamp(),
                    ImmutableListMultimap.of()));
        if (result == VisitResult.TERMINATE) {
          return;
        }
      }
    }
  }

  private ImmutableMap<String, String> copyWorkdir(Path workdir) {
    ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
    try {
      Files.walkFileTree(workdir, new SimpleFileVisitor<Path>() {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          if (Files.isRegularFile(file)) {
            result.put(workdir.relativize(file).toString(),
                       new String(Files.readAllBytes(file), UTF_8));
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return result.build();
  }

  @Override
  public Writer<Revision> newWriter(Glob destinationFiles, boolean dryRun,
      @Nullable String groupId, @Nullable Writer<Revision> oldWriter) {
    return new WriterImpl(destinationFiles, dryRun, groupId, (WriterImpl) oldWriter);
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
    private final int state;
    public boolean pending;

    private ProcessedChange(TransformResult transformResult, ImmutableMap<String, String> workdir,
        String baseline, Glob destinationFiles, boolean dryRun, String groupIdentity, int state) {
      this.transformResult = Preconditions.checkNotNull(transformResult);
      this.workdir = Preconditions.checkNotNull(workdir);
      this.baseline = baseline;
      this.destinationFiles = destinationFiles;
      this.dryRun = dryRun;
      this.groupIdentity = groupIdentity;
      this.state = state;
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

    public String getWorkflowName() {
      return transformResult.getWorkflowName();
    }

    public String getChangeIdentity() {
      return transformResult.getChangeIdentity();
    }

    public boolean isSetRevId() {
      return transformResult.isSetRevId();
    }

    @Nullable
    public ImmutableList<DiffFile> getAffectedFilesForSmartPrune() {
      return transformResult.getAffectedFilesForSmartPrune();
    }

    public int numFiles() {
      return workdir.size();
    }

    public boolean isDryRun() {
      return dryRun;
    }

    public String getGroupIdentity() {
      return groupIdentity;
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

    public ImmutableList<? extends Change<?>> getOriginChanges() {
      return transformResult.getChanges().getCurrent().getImmutableList();
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

    /**
     * This is used to simulate multiple writers creation that can maintain state between them.
     */
    public int getState() {
      return state;
    }
  }
}
