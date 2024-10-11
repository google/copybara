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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.copybara.CheckoutPath;
import com.google.copybara.Destination;
import com.google.copybara.DestinationInfo;
import com.google.copybara.DestinationReader;
import com.google.copybara.Endpoint;
import com.google.copybara.Origin.Baseline;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.authoring.Author;
import com.google.copybara.effect.DestinationEffect;
import com.google.copybara.effect.DestinationEffect.DestinationRef;
import com.google.copybara.effect.DestinationEffect.Type;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.revision.Change;
import com.google.copybara.revision.Revision;
import com.google.copybara.util.DiffUtil.DiffFile;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/**
 * A destination for testing which doesn't write the workdir anywhere and simply records when
 * {@link Destination.Writer#write(TransformResult, Glob, Console)} is called and with what
 * arguments.
 */
public class RecordsProcessCallDestination implements Destination<Revision> {

  private final ArrayDeque<ImmutableList<String>> programmedErrors;

  public final List<ProcessedChange> processed = new ArrayList<>();

  @Nullable
  private Consumer<TransformResult> hook;
  @Nullable Path filePrefix;

  public RecordsProcessCallDestination() {
    this(ImmutableList.of());
  }
  public RecordsProcessCallDestination(ImmutableList<ImmutableList<String>> errors) {
    this.programmedErrors = new ArrayDeque<>(errors);
  }

  /**
   * Support reading files from the DestinationReader not uploaded using this destination.
   * In the event of a conflict, files managed by ProcessedChange objects take priority.
   */
  public static RecordsProcessCallDestination withFilePrefix(Path filePrefix) {
    RecordsProcessCallDestination toReturn = new RecordsProcessCallDestination();
    toReturn.filePrefix = filePrefix;
    return toReturn;
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

    @Nullable
    private final String contextReference;
    private final boolean dryRun;

    protected WriterImpl(boolean dryRun) {
      this.dryRun = dryRun;
      this.contextReference = null;
    }

    protected WriterImpl(boolean dryRun, @Nullable String contextReference) {
      this.dryRun = dryRun;
      this.contextReference = contextReference;
    }

    @Nullable
    @Override
    public DestinationStatus getDestinationStatus(Glob destinationFiles, String labelName) {
      ProcessedChange lastSubmitted = Lists.reverse(processed).stream()
          .filter(c -> !c.pending)
          .findFirst().orElse(null);

      if (lastSubmitted == null) {
        return null;
      }

      if (contextReference == null) {
        return new DestinationStatus(lastSubmitted.getOriginRef().asString(), ImmutableList.of());
      }

      ImmutableList<String> pending =
          processed
              .stream()
              .filter(c -> c.pending &&
                  contextReference.equals(c.getOriginRef().contextReference()))
              .map(c -> c.getOriginRef().asString())
              .collect(ImmutableList.toImmutableList());
      return new DestinationStatus(lastSubmitted.getOriginRef().asString(), pending);
    }

    @Override
    public Endpoint getFeedbackEndPoint(Console console) {
      return endpoint;
    }

    /** Read from the latest processed change. */
    public class DestinationReaderImpl extends DestinationReader {
      Path workdir;
      @Nullable Integer baseline;

      public DestinationReaderImpl(Path workdir, @Nullable String baseline) {
        this.workdir = workdir;
        if (baseline != null && !baseline.equals("HEAD")) {
          this.baseline = Integer.parseInt(baseline);
        }
      }

      private Optional<ProcessedChange> getProcessed() {
        if (baseline != null && baseline >= 0 && baseline < processed.size()) {
          return Optional.of(processed.get(baseline));
        }
        if (processed.size() > 0) {
          return Optional.of(Iterables.getLast(processed));
        }
        return Optional.empty();
      }

      @Override
      public String readFile(String path) throws RepoException {
        Optional<ProcessedChange> processedChange = getProcessed();
        if (processedChange.isPresent() && processedChange.get().getWorkdir().containsKey(path)) {
          return processedChange.get().getWorkdir().get(path);
        }

        if (filePrefix != null && Files.exists(filePrefix.resolve(path))) {
          try {
            String relativePath = path.startsWith("/") ? path.substring(1) : path;
            return Files.readString(filePrefix.resolve(relativePath));
          } catch (IOException e) {
            throw new RepoException("failed to read file", e);
          }
        }

        throw new RepoException(String.format("Could not read file at path %s", path));
      }

      private void writeFile(Path path, String contents) throws RepoException {
        try {
          Files.createDirectories(path.getParent());
          Files.writeString(path, contents);
        } catch (IOException e) {
          throw new RepoException("Failed to write file", e);
        }
      }

      @Override
      public void copyDestinationFiles(Object globObj, Object path)
          throws RepoException, EvalException {
        CheckoutPath checkoutPath = convertFromNoneable(path, null);
        Glob glob = Glob.wrapGlob(globObj, null);
        if (filePrefix != null) {
          PathMatcher filePrefixMatcher = glob.relativeTo(filePrefix);
          try (Stream<Path> prefixFiles = Files.walk(filePrefix)) {
            for (Path file : prefixFiles
                .filter(Files::isRegularFile)
                .filter(filePrefixMatcher::matches)
                .collect(toImmutableList())) {
              String contents = Files.readString(file);
              writeFile(checkoutPath.getCheckoutDir().resolve(filePrefix.relativize(file)),
                  contents);
            }
          } catch (IOException e) {
            throw new RepoException("failed to copy files from filePrefix directory", e);
          }
        }
        if (processed.isEmpty()) {
          return;
        }
        Optional<ProcessedChange> processedChange = getProcessed();
        if (processedChange.isEmpty()) {
          return; // nothing to copy
        }

        PathMatcher absoluteMatcher = glob.relativeTo(Paths.get(""));
        PathMatcher relativeMatcher = glob.relativeTo(checkoutPath.getCheckoutDir());
        for (Entry<String, String> e : processedChange.get().getWorkdir().entrySet()) {
          Path p = Paths.get(e.getKey());
          if (p.toString().startsWith("/") && absoluteMatcher.matches(p)) {
            writeFile(checkoutPath.getCheckoutDir().resolve(Paths.get("/").relativize(p)),
                e.getValue());
          } else if (relativeMatcher.matches(checkoutPath.getCheckoutDir().resolve(p))) {
            writeFile(checkoutPath.getCheckoutDir().resolve(p), e.getValue());
          }
        }
      }

      @Override
      public void copyDestinationFilesToDirectory(Glob glob, Path directory)
          throws RepoException {
        if (filePrefix != null) {
          PathMatcher filePrefixMatcher = glob.relativeTo(filePrefix);
          try (Stream<Path> prefixFiles = Files.walk(filePrefix)) {
            for (Path file : prefixFiles
                .filter(Files::isRegularFile)
                .filter(filePrefixMatcher::matches)
                .collect(toImmutableList())) {
              String contents = Files.readString(file);
              writeFile(directory.resolve(filePrefix.relativize(file)), contents);
            }
          } catch (IOException e) {
            throw new RepoException("failed to copy files from filePrefix directory", e);
          }
        }
        if (processed.isEmpty()) {
          return;
        }
        Optional<ProcessedChange> processedChange = getProcessed();
        if (processedChange.isEmpty()) {
          return; //nothing to copy
        }
        PathMatcher absoluteMatcher = glob.relativeTo(Paths.get(""));
        PathMatcher relativeMatcher = glob.relativeTo(directory);
        for (Entry<String, String> e : processedChange.get().getWorkdir().entrySet()) {
          Path p = Paths.get(e.getKey());
          if (p.toString().startsWith("/") && absoluteMatcher.matches(p)) {
            writeFile(directory.resolve(Paths.get("/").relativize(p)), e.getValue());
          } else if (relativeMatcher.matches(directory.resolve(p))) {
            writeFile(directory.resolve(p), e.getValue());
          }
        }
      }

      @Override
      public boolean exists(String path) {
        if (filePrefix != null && Files.exists(filePrefix.resolve(path))) {
          return true;
        }
        Optional<ProcessedChange> processedChange = getProcessed();
        return processedChange.map(change -> change.getWorkdir().containsKey(path)).orElse(false);
      }

      @Override
      public String lastModified(String path) {
        for (int i = processed.size() - 1; i > 0; i--) {
          ImmutableMap<String, String> currentWorkdir = processed.get(i).getWorkdir();
          ImmutableMap<String, String> previousWorkdir = processed.get(i - 1).getWorkdir();
          if (!Objects.equals(currentWorkdir.get(path), previousWorkdir.get(path))) {
            return String.valueOf(i);
          }
        }
        return String.valueOf(0);
      }

      @Override
      public String getHash(String path) throws RepoException {
        Optional<ProcessedChange> processedChange = getProcessed();
        if (processedChange.isEmpty()) {
          throw new RepoException("attempted to hash empty destination");
        }
        String contents = processedChange.get().getWorkdir().get(path);
        if (contents == null) {
          throw new RepoException("attempted to hash missing file");
        }
        return Hashing.sha256().hashString(contents, UTF_8).toString();
      }
    }

    @Override
    public DestinationReader getDestinationReader(
        Console console, @Nullable Baseline<?> baseline, Path workdir)
        throws ValidationException, RepoException {
      String rawBaseline = baseline != null ? baseline.getBaseline() : null;
        return new DestinationReaderImpl(workdir, rawBaseline);
    }

    @Override
    public DestinationReader getDestinationReader(
        Console console, @Nullable String baseline, Path workdir)
        throws ValidationException, RepoException {
      return new DestinationReaderImpl(workdir, baseline);
    }

    @Override
    public DestinationInfo getDestinationInfo() {
      return getNewDestinationInfo();
    }

    @Override
    public boolean supportsHistory() {
      return true;
    }

    @Override
    public ImmutableList<DestinationEffect> write(
        TransformResult transformResult, Glob destinationFiles, Console console)
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
          new ProcessedChange(
              transformResult,
              copyWorkdir(transformResult.getPath()),
              transformResult.getBaseline(),
              destinationFiles,
              dryRun);
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
                labels.stream()
                    .collect(
                        toImmutableMap(
                            Function.identity(), e -> processedChange.getOriginRef().asString())));
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
      Files.walkFileTree(
          workdir,
          new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              if (Files.isRegularFile(file)) {
                result.put(workdir.relativize(file).toString(), Files.readString(file));
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return result.buildOrThrow();
  }

  @Override
  public Writer<Revision> newWriter(WriterContext writerContext) {
    return new WriterImpl(
        writerContext.isDryRun(),
        writerContext.getOriginalRevision().contextReference());
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return "Destination-RevId";
  }

  /**
   * Class that implements a simple version of DestinationInfo for testing purposes.
   */
  @StarlarkBuiltin(
      name = "testing.destination_info",
      doc = "DestinationInfo object used for testing. Can store a simple string to string mapping.",
      documented = false)
  public static class DestinationInfoImpl implements DestinationInfo, StarlarkValue {
    private final ImmutableMultimap.Builder<String, String> values;

    public DestinationInfoImpl() {
      values = ImmutableMultimap.builder();
    }

    @StarlarkMethod(
        name = "add_value",
        doc = "Adds a key-value pair.",
        parameters = {
          @Param(
              name = "key",
              doc = "Key that will map to the value",
              allowedTypes = {@ParamType(type = String.class)}),
          @Param(
              name = "value",
              doc = "The string value itself",
              allowedTypes = {@ParamType(type = String.class)})
        })
    public void addValue(String key, String value) {
      values.put(key, value);
    }

    @StarlarkMethod(
        name = "get_values",
        doc = "Gets values that map to a given key.",
        parameters = {
          @Param(
              name = "key",
              doc = "Issue ID to modify",
              allowedTypes = {@ParamType(type = String.class)})
        })
    public StarlarkList<String> getValues(String key) {
      return StarlarkList.immutableCopyOf(values.build().get(key));
    }
  }

  /**
   * Returns a new DestinationInfo object that this destination's Writer will use.
   *
   * <p>This function can be overridden to use other DestinationInfo objects for testing purposes.
   * @return the new DestinationInfoImpl object.
   */
  public DestinationInfo getNewDestinationInfo() {
    return new DestinationInfoImpl();
  }

  public static class ProcessedChange {

    private final TransformResult transformResult;
    private final ImmutableMap<String, String> workdir;
    private final String baseline;
    private final Glob destinationFiles;
    private final boolean dryRun;
    public boolean pending;

    public ProcessedChange(
        TransformResult transformResult,
        ImmutableMap<String, String> workdir,
        String baseline,
        Glob destinationFiles,
        boolean dryRun) {
      this.transformResult = Preconditions.checkNotNull(transformResult);
      this.workdir = Preconditions.checkNotNull(workdir);
      this.baseline = baseline;
      this.destinationFiles = destinationFiles;
      this.dryRun = dryRun;
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

    public String getRevIdLabel() {
      return transformResult.getRevIdLabel();
    }

    public DestinationInfo getDestinationInfo() {
      return transformResult.getDestinationInfo();
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
  }
}
