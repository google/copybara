// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.Charsets;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;

/**
 * Gerrit repository destination.
 */
public final class GerritDestination extends AbstractGitDestination {
  private GerritDestination(String repoUrl, String pullFromRef,
      GitOptions gitOptions, boolean verbose) {
    super(repoUrl, pullFromRef, "refs/for/master", gitOptions, verbose);
  }

  private String maybeParentHash(GitRepository repo) {
    try {
      return repo.simpleCommand("rev-parse", "HEAD^0").getStdout();
    } catch (RepoException e) {
      return "";
    }
  }

  @Override
  protected String commitMessage(GitRepository repo) throws RepoException {
    StringBuilder message = new StringBuilder(super.commitMessage(repo));

    message.append("\n\nChange-Id: I");

    Hasher changeIdHasher = Hashing.sha1().newHasher()
        .putString(repo.simpleCommand("write-tree").getStdout(), Charsets.UTF_8)
        .putString(maybeParentHash(repo), Charsets.UTF_8)
        .putString(repo.simpleCommand("var", "GIT_AUTHOR_IDENT").getStdout(), Charsets.UTF_8)
        .putString(repo.simpleCommand("var", "GIT_COMMITTER_IDENT").getStdout(), Charsets.UTF_8);
    return message
        .append(changeIdHasher.hash())
        .append("\n")
        .toString();
  }

  public static final class Yaml extends AbstractYaml {
    @Override
    public GerritDestination withOptions(Options options) {
      return new GerritDestination(url, pullFromRef, options.get(GitOptions.class),
          options.get(GeneralOptions.class).isVerbose());
    }
  }
}
