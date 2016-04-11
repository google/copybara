// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.Preconditions;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Git repository destination.
 */
public final class GitDestination implements Destination {

  private static final Logger logger = Logger.getLogger(GitDestination.class.getName());

  private final String repoUrl;
  private final String pullFromRef;
  private final String pushToRef;
  private final GitOptions gitOptions;
  private final boolean verbose;

  private GitDestination(String repoUrl, String pullFromRef, String pushToRef, GitOptions gitOptions,
      boolean verbose) {
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.pullFromRef = Preconditions.checkNotNull(pullFromRef);
    this.pushToRef = Preconditions.checkNotNull(pushToRef);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.verbose = verbose;
  }

  @Override
  public String toString() {
    return String.format(
        "{repoUrl: %s, pullFromRef: %s, pushToRef: %s, gitOptions: %s, verbose: %s}",
        repoUrl, pullFromRef, pushToRef, gitOptions, verbose);
  }

  @Override
  public void process(Path workdir) throws RepoException {
    logger.log(Level.INFO, "Exporting " + workdir + " to: " + this);

    GitRepository scratchClone = GitRepository.initScratchRepo(gitOptions, verbose);
    try {
      scratchClone.simpleCommand("fetch", repoUrl, pullFromRef);
      scratchClone.simpleCommand("checkout", "FETCH_HEAD");
    } catch (CannotFindReferenceException e) {
      logger.log(Level.INFO, "pullFromRef doesn't exist", e);
    }
    GitRepository alternate = scratchClone.withWorkTree(workdir);
    alternate.simpleCommand("add", "--all");
    alternate.simpleCommand("commit", "-m", "Copybara commit");
    alternate.simpleCommand("push", repoUrl, "HEAD:" + pushToRef);
  }

  public static final class Yaml implements Destination.Yaml {
    private String url;
    private String pullFromRef;
    private String pushToRef;

    /**
     * Indicates the URL to push to as well as the URL from which to get the parent commit.
     */
    public void setUrl(String url) {
      this.url = url;
    }

    /**
     * Indicates the ref from which to get the parent commit.
     */
    public void setPullFromRef(String pullFromRef) {
      this.pullFromRef = pullFromRef;
    }

    /**
     * Indicates the ref to push to after the repository has been updated. For instance, to create a
     * Gerrit review, this can be {@code refs/for/master}. This can also be set to the same value as
     * {@code defaultTrackingRef}.
     */
    public void setPushToRef(String pushToRef) {
      this.pushToRef = pushToRef;
    }

    @Override
    public GitDestination withOptions(Options options) {
      ConfigValidationException.checkNotMissing(url, "url");

      return new GitDestination(
          url,
          ConfigValidationException.checkNotMissing(pullFromRef, "pullFromRef"),
          ConfigValidationException.checkNotMissing(pushToRef, "pushToRef"),
          options.getOption(GitOptions.class),
          options.getOption(GeneralOptions.class).isVerbose());
    }
  }
}
