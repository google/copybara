// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;

import javax.annotation.Nullable;

/**
 * Gerrit repository destination.
 */
public final class GerritDestination extends AbstractGitDestination {

  private final GerritOptions gerritOptions;

  private GerritDestination(String repoUrl, String pullFromRef,
      GitOptions gitOptions, GerritOptions gerritOptions, boolean verbose) {
    super(repoUrl, pullFromRef, "refs/for/master", gitOptions, verbose);
    this.gerritOptions = Preconditions.checkNotNull(gerritOptions);
  }

  private String maybeParentHash(GitRepository repo) {
    try {
      return repo.simpleCommand("rev-parse", "HEAD^0").getStdout();
    } catch (RepoException e) {
      return "";
    }
  }

  private String changeId(GitRepository repo) throws RepoException {
    if (!Strings.isNullOrEmpty(gerritOptions.gerritChangeId)) {
      return gerritOptions.gerritChangeId;
    }

    return "I" + Hashing.sha1().newHasher()
        .putString(repo.simpleCommand("write-tree").getStdout(), Charsets.UTF_8)
        .putString(maybeParentHash(repo), Charsets.UTF_8)
        .putString(repo.simpleCommand("var", "GIT_AUTHOR_IDENT").getStdout(), Charsets.UTF_8)
        .putString(repo.simpleCommand("var", "GIT_COMMITTER_IDENT").getStdout(), Charsets.UTF_8)
        .hash();
  }

  @Override
  protected String commitMessage(GitRepository repo) throws RepoException {
    return String.format("%s\n\nChange-Id: %s\n", super.commitMessage(repo), changeId(repo));
  }

  @Nullable
  @Override
  public String getPreviousRef() throws RepoException {
    // This doesn't make sense for Gerrit since we do not plan to use previous ref for
    // pull requests.
    return null;
  }

  public static final class Yaml extends AbstractYaml {
    @Override
    public GerritDestination withOptions(Options options) {
      return new GerritDestination(url, pullFromRef,
          options.get(GitOptions.class),
          options.get(GerritOptions.class),
          options.get(GeneralOptions.class).isVerbose());
    }
  }
}
