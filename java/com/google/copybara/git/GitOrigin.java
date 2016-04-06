package com.google.copybara.git;

import com.google.common.base.Strings;
import com.google.common.net.PercentEscaper;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;

import com.beust.jcommander.internal.Nullable;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * A class for manipulating Git repositories
 */
public final class GitOrigin implements Origin {

  private final GitRepository repository;

  /**
   * Default reference to track
   */
  @Nullable
  private final String defaultTrackingRef;

  GitOrigin(GitRepository repository, @Nullable String defaultTrackingRef) {
    this.repository = repository;
    this.defaultTrackingRef = defaultTrackingRef;
  }

  public GitRepository getRepository() {
    return repository;
  }

  /**
   * Creates a worktree with the contents of the ref {@code ref} for the repository {@code repoUrl}
   *
   * <p>Any content in the workdir is removed/overwritten.
   */
  @Override
  public void checkoutReference(@Nullable String reference, Path workdir) throws RepoException {
    String ref;
    if (Strings.isNullOrEmpty(reference)) {
      if (defaultTrackingRef == null) {
        throw new RepoException("No reference was pass for " + repository.getRepoUrl()
            + " and no default reference was configured");
      }
      ref = defaultTrackingRef;
    } else {
      ref = reference;
    }
    repository.checkoutReference(ref, workdir);
  }

  @Override
  public String toString() {
    return "GitOrigin{" +
        "repository=" + repository +
        ", defaultTrackingRef='" + defaultTrackingRef + '\'' +
        '}';
  }

  public final static class Yaml implements Origin.Yaml {

    private String url;
    private String defaultTrackingRef;

    public void setUrl(String url) {
      this.url = url;
    }

    public void setDefaultTrackingRef(String defaultTrackingRef) {
      this.defaultTrackingRef = defaultTrackingRef;
    }

    @Override
    public GitOrigin withOptions(Options options) {
      ConfigValidationException.checkNotMissing(url, "url");

      return new GitOrigin(GitRepository.withRepoUrl(url, options), defaultTrackingRef);
    }
  }
}
