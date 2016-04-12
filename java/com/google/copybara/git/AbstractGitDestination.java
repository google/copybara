// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.copybara.Destination;
import com.google.copybara.RepoException;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common superclass for Git repository destinations.
 */
public abstract class AbstractGitDestination implements Destination {

  private static final Logger logger = Logger.getLogger(AbstractGitDestination.class.getName());

  protected final String repoUrl;
  protected final String pullFromRef;
  protected final String pushToRef;
  protected final GitOptions gitOptions;
  protected final boolean verbose;

  protected AbstractGitDestination(String repoUrl, String pullFromRef, String pushToRef,
      GitOptions gitOptions, boolean verbose) {
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.pullFromRef = Preconditions.checkNotNull(pullFromRef);
    this.pushToRef = Preconditions.checkNotNull(pushToRef);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.verbose = verbose;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("repoUrl", repoUrl)
        .add("pullFromRef", pullFromRef)
        .add("pushToRef", pushToRef)
        .add("gitOptions", gitOptions)
        .add("verbose", verbose)
        .toString();
  }

  @Override
  public void process(Path workdir) throws RepoException {
    logger.log(Level.INFO, "Exporting " + workdir + " to: " + this);

    GitRepository scratchClone = GitRepository.initScratchRepo(gitOptions, verbose);
    try {
      scratchClone.simpleCommand("fetch", repoUrl, pullFromRef);
      if (gitOptions.gitFirstCommit) {
        throw new RepoException("'" + pullFromRef + "' already exists in '" + repoUrl + "'.");
      }
      scratchClone.simpleCommand("checkout", "FETCH_HEAD");
    } catch (CannotFindReferenceException e) {
      if (!gitOptions.gitFirstCommit) {
        throw new RepoException("'" + pullFromRef + "' doesn't exist in '" + repoUrl
            + "'. Use --git-first-commit flag if you want to push anyway");
      }
    }
    GitRepository alternate = scratchClone.withWorkTree(workdir);
    alternate.simpleCommand("add", "--all");
    alternate.simpleCommand("commit", "-m", commitMessage());
    alternate.simpleCommand("push", repoUrl, "HEAD:" + pushToRef);
  }

  protected String commitMessage() throws RepoException {
    return "Copybara commit";
  }

  protected static abstract class AbstractYaml implements Destination.Yaml {
    protected String url;
    protected String pullFromRef;

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
  }
}
