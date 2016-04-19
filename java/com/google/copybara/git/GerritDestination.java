// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;

import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * Gerrit repository destination.
 */
public final class GerritDestination implements Destination {

  private static final class CommitGenerator implements GitDestination.CommitGenerator {

    private final GerritOptions gerritOptions;

    CommitGenerator(GerritOptions gerritOptions) {
      this.gerritOptions = Preconditions.checkNotNull(gerritOptions);
    }

    /**
     * Generates a message with a trailing Gerrit change id in the form:
     *
     * <pre>
     * Change-Id: I{SHA1 hash}
     * </pre>
     *
     * Where the hash is generated from the data in the current tree and other data, including the
     * values of the git variables {@code GIT_AUTHOR_IDENT} and {@code GIT_COMMITTER_IDENT}.
     */
    @Override
    public String message(GitRepository repo, String originRef) throws RepoException {
      return String.format("Copybara commit\n\n%s: %s\nChange-Id: %s\n",
          Origin.COMMIT_ORIGIN_REFERENCE_FIELD,
          originRef,
          changeId(repo)
      );
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

  }

  private final GitDestination gitDestination;

  private GerritDestination(GitDestination gitDestination) {
    this.gitDestination = Preconditions.checkNotNull(gitDestination);
  }

  @Override
  public void process(Path workdir, String originRef) throws RepoException {
    gitDestination.process(workdir, originRef);
  }

  @Nullable
  @Override
  public String getPreviousRef() throws RepoException {
    // This doesn't make sense for Gerrit since we do not plan to use previous ref for
    // pull requests.
    return null;
  }

  public static final class Yaml extends GitDestination.AbstractYaml {
    @Override
    public GerritDestination withOptions(Options options) {
      return new GerritDestination(
          new GitDestination(
              url, pullFromRef, "refs/for/master",
              options.get(GitOptions.class),
              options.get(GeneralOptions.class).isVerbose(),
              new CommitGenerator(options.get(GerritOptions.class))));
    }
  }
}
