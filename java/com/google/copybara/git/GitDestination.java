// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin.Reference;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.util.console.Console;

import java.nio.file.Path;
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
    String message(String commitMsg, GitRepository repo, Reference<?> originRef)
        throws RepoException;
  }

  private static final class DefaultCommitGenerator implements CommitGenerator {
    @Override
    public String message(String commitMsg, GitRepository repo, Reference<?> originRef) {
      return String.format("%s\n%s: %s\n",
          commitMsg,
          originRef.getLabelName(),
          originRef.asString()
      );
    }
  }

  private static final Logger logger = Logger.getLogger(GitDestination.class.getName());

  private final String configName;
  private final String repoUrl;
  private final String fetch;
  private final String push;
  private final String author;
  private final GitOptions gitOptions;
  private final boolean verbose;
  private final CommitGenerator commitGenerator;

  GitDestination(String configName, String repoUrl, String fetch, String push,
      String author, GitOptions gitOptions, boolean verbose, CommitGenerator commitGenerator) {
    this.configName = Preconditions.checkNotNull(configName);
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.fetch = Preconditions.checkNotNull(fetch);
    this.push = Preconditions.checkNotNull(push);
    this.author = Preconditions.checkNotNull(author);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.verbose = verbose;
    this.commitGenerator = Preconditions.checkNotNull(commitGenerator);
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
  public void process(Path workdir, Reference<?> originRef, long timestamp,
      String changesSummary, Console console) throws RepoException {
    logger.log(Level.INFO, "Exporting " + configName + " from " + workdir + " to: " + this);

    console.progress("Git Destination: Fetching " + repoUrl);
    GitRepository scratchClone = cloneBaseline();
    if (!gitOptions.gitFirstCommit) {
      console.progress("Git Destination: Checking out " + fetch);
      scratchClone.simpleCommand("checkout", "-q", "FETCH_HEAD");
    }
    if (!Strings.isNullOrEmpty(gitOptions.gitCommitterName)) {
      scratchClone.simpleCommand("config", "user.name", gitOptions.gitCommitterName);
    }
    if (!Strings.isNullOrEmpty(gitOptions.gitCommitterEmail)) {
      scratchClone.simpleCommand("config", "user.email", gitOptions.gitCommitterEmail);
    }
    verifyUserInfoConfigured(scratchClone);
    console.progress("Git Destination: Adding files for push");
    GitRepository alternate = scratchClone.withWorkTree(workdir);
    alternate.simpleCommand("add", "--all");
    alternate.simpleCommand("commit",
        "--author", author,
        "--date", timestamp + " +0000",
        "-m", commitGenerator.message(changesSummary, alternate, originRef));
    console.progress("Git Destination: Pushing to " + repoUrl);
    alternate.simpleCommand("push", repoUrl, "HEAD:" + push);
  }

  private GitRepository cloneBaseline() throws RepoException {
    GitRepository scratchClone = GitRepository.initScratchRepo(gitOptions, verbose);
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

  @Nullable
  @Override
  public String getPreviousRef(String labelName) throws RepoException {
    if (gitOptions.gitFirstCommit) {
      return null;
    }
    GitRepository gitRepository = cloneBaseline();
    String commit = gitRepository.revParse("FETCH_HEAD");
    String log = gitRepository.simpleCommand("log", commit, "-1").getStdout();
    String prefix = "    " + labelName + ": ";
    for (String line : log.split("\n")) {
      if (line.startsWith(prefix)) {
        return line.substring(prefix.length());
      }

    }
    return null;
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
    public GitDestination withOptions(Options options, String configName) throws ConfigValidationException {
      checkRequiredFields();
      return new GitDestination(
          configName,
          url,
          ConfigValidationException.checkNotMissing(fetch, "fetch"),
          ConfigValidationException.checkNotMissing(push, "push"),
          author,
          options.get(GitOptions.class),
          options.get(GeneralOptions.class).isVerbose(),
          new DefaultCommitGenerator()
      );
    }
  }
}
