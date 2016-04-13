package com.google.copybara.git;

import static com.google.copybara.git.GitRepository.isSha1Reference;

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

  // TODO(malcon): Refactor reference to return a Reference object.
  @Override
  public String resolveReference(@Nullable String reference) throws RepoException {
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
    return repository.revParse("FETCH_HEAD");
  }

  /**
   * Creates a worktree with the contents of the ref {@code ref} for the repository {@code repoUrl}
   *
   * <p>Any content in the workdir is removed/overwritten.
   */
  @Override
  public void checkoutReference(String reference, Path workdir) throws RepoException {
    repository.initGitDir();
    Preconditions.checkArgument(
        isSha1Reference(reference), "'%s' should be already resolved", reference);
    String resolved = repository.revParse(reference);
    // Should never happen unless a shortened ref is passed.
    Preconditions.checkArgument(resolved.equals(reference),
        "'%s' should resolve to the same ref. But was resolved to '%s'", reference, resolved);

    repository.withWorkTree(workdir).simpleCommand("checkout", "-f", resolved);
  }

  @Override
  public ImmutableList<Change> changes(@Nullable String previousRef, String reference)
      throws RepoException {
    repository.initGitDir();
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

      GitOptions gitConfig = options.get(GitOptions.class);

      Path gitRepoStorage = FileSystems.getDefault().getPath(gitConfig.gitRepoStorage);
      Path gitDir = gitRepoStorage.resolve(PERCENT_ESCAPER.escape(url));

      return new GitOrigin(GitRepository.bareRepo(gitDir, options), url, defaultTrackingRef);
    }
  }
}
