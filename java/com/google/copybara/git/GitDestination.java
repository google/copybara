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

import static com.google.copybara.git.GitOptions.GIT_FIRST_COMMIT_FLAG;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.copybara.ChangeRejectedException;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * A Git repository destination.
 */
public final class GitDestination implements Destination {

  interface CommitGenerator {
    /**
     * Generates a commit message based on the uncommitted index stored in the given repository.
     */
    String message(TransformResult transformResult, GitRepository repo)
        throws RepoException;
  }

  private static final class DefaultCommitGenerator implements CommitGenerator {
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
  private final GitOptions gitOptions;
  private final boolean verbose;
  private final CommitGenerator commitGenerator;
  private final ProcessPushOutput processPushOutput;
  private final Map<String, String> environment;

  GitDestination(String repoUrl, String fetch, String push, GitOptions gitOptions, boolean verbose,
      CommitGenerator commitGenerator, ProcessPushOutput processPushOutput,
      Map<String, String> environment) {
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.fetch = Preconditions.checkNotNull(fetch);
    this.push = Preconditions.checkNotNull(push);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.verbose = verbose;
    this.commitGenerator = Preconditions.checkNotNull(commitGenerator);
    this.processPushOutput = Preconditions.checkNotNull(processPushOutput);
    this.environment = environment;
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
      if (gitOptions.gitFirstCommit) {
        return null;
      }
      GitRepository gitRepository = cloneBaseline();
      String commit = gitRepository.revParse("FETCH_HEAD");
      String labelPrefix = labelName + ": ";
      // Look at commits in reverse chronological order, starting from FETCH_HEAD.
      while (!commit.isEmpty()) {
        // Get commit message body.
        String body = gitRepository.simpleCommand("log", "--no-color", "--format=%b", commit, "-1")
            .getStdout();
        for (String line : body.split("\n")) {
          if (line.startsWith(labelPrefix)) {
            return line.substring(labelPrefix.length());
          }
        }

        // Get parent hash.
        commit = gitRepository.simpleCommand("log", "--no-color", "--format=%P", commit, "-1")
            .getStdout().trim();
        if (commit.indexOf(' ') != -1) {
          throw new RepoException(
              "Found commit with multiple parents (merge commit) when looking for "
              + labelName + ". Please invoke Copybara with the --last-rev flag.");
        }
      }

      return null;
    }

    @Override
    public WriterResult write(TransformResult transformResult, Console console) throws RepoException {
      logger.log(Level.INFO, "Exporting from " + transformResult.getPath() + " to: " + this);

      String baseline = transformResult.getBaseline();
      if (scratchClone == null) {
        console.progress("Git Destination: Fetching " + repoUrl);

        scratchClone = cloneBaseline();
        if (gitOptions.gitFirstCommit && baseline != null) {
          throw new RepoException(
              "Cannot use " + GIT_FIRST_COMMIT_FLAG + " and a previous baseline (" + baseline
              + "). Migrate some code to " + repoUrl + ":" + repoUrl + " first.");
        }
        if (!gitOptions.gitFirstCommit) {
          console.progress("Git Destination: Checking out " + fetch);
          // If baseline is not null we sync first to the baseline and apply the changes on top of
          // that. Then we will rebase the new change to FETCH_HEAD.
          scratchClone.simpleCommand("checkout", "-q", baseline != null ? baseline : "FETCH_HEAD");
        }

        if (!Strings.isNullOrEmpty(gitOptions.gitCommitterName)) {
          scratchClone.simpleCommand("config", "user.name", gitOptions.gitCommitterName);
        }
        if (!Strings.isNullOrEmpty(gitOptions.gitCommitterEmail)) {
          scratchClone.simpleCommand("config", "user.email", gitOptions.gitCommitterEmail);
        }
        verifyUserInfoConfigured(scratchClone);
      }

      console.progress("Git Destination: Creating a local commit");
      GitRepository alternate = scratchClone.withWorkTree(transformResult.getPath());
      alternate.simpleCommand("add", "--all");

      new AddExcludedFilesToIndexVisitor(scratchClone, destinationFiles).walk();

      alternate.commit(alternate, transformResult.getAuthor().toString(),
          transformResult.getTimestamp(), commitGenerator.message(transformResult, alternate));

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
      scratchClone.simpleCommand("fetch", repoUrl, fetch);
      if (gitOptions.gitFirstCommit) {
        throw new RepoException("'" + fetch + "' already exists in '" + repoUrl + "'.");
      }
    } catch (CannotFindReferenceException e) {
      if (!gitOptions.gitFirstCommit) {
        throw new RepoException("'" + fetch + "' doesn't exist in '" + repoUrl
            + "'. Use --git-first-commit flag if you want to push anyway");
      }
    }
    return scratchClone;
  }

  /**
   * A walker which adds all files matching a PathMatcher to the index of a Git repo using
   * {@code git add}.
   */
  private static final class AddExcludedFilesToIndexVisitor extends SimpleFileVisitor<Path> {
    private final GitRepository repo;
    private final PathMatcher destinationFiles;

    AddExcludedFilesToIndexVisitor(GitRepository repo, Glob destinationFilesBuilder) {
      this.repo = repo;
      this.destinationFiles = destinationFilesBuilder.relativeTo(repo.getWorkTree());
    }

    private void git(String command, Path path) throws IOException {
      try {
        repo.simpleCommand(command, "--", path.toString());
      } catch (RepoException e) {
        throw new IOException(e);
      }
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
      return dir.equals(repo.getGitDir())
          ? FileVisitResult.SKIP_SUBTREE
          : FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      if (!destinationFiles.matches(file)) {
        git("add", file);
      }
      return FileVisitResult.CONTINUE;
    }

    void walk() throws RepoException {
      try {
        Files.walkFileTree(repo.getWorkTree(), this);
      } catch (IOException e) {
        throw new RepoException("Error when adding excludedDestinationPaths to destination.", e);
      }
    }
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
   * Builds a new {@link GitDestination}.
   */
  static GitDestination newGitDestination(Options options, String url, String fetch, String push,
      Map<String, String> environment) {
    return new GitDestination(
        url, fetch, push,
        options.get(GitOptions.class),
        options.get(GeneralOptions.class).isVerbose(),
        new DefaultCommitGenerator(),
        new ProcessPushOutput(),
        environment);
  }

  /**
   * Process the server response from the push command
   */
  static class ProcessPushOutput {

    void process(String output) {

    }
  }
}
