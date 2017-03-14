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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.ChangeRejectedException;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.ValidationException;
import com.google.copybara.git.ChangeReader.GitChange;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.TempDirectoryFactory;
import com.google.copybara.util.console.Console;
import java.io.IOException;
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
    MessageInfo message(TransformResult transformResult, GitRepository repo) throws RepoException;
  }

  static final class DefaultCommitGenerator implements CommitGenerator {
    @Override
    public MessageInfo message(TransformResult transformResult, GitRepository repo) {
      return new MessageInfo(
          String.format(
              "%s\n\n%s: %s\n",
              transformResult.getSummary(),
              transformResult.getCurrentRevision().getLabelName(),
              transformResult.getCurrentRevision().asString()),
          /*newPush*/ true);
    }
  }

  private static final Logger logger = Logger.getLogger(GitDestination.class.getName());

  private final String repoUrl;
  private final String fetch;
  private final String push;
  private final GitDestinationOptions destinationOptions;
  private final boolean verbose;
  private final boolean force;
  private final CommitGenerator commitGenerator;
  private final ProcessPushOutput processPushOutput;
  private final Map<String, String> environment;
  private final Console console;
  private final TempDirectoryFactory tempDirectoryFactory;

  GitDestination(String repoUrl, String fetch, String push,
      GitDestinationOptions destinationOptions, boolean verbose, boolean force,
      CommitGenerator commitGenerator, ProcessPushOutput processPushOutput,
      Map<String, String> environment, Console console, TempDirectoryFactory tempDirectoryFactory) {
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.fetch = Preconditions.checkNotNull(fetch);
    this.push = Preconditions.checkNotNull(push);
    this.destinationOptions = Preconditions.checkNotNull(destinationOptions);
    this.verbose = verbose;
    this.force = force;
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
  public Writer newWriter(Glob destinationFiles) {
    return new WriterImpl(destinationFiles);
  }

  private class WriterImpl implements Writer {

    @Nullable private GitRepository scratchClone;
    private final Glob destinationFiles;

    WriterImpl(Glob destinationFiles) {
      this.destinationFiles = Preconditions.checkNotNull(destinationFiles);
    }

    @Nullable
    @Override
    public String getPreviousRef(String labelName) throws RepoException {
      if (force) {
        return null;
      }
      ImmutableSet<String> roots = destinationFiles.roots();
      GitRepository gitRepository = cloneBaseline();
      String commit = gitRepository.revParse("FETCH_HEAD");
      String labelPrefix = labelName + ": ";
      // Look at commits in reverse chronological order, starting from FETCH_HEAD.
      while (!commit.isEmpty()) {
        // Get commit message body.
        String body = gitRepository.simpleCommand(
            createPreviousRefLogCommand(roots, commit, "--format=%b")).getStdout();
        for (String line : body.split("\n")) {
          if (line.startsWith(labelPrefix)) {
            return line.substring(labelPrefix.length());
          }
        }

        // Get parent hash.
        commit = gitRepository.simpleCommand(
            createPreviousRefLogCommand(roots, commit, "--format=%P")).getStdout().trim();
        if (commit.indexOf(' ') != -1) {
          throw new RepoException(
              "Found commit with multiple parents (merge commit) when looking for "
              + labelName + ". Please invoke Copybara with the --last-rev flag.");
        }
      }

      return null;
    }

    private String[] createPreviousRefLogCommand(ImmutableSet<String> roots, String commit,
        String format) {
      List<String> args = Lists.newArrayList("log", "--no-color", format, commit, "-1");
      if (!roots.isEmpty() && !roots.equals(SINGLE_ROOT_WITHOUT_FOLDER)) {
        args.add("--");
        args.addAll(roots);
      }
      return args.toArray(new String[args.size()]);
    }

    @Override
    public WriterResult write(TransformResult transformResult, Console console)
        throws ValidationException, RepoException, IOException {
      logger.log(Level.INFO, "Exporting from " + transformResult.getPath() + " to: " + this);

      String baseline = transformResult.getBaseline();
      if (scratchClone == null) {
        console.progress("Git Destination: Fetching " + repoUrl);

        scratchClone = cloneBaseline();
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
      console.progress(String.format("Git Destination: Pushing to %s %s", repoUrl, push));
      // Git push writes to Stderr
      processPushOutput.process(
          alternate.simpleCommand("push", repoUrl, "HEAD:" + GitDestination.this.push).getStderr(),
          messageInfo.newPush);
      return WriterResult.OK;
    }
  }

  private GitRepository cloneBaseline() throws RepoException {
    GitRepository scratchClone =
        GitRepository.initScratchRepo(verbose, environment, tempDirectoryFactory);
    try {
      scratchClone.fetchSingleRef(repoUrl, fetch);
    } catch (CannotResolveRevisionException e) {
      if (!force) {
        throw new RepoException("'" + fetch + "' doesn't exist in '" + repoUrl
            + "'. Use " + GeneralOptions.FORCE + " flag if you want to push anyway");
      }
    }
    return scratchClone;
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
    return new ImmutableSetMultimap.Builder<String, String>()
        .put("type", "git.destination")
        .put("url", repoUrl)
        .put("fetch", fetch)
        .put("push", push)
        .build();
  }

}
