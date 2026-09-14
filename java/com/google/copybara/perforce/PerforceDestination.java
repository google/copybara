/*
 * Copyright (C) 2026 Google Inc.
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

package com.google.copybara.perforce;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Destination;
import com.google.copybara.DestinationReader;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.checks.Checker;
import com.google.copybara.checks.DescriptionChecker;
import com.google.copybara.credentials.CredentialModule.UsernamePasswordIssuer;
import com.google.copybara.effect.DestinationEffect;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** A Perforce (Helix Core) destination that submits each migrated change as a changelist. */
public class PerforceDestination implements Destination<PerforceRevision> {

  private static final String ORIGIN_LABEL_SEPARATOR = ": ";

  private final GeneralOptions generalOptions;
  private final PerforceOptions perforceOptions;
  private final String stream;
  private final boolean submitAsAuthor;
  @Nullable private final UsernamePasswordIssuer credentials;
  @Nullable private final Checker checker;

  private PerforceDestination(
      GeneralOptions generalOptions,
      PerforceOptions perforceOptions,
      String stream,
      boolean submitAsAuthor,
      @Nullable UsernamePasswordIssuer credentials,
      @Nullable Checker checker) {
    this.generalOptions = checkNotNull(generalOptions);
    this.perforceOptions = checkNotNull(perforceOptions);
    this.stream = CharMatcher.is('/').trimTrailingFrom(checkNotNull(stream));
    this.submitAsAuthor = submitAsAuthor;
    this.credentials = credentials;
    this.checker = checker;
  }

  private PerforceServer server() throws RepoException, ValidationException {
    return perforceOptions.server(credentials);
  }

  @Override
  public Writer<PerforceRevision> newWriter(WriterContext writerContext) {
    return new WriterImpl(writerContext.getWorkflowName(), writerContext.isDryRun());
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return PerforceOrigin.PERFORCE_ORIGIN_REV_ID;
  }

  @Override
  public String getType() {
    return "perforce.destination";
  }

  @Override
  public com.google.common.collect.ImmutableSetMultimap<String, String> describe(Glob originFiles) {
    return new com.google.common.collect.ImmutableSetMultimap.Builder<String, String>()
        .put("type", getType())
        .put("stream", stream)
        .build();
  }

  class WriterImpl implements Writer<PerforceRevision> {

    private final String workflowName;
    private final boolean dryRun;
    // Lazily created on first write: a stream client and its on-disk workspace under the cache.
    private String clientName;
    private Path workspaceRoot;

    WriterImpl(String workflowName, boolean dryRun) {
      this.workflowName = checkNotNull(workflowName);
      this.dryRun = dryRun;
    }

    @Override
    public DestinationStatus getDestinationStatus(Glob destinationFiles, String labelName)
        throws RepoException, ValidationException {
      String baseline = server().findOriginLabel(stream, labelName);
      return baseline == null ? null : new DestinationStatus(baseline, ImmutableList.of());
    }

    @Override
    public boolean supportsHistory() {
      return true;
    }

    @Override
    public ImmutableList<DestinationEffect> write(
        TransformResult transformResult, Glob destinationFiles, Console console)
        throws ValidationException, RepoException, IOException {
      ensureWorkspace();
      PerforceServer server = server();

      // cleanWorkspace() reverts any files left open by a prior aborted run, so this attempt starts
      // from a clean, head-aligned state.
      console.progress("Perforce Destination: preparing workspace");
      server.cleanWorkspace(clientName, stream);

      console.progress("Perforce Destination: staging transformed files");
      mirror(transformResult.getPath(), workspaceRoot, destinationFiles);

      // Scan the staged tree and (if supported) the changelist description before submitting.
      String description =
          applyChecker(
              checker, transformResult.getPath(), changeDescription(transformResult), console);

      if (dryRun) {
        // Compute the diff but never create or submit a changelist.
        int changed = server.previewReconcile(clientName, stream);
        if (changed == 0) {
          throw new EmptyChangeException(
              "No changes to submit: the destination already matches the transformed tree");
        }
        String summary =
            String.format("(dry run) would submit %d file(s) to %s; not submitted", changed, stream);
        console.info("Perforce Destination: " + summary);
        return ImmutableList.of(
            new DestinationEffect(
                DestinationEffect.Type.CREATED,
                summary,
                transformResult.getChanges().getCurrent(),
                new DestinationEffect.DestinationRef("(dry run)", "changelist", /* url= */ null)));
      }

      // We do NOT retry a failed submit: reconcileAndSubmit() verifies the submit landed and
      // otherwise throws with the real Perforce error, failing loud rather than silently drifting.
      // A failed run is safe to re-run.
      console.progress("Perforce Destination: submitting changelist");
      int changelist =
          server.reconcileAndSubmit(
              clientName, stream, description, transformResult.getAuthor(), submitAsAuthor);
      console.info(String.format("Perforce Destination: submitted changelist %d", changelist));
      return ImmutableList.of(
          new DestinationEffect(
              DestinationEffect.Type.CREATED,
              String.format("Submitted changelist %d", changelist),
              transformResult.getChanges().getCurrent(),
              new DestinationEffect.DestinationRef(
                  Integer.toString(changelist), "changelist", /* url= */ null)));
    }

    @Override
    public void visitChanges(PerforceRevision start, ChangesVisitor visitor) {
      throw new UnsupportedOperationException("History traversal of a Perforce destination is not"
          + " supported");
    }

    @Override
    public DestinationReader getDestinationReader(
        Console console, @Nullable Origin.Baseline<?> baseline, Path workdir)
        throws ValidationException, RepoException {
      return new PerforceDestinationReader(server(), stream, workdir);
    }

    @Override
    public DestinationReader getDestinationReader(
        Console console, @Nullable String baseline, Path workdir)
        throws ValidationException, RepoException {
      return new PerforceDestinationReader(server(), stream, workdir);
    }

    private void ensureWorkspace() throws RepoException, ValidationException {
      if (clientName != null) {
        return;
      }
      // Stable per (workflow, stream) so the workspace and its have-list survive across runs,
      // avoiding a full re-sync each time.
      String suffix = Integer.toHexString((stream + "\0" + workflowName).hashCode());
      clientName = "copybara_" + sanitize(workflowName) + "_" + suffix;
      try {
        workspaceRoot = generalOptions.getDirFactory().getCacheDir("perforce_ws").resolve(clientName);
        Files.createDirectories(workspaceRoot);
      } catch (IOException e) {
        throw new RepoException("Could not create Perforce workspace directory", e);
      }
      server().ensureClient(clientName, workspaceRoot, stream);
    }
  }

  /**
   * Makes the managed (glob-matched) files in {@code to} exactly mirror those in {@code from}:
   * existing managed files are removed first, then the desired set is copied in. Whatever the depot
   * still has but {@code from} no longer contains is thereby left missing on disk, which `p4
   * reconcile -d` turns into a delete. Files outside the glob are never touched.
   */
  private static void mirror(Path from, Path to, Glob destinationFiles) throws RepoException {
    PathMatcher fromMatcher = destinationFiles.relativeTo(from);
    PathMatcher toMatcher = destinationFiles.relativeTo(to);
    try {
      if (Files.exists(to)) {
        try (Stream<Path> walk = Files.walk(to)) {
          for (Path file : (Iterable<Path>) walk.filter(PerforceDestination::isManaged)
              .filter(toMatcher::matches)::iterator) {
            Files.delete(file);
          }
        }
      }
      try (Stream<Path> walk = Files.walk(from)) {
        for (Path file : (Iterable<Path>) walk.filter(PerforceDestination::isManaged)
            .filter(fromMatcher::matches)::iterator) {
          Path dest = to.resolve(from.relativize(file));
          Files.createDirectories(dest.getParent());
          if (Files.isSymbolicLink(file)) {
            // Recreate the link itself (not its target) so p4 reconcile stores it as a symlink.
            Files.deleteIfExists(dest);
            Files.createSymbolicLink(dest, Files.readSymbolicLink(file));
          } else {
            Files.copy(
                file,
                dest,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
          }
        }
      }
    } catch (IOException e) {
      throw new RepoException("Error staging files into the Perforce workspace", e);
    }
  }

  /** The content kinds Perforce tracks: regular files and symlinks (directories are implicit). */
  private static boolean isManaged(Path path) {
    return Files.isSymbolicLink(path) || Files.isRegularFile(path);
  }

  /**
   * Runs {@code checker} (if any) over the staged tree and, when it is a {@link DescriptionChecker},
   * over the changelist description, returning the possibly-rewritten description.
   */
  static String applyChecker(
      @Nullable Checker checker, Path tree, String description, Console console)
      throws ValidationException, IOException {
    if (checker == null) {
      return description;
    }
    console.progress("Perforce Destination: running checker");
    checker.doCheck(tree, console);
    if (checker instanceof DescriptionChecker) {
      return ((DescriptionChecker) checker).processDescription(description, console);
    }
    return description;
  }

  /** Builds the changelist description, stamping the origin revision label for incremental syncs. */
  private static String changeDescription(TransformResult transformResult)
      throws ValidationException {
    ChangeMessage message = ChangeMessage.parseMessage(transformResult.getSummary());
    if (transformResult.isSetRevId()) {
      message =
          message.withNewOrReplacedLabel(
              transformResult.getRevIdLabel(),
              ORIGIN_LABEL_SEPARATOR,
              transformResult.getCurrentRevision().asString());
    }
    return message.toString();
  }

  private static String sanitize(String value) {
    return value.replaceAll("[^A-Za-z0-9_]", "_");
  }

  static PerforceDestination newPerforceDestination(
      Options options,
      String stream,
      boolean submitAsAuthor,
      @Nullable UsernamePasswordIssuer credentials,
      @Nullable Checker checker) {
    return new PerforceDestination(
        options.get(GeneralOptions.class),
        options.get(PerforceOptions.class),
        stream,
        submitAsAuthor,
        credentials,
        checker);
  }
}
