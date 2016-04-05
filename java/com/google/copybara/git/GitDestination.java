// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.Preconditions;
import com.google.copybara.Destination;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Git repository destination.
 */
public final class GitDestination implements Destination {

  private static final Logger logger = Logger.getLogger(GitDestination.class.getName());

  private final GitRepository repository;
  private final String pushToRef;

  private GitDestination(GitRepository repository, String pushToRef) {
    this.repository = Preconditions.checkNotNull(repository);
    this.pushToRef = Preconditions.checkNotNull(pushToRef);
  }

  @Override
  public String toString() {
    return String.format("{repository: %s, pushToRef: %s}", repository, pushToRef);
  }

  @Override
  public void process(Path workdir) {
    logger.log(Level.INFO, "Exporting " + workdir + " to: " + this);

    // TODO(matvore): Implement.
  }

  public static final class Yaml implements Destination.Yaml {
    private GitRepository.Yaml repositoryYaml = new GitRepository.Yaml();
    private String pushToRef;

    /**
     * Indicates the URL to push to as well as the URL from which to get the parent commit.
     */
    public void setUrl(String url) {
      repositoryYaml.setUrl(url);
    }

    /**
     * Indicates the ref from which to get the parent commit.
     */
    public void setDefaultTrackingRef(String defaultTrackingRef) {
      repositoryYaml.setDefaultTrackingRef(defaultTrackingRef);
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
      return new GitDestination(
          repositoryYaml.withOptions(options),
          ConfigValidationException.checkNotMissing(pushToRef, "pushToRef"));
    }
  }
}
