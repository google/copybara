// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.Strings;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * A Git repository destination.
 */
public final class GitDestination extends AbstractGitDestination {
  // We allow shorter git sha1 prefixes, as does git.
  private static final Pattern GIT_SHA1_PATTERN = Pattern.compile("[0-9a-f]{7,40}");

  private static final Logger logger = Logger.getLogger(GitDestination.class.getName());

  private GitDestination(String repoUrl, String pullFromRef, String pushToRef, GitOptions gitOptions,
      boolean verbose) {
    super(repoUrl, pullFromRef, pushToRef, gitOptions, verbose);
  }

  @Nullable
  @Override
  public String getPreviousRef() throws RepoException {
    // For now we only rely on users using the flag
    if (Strings.isNullOrEmpty(gitOptions.gitPreviousRef)) {
      return null;
    }
    Matcher matcher = GIT_SHA1_PATTERN.matcher(gitOptions.gitPreviousRef);
    if (matcher.matches()) {
      return gitOptions.gitPreviousRef;
    }
    throw new RepoException("Invalid git SHA-1 reference " + gitOptions.gitPreviousRef);
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
          options.get(GitOptions.class),
          options.get(GeneralOptions.class).isVerbose());
    }
  }
}
