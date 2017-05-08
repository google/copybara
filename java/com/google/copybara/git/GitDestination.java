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

package com.google.copybara.git;

import static com.google.copybara.ChangeMessage.parseMessage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.ChangeMessage;
import com.google.copybara.ChangeRejectedException;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.RepoException;
import com.google.copybara.Revision;
import com.google.copybara.TransformResult;
import com.google.copybara.ValidationException;
import com.google.copybara.git.ChangeReader.GitChange;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.GitRepository.LogCmd;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.TempDirectoryFactory;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * A Git repository destination.
 */
public final class GitDestination implements Destination<GitRevision> {

  private static final ImmutableSet<String> SINGLE_ROOT_WITHOUT_FOLDER = ImmutableSet.of("");
  private static final String ORIGIN_LABEL_SEPARATOR = ": ";

  static class MessageInfo {
    final String text;
    final boolean newPush;

    MessageInfo(String text, boolean newPush) {
      this.text = text;
      this.newPush = newPush;
    }
  }

  interface CommitGenerator {
    /** Generates a commit message based on the uncommitted index stored in the given repository. */
    MessageInfo message(TransformResult transformResult, GitRepository repo)
        throws RepoException, ValidationException;
  }

  static final class DefaultCommitGenerator implements CommitGenerator {
    @Override
    public MessageInfo message(TransformResult transformResult, GitRepository repo) {

      Revision rev = transformResult.getCurrentRevision();
      ChangeMessage msg = parseMessage(transformResult.getSummary())
          .addOrReplaceLabel(rev.getLabelName(), ORIGIN_LABEL_SEPARATOR, rev.asString());
      return new MessageInfo(msg.toString(), /*newPush*/true);
    }
  }

  private static final Logger logger = Logger.getLogger(GitDestination.class.getName());

  private final String repoUrl;
  private final String fetch;
  private final String push;
  private final GitDestinationOptions destinationOptions;
  private final boolean verbose;
  private final boolean force;
  // Whether the skip_push flag is set in copy.bara.sky
  private final boolean skipPush;
  // Whether skip_push is set, either by command line or copy.bara.sky
  private final boolean effectiveSkipPush;
  private final CommitGenerator commitGenerator;
  private final ProcessPushOutput processPushOutput;
  private final Map<String, String> environment;
  private final Console console;
  private final TempDirectoryFactory tempDirectoryFactory;
  private boolean localRepoInitialized = false;

  GitDestination(String repoUrl, String fetch, String push,
      GitDestinationOptions destinationOptions, boolean verbose, boolean force, boolean skipPush,
      CommitGenerator commitGenerator, ProcessPushOutput processPushOutput,
      Map<String, String> environment, Console console, TempDirectoryFactory tempDirectoryFactory) {
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.fetch = Preconditions.checkNotNull(fetch);
    this.push = Preconditions.checkNotNull(push);
    this.destinationOptions = Preconditions.checkNotNull(destinationOptions);
    this.verbose = verbose;
    this.force = force;
    this.skipPush = skipPush;
    this.effectiveSkipPush = skipPush || destinationOptions.skipPush;
    this.commitGenerator = Preconditions.checkNotNull(commitGenerator);
    this.processPushOutput = Preconditions.checkNotNull(processPushOutput);
    this.environment = environment;
    this.console = console;
    this.tempDirectoryFactory = Preconditions.checkNotNull(tempDirectoryFactory);
  }

  /**
   * Throws an exception if the user.email or user.name Git configuration settings are not set. This
   * helps ensure that the committer field of generated commits is correct.
   */
  private void verifyUserInfoConfigured(GitRepository repo) throws RepoException {
    String output = repo.simpleCommand("config", "-l").getStdout();
    boolean nameConfigured = false;
    boolean emailConfigured = false;
    for (String line : output.split("\n")) {
      if (line.startsWith("user.name=")) {
        nameConfigured = true;
      } else if (line.startsWith("user.email=")) {
        emailConfigured = true;
      }
    }
    if (!nameConfigured || !emailConfigured) {
      throw new RepoException("'user.name' and/or 'user.email' are not configured. Please run "
          + "`git config --global SETTING VALUE` to set them");
    }
  }

  @Override
  public Writer newWriter(Glob destinationFiles, boolean dryRun) {
    return new WriterImpl(destinationFiles, dryRun);
  }

  private class WriterImpl implements Writer {

    @Nullable private GitRepository scratchClone;
    private final Glob destinationFiles;
    private final boolean dryRun;

    WriterImpl(Glob destinationFiles, boolean dryRun) {
      this.destinationFiles = Preconditions.checkNotNull(destinationFiles);
      this.dryRun = dryRun;
    }

    @Nullable
    @Override
    public String getPreviousRef(String labelName) throws RepoException {
      if (force) {
        return null;
      }
      ImmutableSet<String> roots = destinationFiles.roots();
      GitRepository gitRepository = cloneBaseline(/*fetchIfInitialized=*/);
      ImmutableCollection<String> paths = roots.equals(SINGLE_ROOT_WITHOUT_FOLDER)
          ? ImmutableList.of()
          : roots;

      String startRef = gitRepository.revParse("FETCH_HEAD");
      LogCmd logCmd = gitRepository.log(startRef)
          .grep("^" + labelName + ORIGIN_LABEL_SEPARATOR)
          .firstParent(destinationOptions.lastRevFirstParent)
          .withPaths(paths);

      // 99% of the times it will be the first match. But grep could return a false positive
      // for a comment that contains labelName. But if entries is empty we know for sure
      // that the label is not there.
      ImmutableList<GitLogEntry> entries = logCmd.withLimit(1).run();
      if (entries.isEmpty()) {
        return null;
      }

      String value = findLabelValue(labelName, entries);
      if (value != null) {
        return value;
      }
      // Lets try with the latest matches. If we have that many false positives we give up.
      entries = logCmd.withLimit(50).run();
      return findLabelValue(labelName, entries);
    }

    @Nullable
    private String findLabelValue(String labelName, ImmutableList<GitLogEntry> entries) {
      for (GitLogEntry entry : entries) {
        List<String> prev = parseMessage(entry.getBody()).labelsAsMultimap().get(labelName);
        if (!prev.isEmpty()) {
          return Iterables.getLast(prev);
        }
      }
      return null;
    }

    @Override
    public WriterResult write(TransformResult transformResult, Console console)
        throws ValidationException, RepoException, IOException {
      logger.log(Level.INFO, "Exporting from " + transformResult.getPath() + " to: " + this);
      String baseline = transformResult.getBaseline();
      boolean pushToRemote = !GitDestination.this.effectiveSkipPush && !dryRun;
      if (scratchClone == null) {
        console.progress("Git Destination: Fetching " + repoUrl);

        scratchClone = cloneBaseline();
        // Should be a no-op, but an iterative migration could take several minutes between
        // migrations so lets fetch the latest first.
        if (pushToRemote) {
          fetchFromRemote(scratchClone);
        }

        if (force && baseline != null) {
          //TODO Here
          throw new RepoException(
              "Cannot use " + GeneralOptions.FORCE + " and a previous baseline ("
                  + baseline + "). Migrate some code to " + repoUrl + ":" + repoUrl + " first.");
        }

        console.progress("Git Destination: Checking out " + fetch);
        // If baseline is not null we sync first to the baseline and apply the changes on top of
        // that. Then we will rebase the new change to FETCH_HEAD.
        String reference = baseline != null ? baseline : "FETCH_HEAD";
        try {
          scratchClone.simpleCommand("checkout", "-q", reference);
        } catch (RepoException e) {
          if (force) {
            console.warn(String.format(
                "Git Destination: Cannot checkout '%s'. Ignoring baseline.", reference));
          } else {
            throw new RepoException(String.format(
                "Cannot checkout '%s' from '%s'. Use '%s' if the destination is a new git repo or"
                    + " you don't care about the destination current status", reference, repoUrl,
                GeneralOptions.FORCE), e);
          }
        }

        if (!Strings.isNullOrEmpty(destinationOptions.committerName)) {
          scratchClone.simpleCommand("config", "user.name", destinationOptions.committerName);
        }
        if (!Strings.isNullOrEmpty(destinationOptions.committerEmail)) {
          scratchClone.simpleCommand("config", "user.email", destinationOptions.committerEmail);
        }
        verifyUserInfoConfigured(scratchClone);
      }

      // Get the submodules before we stage them for deletion with
      // alternate.simpleCommand(add --all)
      AddExcludedFilesToIndex excludedAdder =
          new AddExcludedFilesToIndex(scratchClone, destinationFiles);
      excludedAdder.findSubmodules(console);

      console.progress("Git Destination: Cloning destination");
      GitRepository alternate = scratchClone.withWorkTree(transformResult.getPath());
      console.progress("Git Destination: Adding all files");
      alternate.add().force().all().run();

      console.progress("Git Destination: Excluding files");

      excludedAdder.add();

      console.progress("Git Destination: Creating a local commit");
      MessageInfo messageInfo = commitGenerator.message(transformResult, alternate);
      alternate.commit(
          transformResult.getAuthor().toString(),
          transformResult.getTimestamp(),
          messageInfo.text);

      if (baseline != null) {
        // Our current implementation (That we should change) leaves unstaged files in the
        // work-tree. This is fine for commit/push but not for rebase, since rebase could fail
        // and needs to create a conflict resolution work-tree.
        alternate.simpleCommand("reset", "--hard");
        alternate.rebase("FETCH_HEAD");
      }

      if (destinationOptions.localRepoPath != null) {
        // If the user provided a directory for the local repo we don't want to leave changes
        // in the checkout dir. Remove tracked changes:
        scratchClone.simpleCommand("reset", "--hard");
        // ...and untracked ones:
        scratchClone.simpleCommand("clean", "-f");

        // Update current HEAD to point to a named reference so that it shows nice in the created
        // repo. This is purely cosmetic since we push using remote.origin.push.
        String localRef = push.startsWith("refs/") ? push : "refs/heads/" + push;
        scratchClone.simpleCommand("update-ref", localRef, "HEAD");
        scratchClone.simpleCommand("checkout", localRef);
      }

      if (transformResult.isAskForConfirmation()) {
        // The git repo contains the staged changes at this point. Git diff writes to Stdout
        console.info(DiffUtil.colorize(
            console, alternate.simpleCommand("show", "HEAD").getStdout()));
        if (!console.promptConfirmation(
            String.format("Proceed with push to %s %s?", repoUrl, push))) {
          console.warn("Migration aborted by user.");
          throw new ChangeRejectedException(
              "User aborted execution: did not confirm diff changes.");
        }
      }
      if (pushToRemote) {
        console.progress(String.format("Git Destination: Pushing to %s %s", repoUrl, push));
        // Git push writes to Stderr
        processPushOutput.process(
            alternate.simpleCommand("push", repoUrl, "HEAD:" + GitDestination.this.push)
                .getStderr(),
            messageInfo.newPush);
      } else {
        console.info("Local repository available at " + scratchClone.getWorkTree());
      }
      return WriterResult.OK;
    }
  }

  private GitRepository cloneBaseline() throws RepoException {
    if (destinationOptions.localRepoPath == null) {
      GitRepository scratchClone = GitRepository.initScratchRepo(verbose, environment,
          tempDirectoryFactory);
      fetchFromRemote(scratchClone);
      return scratchClone;
    } else {
      return configLocalRepo();
    }
  }

  private GitRepository configLocalRepo() throws RepoException {
    Path path = Paths.get(destinationOptions.localRepoPath);
    // Skip creating initializing the repo twice, since we delete everything.
    if (localRepoInitialized) {
      return new GitRepository(path.resolve(".git"), path, verbose, environment);
    }

    try {
      if (Files.exists(path)) {
        FileUtil.deleteAllFilesRecursively(path);
      } else {
        Files.createDirectories(path);
      }
    } catch (IOException e) {
      throw new RepoException("Cannot delete existing local repository", e);
    }
    GitRepository scratchClone = GitRepository.initScratchRepo(verbose, path, environment);
    // Configure the local repo to allow pushing to the ref manually outside of Copybara
    scratchClone.simpleCommand("remote", "add", "origin", repoUrl);
    scratchClone.simpleCommand("config", "--local", "remote.origin.push", "HEAD:" + push);
    fetchFromRemote(scratchClone);

    localRepoInitialized = true;
    return scratchClone;
  }

  private void fetchFromRemote(GitRepository scratchClone) throws RepoException {
    try {
      scratchClone.fetchSingleRef(repoUrl, fetch);
    } catch (CannotResolveRevisionException e) {
      if (!force) {
        throw new RepoException("'" + fetch + "' doesn't exist in '" + repoUrl
            + "'. Use " + GeneralOptions.FORCE + " flag if you want to push anyway");
      }
    }
  }

  @VisibleForTesting
  String getFetch() {
    return fetch;
  }

  @VisibleForTesting
  String getPush() {
    return push;
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("repoUrl", repoUrl)
        .add("fetch", fetch)
        .add("push", push)
        .add("skip_push", skipPush)
        .toString();
  }

  /**
   * Process the server response from the push command
   */
  static class ProcessPushOutput {

    /**
     * @param output - the message for the commit
     * @param newPush - true if is the first time we are pushing to the origin ref
     */
    void process(String output, boolean newPush) {}
  }

  @Override
  public Reader<GitRevision> newReader(Glob destinationFiles) {
    // TODO(hsudhof): limit the reader to changes affecting destinationFiles.
    return new GitReader();
  }

  class GitReader implements Reader<GitRevision> {

    @Override
    public void visitChanges(GitRevision start, ChangesVisitor visitor)
        throws RepoException, CannotResolveRevisionException {
      GitRepository repository = cloneBaseline();
      String revString = start == null ? "FETCH_HEAD" : start.asString();
      ChangeReader changeReader =
          ChangeReader.Builder.forDestination(repository, console)
              .setVerbose(verbose)
              .setLimit(1)
              .build();

      ImmutableList<GitChange> result = changeReader.run(revString);
      if (result.isEmpty()) {
        if (start == null) {
          console.error("Unable to find HEAD - is the destination repository bare?");
        }
        throw new CannotResolveRevisionException("Cannot find reference " + revString);
      }
      GitChange current = Iterables.getOnlyElement(result);
      while (current != null) {
        if (visitor.visit(current.getChange()) == VisitResult.TERMINATE
            || current.getParents().isEmpty()) {
          break;
        }
        current =
            Iterables.getOnlyElement(changeReader.run(current.getParents().get(0).asString()));
      }
    }
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
            .put("type", "git.destination")
            .put("url", repoUrl)
            .put("fetch", fetch)
            .put("push", push);
    if (skipPush) {
      builder.put("skip_push", "" + skipPush);
    }
    return builder.build();
  }

}
