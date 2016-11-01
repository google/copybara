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

import static com.google.copybara.git.GitDestinationOptions.FIRST_COMMIT_FLAG;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.copybara.Authoring;
import com.google.copybara.ChangeRejectedException;
import com.google.copybara.Destination;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.git.ChangeReader.GitChange;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.Glob;
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
public final class GitDestination implements Destination<GitReference> {

  private static final ImmutableSet<String> SINGLE_ROOT_WITHOUT_FOLDER = ImmutableSet.of("");

  interface CommitGenerator {
    /**
     * Generates a commit message based on the uncommitted index stored in the given repository.
     */
    String message(TransformResult transformResult, GitRepository repo)
        throws RepoException;
  }

  static final class DefaultCommitGenerator implements CommitGenerator {
    @Override
    public String message(TransformResult transformResult, GitRepository repo) {
      return String.format("%s\n%s: %s\n",
          transformResult.getSummary(),
          transformResult.getOriginRef().getLabelName(),
          transformResult.getOriginRef().asString()
      );
    }
  }

  private static final Logger logger = Logger.getLogger(GitDestination.class.getName());

  private final String repoUrl;
  private final String fetch;
  private final String push;
  private final GitDestinationOptions destinationOptions;
  private final boolean verbose;
  private final CommitGenerator commitGenerator;
  private final ProcessPushOutput processPushOutput;
  private final Map<String, String> environment;
  private final Console console;

  GitDestination(String repoUrl, String fetch, String push,
      GitDestinationOptions destinationOptions, boolean verbose, CommitGenerator commitGenerator,
      ProcessPushOutput processPushOutput, Map<String, String> environment, Console console) {
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.fetch = Preconditions.checkNotNull(fetch);
    this.push = Preconditions.checkNotNull(push);
    this.destinationOptions = Preconditions.checkNotNull(destinationOptions);
    this.verbose = verbose;
    this.commitGenerator = Preconditions.checkNotNull(commitGenerator);
    this.processPushOutput = Preconditions.checkNotNull(processPushOutput);
    this.environment = environment;
    this.console = console;
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
      if (destinationOptions.firstCommit) {
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
        throws IOException, RepoException {
      logger.log(Level.INFO, "Exporting from " + transformResult.getPath() + " to: " + this);

      String baseline = transformResult.getBaseline();
      if (scratchClone == null) {
        console.progress("Git Destination: Fetching " + repoUrl);

        scratchClone = cloneBaseline();
        if (destinationOptions.firstCommit && baseline != null) {
          throw new RepoException(
              "Cannot use " + FIRST_COMMIT_FLAG + " and a previous baseline (" + baseline
              + "). Migrate some code to " + repoUrl + ":" + repoUrl + " first.");
        }
        if (!destinationOptions.firstCommit) {
          console.progress("Git Destination: Checking out " + fetch);
          // If baseline is not null we sync first to the baseline and apply the changes on top of
          // that. Then we will rebase the new change to FETCH_HEAD.
          scratchClone.simpleCommand("checkout", "-q", baseline != null ? baseline : "FETCH_HEAD");
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
      alternate.commit(transformResult.getAuthor().toString(), transformResult.getTimestamp(),
          commitGenerator.message(transformResult, alternate));

      if (baseline != null) {
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
          alternate.simpleCommand("push", repoUrl, "HEAD:" + GitDestination.this.push).getStderr());
      return WriterResult.OK;
    }
  }

  private GitRepository cloneBaseline() throws RepoException {
    GitRepository scratchClone = GitRepository.initScratchRepo(verbose, environment);
    try {
      scratchClone.fetchSingleRef(repoUrl, fetch);
      if (destinationOptions.firstCommit) {
        throw new RepoException("'" + fetch + "' already exists in '" + repoUrl + "'.");
      }
    } catch (CannotFindReferenceException e) {
      if (!destinationOptions.firstCommit) {
        throw new RepoException("'" + fetch + "' doesn't exist in '" + repoUrl
            + "'. Use --git-first-commit flag if you want to push anyway");
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

    void process(String output) {

    }
  }

  @Override
  public Reader<GitReference> newReader(Glob destinationFiles) {
    // TODO(hsudhof): limit the reader to changes affecting destinationFiles.
    return new GitReader();
  }

  class GitReader implements Reader<GitReference> {

    @Override
    public void visitChanges(GitReference start, ChangesVisitor visitor)
        throws RepoException {
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
        throw new CannotFindReferenceException("Cannot find reference " + revString);
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
}
