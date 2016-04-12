// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;

import java.util.logging.Logger;

/**
 * A Git repository destination.
 */
public final class GitDestination extends AbstractGitDestination {

  private static final Logger logger = Logger.getLogger(GitDestination.class.getName());

  private GitDestination(String repoUrl, String pullFromRef, String pushToRef, GitOptions gitOptions,
      boolean verbose) {
    super(repoUrl, pullFromRef, pushToRef, gitOptions, verbose);
  }

  public static final class Yaml extends AbstractYaml {
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
