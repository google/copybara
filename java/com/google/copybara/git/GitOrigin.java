package com.google.copybara.git;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.net.PercentEscaper;
import com.google.copybara.CannotComputeChangesException;
import com.google.copybara.Change;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * A class for manipulating Git repositories
 */
public final class GitOrigin implements Origin<GitOrigin> {

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

  // TODO(malcon): Refactor reference to return a Reference object.
  @Override
  public Reference<GitOrigin> resolve(@Nullable String reference) throws RepoException {
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
    return new GitReference(repository.revParse("FETCH_HEAD"));
  }

  @Override
  public ImmutableList<Change<GitOrigin>> changes(@Nullable Reference<GitOrigin> fromRef,
      Reference<GitOrigin> toRef) throws RepoException {
    throw new CannotComputeChangesException("not supported");
  }

  @Override
  public String toString() {
    return "GitOrigin{" +
        "repository=" + repository +
        "repoUrl=" + repoUrl +
        ", defaultTrackingRef='" + defaultTrackingRef + '\'' +
        '}';
  }

  private final class GitReference implements Reference<GitOrigin> {

    private final String reference;

    private GitReference(String reference) {
      this.reference = reference;
    }

    @Override
    public Long readTimestamp() throws RepoException {
      // -s suppresses diff output
      // --format=%at indicates show the author timestamp as the number of seconds from UNIX epoch
      String stdout = repository.simpleCommand("show", "-s", "--format=%at", reference).getStdout();
      try {
        return Long.parseLong(stdout.trim());
      } catch (NumberFormatException e) {
        throw new RepoException("Output of git show not a valid long", e);
      }
    }

    /**
     * Creates a worktree with the contents of the git reference
     *
     * <p>Any content in the workdir is removed/overwritten.
     */
    @Override
    public void checkout(Path workdir) throws RepoException {
      repository.withWorkTree(workdir).simpleCommand("checkout", "-f", reference);
    }

    @Override
    public String asString() {
      return reference;
    }

    @Override
    public String toString() {
      return "GitReference{reference='" + reference + "', repoUrl=" + repoUrl + '}';
    }
  }

  public final static class Yaml implements Origin.Yaml<GitOrigin> {

    private String url;
    private String defaultTrackingRef;

    public void setUrl(String url) {
      this.url = url;
    }

    public void setDefaultTrackingRef(String defaultTrackingRef) {
      this.defaultTrackingRef = defaultTrackingRef;
    }

    @Override
    public GitOrigin withOptions(Options options) throws ConfigValidationException {
      ConfigValidationException.checkNotMissing(url, "url");

      GitOptions gitConfig = options.get(GitOptions.class);

      Path gitRepoStorage = FileSystems.getDefault().getPath(gitConfig.gitRepoStorage);
      Path gitDir = gitRepoStorage.resolve(PERCENT_ESCAPER.escape(url));

      return new GitOrigin(GitRepository.bareRepo(gitDir, options), url, defaultTrackingRef);
    }
  }
}
