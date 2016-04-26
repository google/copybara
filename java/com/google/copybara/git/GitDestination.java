// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * A Git repository destination.
 */
public final class GitDestination implements Destination {

  interface CommitGenerator {
    /**
     * Generates a commit message based on the uncommitted index stored in the given repository.
     */
    String message(String commitMsg, GitRepository repo, String originRef) throws RepoException;
  }

  private static final class DefaultCommitGenerator implements CommitGenerator {
    @Override
    public String message(String commitMsg, GitRepository repo, String originRef) {
      return String.format("%s\n%s: %s\n",
          commitMsg,
          Origin.COMMIT_ORIGIN_REFERENCE_FIELD,
          originRef
      );
    }
  }

  // We allow shorter git sha1 prefixes, as does git.
  private static final Pattern GIT_SHA1_PATTERN = Pattern.compile("[0-9a-f]{7,40}");

  private static final Logger logger = Logger.getLogger(GitDestination.class.getName());

  private final String repoUrl;
  private final String pullFromRef;
  private final String pushToRef;
  private final String author;
  private final GitOptions gitOptions;
  private final boolean verbose;
  private final CommitGenerator commitGenerator;

  GitDestination(String repoUrl, String pullFromRef, String pushToRef, String author,
      GitOptions gitOptions, boolean verbose, CommitGenerator commitGenerator) {
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.pullFromRef = Preconditions.checkNotNull(pullFromRef);
    this.pushToRef = Preconditions.checkNotNull(pushToRef);
    this.author = Preconditions.checkNotNull(author);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.verbose = verbose;
    this.commitGenerator = Preconditions.checkNotNull(commitGenerator);
  }

  @Override
  public void process(Path workdir, String originRef, long timestamp,
      String changesSummary) throws RepoException {
    logger.log(Level.INFO, "Exporting " + workdir + " to: " + this);

    GitRepository scratchClone = cloneBaseline();
    if (!gitOptions.gitFirstCommit) {
      scratchClone.simpleCommand("checkout", "-q", "FETCH_HEAD");
    }
    GitRepository alternate = scratchClone.withWorkTree(workdir);
    alternate.simpleCommand("add", "--all");
    alternate.simpleCommand("commit",
        "--author", author,
        "--date", timestamp + " +0000",
        "-m", commitGenerator.message(changesSummary, alternate, originRef));
    alternate.simpleCommand("push", repoUrl, "HEAD:" + pushToRef);
  }

  private GitRepository cloneBaseline() throws RepoException {
    GitRepository scratchClone = GitRepository.initScratchRepo(gitOptions, verbose);
    try {
      scratchClone.simpleCommand("fetch", repoUrl, pullFromRef);
      if (gitOptions.gitFirstCommit) {
        throw new RepoException("'" + pullFromRef + "' already exists in '" + repoUrl + "'.");
      }
    } catch (CannotFindReferenceException e) {
      if (!gitOptions.gitFirstCommit) {
        throw new RepoException("'" + pullFromRef + "' doesn't exist in '" + repoUrl
            + "'. Use --git-first-commit flag if you want to push anyway");
      }
    }
    return scratchClone;
  }

  @Nullable
  @Override
  public String getPreviousRef() throws RepoException {
    // For now we only rely on users using the flag
    if (!Strings.isNullOrEmpty(gitOptions.gitPreviousRef)) {
      Matcher matcher = GIT_SHA1_PATTERN.matcher(gitOptions.gitPreviousRef);
      if (matcher.matches()) {
        return gitOptions.gitPreviousRef;
      }
      throw new RepoException("Invalid git SHA-1 reference " + gitOptions.gitPreviousRef);
    }
    if (gitOptions.gitFirstCommit) {
      return null;
    }
    GitRepository gitRepository = cloneBaseline();
    String commit = gitRepository.revParse("FETCH_HEAD");
    String log = gitRepository.simpleCommand("log", commit, "-1").getStdout();
    String prefix = "    " + Origin.COMMIT_ORIGIN_REFERENCE_FIELD + ": ";
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
        .add("repoUrl", repoUrl)
        .add("pullFromRef", pullFromRef)
        .add("pushToRef", pushToRef)
        .add("gitOptions", gitOptions)
        .add("verbose", verbose)
        .add("commitGenerator", commitGenerator)
        .toString();
  }

  public static final class Yaml extends AbstractDestinationYaml {
    private String pushToRef;

    /**
     * Indicates the ref to push to after the repository has been updated. For instance, to create a
     * Gerrit review, this can be {@code refs/for/master}. This can also be set to the same value as
     * {@code defaultTrackingRef}.
     */
    public void setPushToRef(String pushToRef) {
      this.pushToRef = pushToRef;
    }

    @Override
    public GitDestination withOptions(Options options) throws ConfigValidationException {
      ConfigValidationException.checkNotMissing(url, "url");

      return new GitDestination(
          url,
          ConfigValidationException.checkNotMissing(pullFromRef, "pullFromRef"),
          ConfigValidationException.checkNotMissing(pushToRef, "pushToRef"),
          author,
          options.get(GitOptions.class),
          options.get(GeneralOptions.class).isVerbose(),
          new DefaultCommitGenerator());
    }
  }
}
