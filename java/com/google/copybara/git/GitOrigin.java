package com.google.copybara.git;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.PercentEscaper;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;

import javax.annotation.Nullable;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * A class for manipulating Git repositories
 */
public final class GitOrigin implements Origin {

  private static final PercentEscaper PERCENT_ESCAPER = new PercentEscaper(
      "-_", /*plusForSpace=*/ true);

  private final GitRepository repository;

  /**
   * Url of the repository
   */
  private final String repoUrl;

  /**
   * Default reference to track
   */
  @Nullable
  private final String defaultTrackingRef;

  GitOrigin(GitRepository repository, String repoUrl, @Nullable String defaultTrackingRef) {
    this.repository = Preconditions.checkNotNull(repository);
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.defaultTrackingRef = Preconditions.checkNotNull(defaultTrackingRef);
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
    repository.initGitDir();

    String ref;
    if (Strings.isNullOrEmpty(reference)) {
      if (defaultTrackingRef == null) {
        throw new RepoException("No reference was pass for " + repoUrl
            + " and no default reference was configured");
      }
      ref = defaultTrackingRef;
    } else {
      ref = reference;
    }
    repository.simpleCommand("fetch", "-f", repoUrl, ref);
    repository.withWorkTree(workdir).simpleCommand("checkout", "-f", "FETCH_HEAD");
  }

  @Override
  public String toString() {
    return "GitOrigin{" +
        "repository=" + repository +
        "repoUrl=" + repoUrl +
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

      GitOptions gitConfig = options.getOption(GitOptions.class);

      Path gitRepoStorage = FileSystems.getDefault().getPath(gitConfig.gitRepoStorage);
      Path gitDir = gitRepoStorage.resolve(PERCENT_ESCAPER.escape(url));

      return new GitOrigin(GitRepository.bareRepo(gitDir, options), url, defaultTrackingRef);
    }
  }
}
