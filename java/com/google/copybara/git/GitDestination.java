// Copyright 2016 Google Inc. All Rights Reserved.
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
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.copybara.util.console.Console;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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

  private final String configName;
  private final String repoUrl;
  private final String fetch;
  private final String push;
  private final boolean askConfirmation;
  private final GitOptions gitOptions;
  private final boolean verbose;
  private final CommitGenerator commitGenerator;
  private final ProcessPushOutput processPushOutput;

  GitDestination(String configName, String repoUrl, String fetch, String push,
      boolean askConfirmation, GitOptions gitOptions, boolean verbose,
      CommitGenerator commitGenerator, ProcessPushOutput processPushOutput) {
    this.configName = Preconditions.checkNotNull(configName);
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.fetch = Preconditions.checkNotNull(fetch);
    this.push = Preconditions.checkNotNull(push);
    this.askConfirmation = askConfirmation;
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.verbose = verbose;
    this.commitGenerator = Preconditions.checkNotNull(commitGenerator);
    this.processPushOutput = Preconditions.checkNotNull(processPushOutput);
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
  public Writer newWriter() {
    return new WriterImpl();
  }

  private class WriterImpl implements Writer {
    @Override
    public void write(TransformResult transformResult, Console console) throws RepoException {
      logger.log(Level.INFO,
          "Exporting " + configName + " from " + transformResult.getPath() + " to: " + this);

      console.progress("Git Destination: Fetching " + repoUrl);

      // TODO(matvore): Do not re-generate the scratch clone for each invocation.
      GitRepository scratchClone = cloneBaseline();
      String baseline = transformResult.getBaseline();
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

      console.progress("Git Destination: Adding files for push");
      GitRepository alternate = scratchClone.withWorkTree(transformResult.getPath());
      alternate.simpleCommand("add", "--all");

      new AddMatchingFilesToIndexVisitor(scratchClone, transformResult.getExcludedDestinationPaths())
          .walk();

      if (askConfirmation) {
        // TODO(danielromero): Show diff using git global config
        if (!console.promptConfirmation(
                String.format("Proceed with push to %s %s?", repoUrl, push))) {
          console.info("Migration aborted by user. Local copy of the transformed code: "
              + alternate.getGitDir());
          throw new ChangeRejectedException("User aborted execution: did not confirm diff changes.");
        }
      }

      alternate.simpleCommand("commit",
          "--author", transformResult.getAuthor().toString(),
          "--date", transformResult.getTimestamp() + " +0000",
          "-m", commitGenerator.message(transformResult, alternate));
      console.progress(String.format("Git Destination: Pushing to %s %s", repoUrl, push));

      if (baseline != null) {
        alternate.rebase("FETCH_HEAD");
      }

      // Git push writes to Stderr
      processPushOutput.process(
          alternate.simpleCommand("push", repoUrl, "HEAD:" + GitDestination.this.push).getStderr());
    }
  }

  private GitRepository cloneBaseline() throws RepoException {
    GitRepository scratchClone = GitRepository.initScratchRepo(verbose);
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
  private static final class AddMatchingFilesToIndexVisitor extends SimpleFileVisitor<Path> {
    private final GitRepository repo;
    private final PathMatcher matcher;

    AddMatchingFilesToIndexVisitor(GitRepository repo, PathMatcherBuilder matcherBuilder) {
      this.repo = repo;
      this.matcher = matcherBuilder.relativeTo(repo.getWorkTree());
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
      return dir.equals(repo.getGitDir())
          ? FileVisitResult.SKIP_SUBTREE
          : FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      if (matcher.matches(file)) {
        try {
          repo.simpleCommand("add", file.toString());
        } catch (RepoException e) {
          throw new IOException(e);
        }
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
  public String getLabelNameWhenOrigin() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("configName", configName)
        .add("repoUrl", repoUrl)
        .add("fetch", fetch)
        .add("push", push)
        .add("gitOptions", gitOptions)
        .add("verbose", verbose)
        .add("commitGenerator", commitGenerator)
        .toString();
  }

  @DocElement(yamlName = "!GitDestination",
      description = "Creates a commit in a git repository using the transformed worktree",
      elementKind = Destination.class, flags = {GitOptions.class})
  public static final class Yaml extends AbstractDestinationYaml {
    private String push;

    /**
     * Indicates the ref to push to after the repository has been updated. For instance, to create a
     * Gerrit review, this can be {@code refs/for/master}. This can also be set to the same value as
     * {@code defaultTrackingRef}.
     */
    @DocField(description = "Reference to use for pushing the change, for example 'master'")
    public void setPush(String push) {
      this.push = push;
    }

    @Override
    public GitDestination withOptions(
        Options options, String configName, boolean askConfirmation)
        throws ConfigValidationException {
      checkRequiredFields();
      return new GitDestination(
          configName,
          url,
          ConfigValidationException.checkNotMissing(fetch, "fetch"),
          ConfigValidationException.checkNotMissing(push, "push"),
          askConfirmation,
          options.get(GitOptions.class),
          options.get(GeneralOptions.class).isVerbose(),
          new DefaultCommitGenerator(),
          new ProcessPushOutput()
      );
    }
  }

  /**
   * Process the server response from the push command
   */
  static class ProcessPushOutput {

    void process(String output) {

    }
  }
}
