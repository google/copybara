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

  @Override
  protected String commitMessage() throws RepoException {
    StringBuilder message = new StringBuilder(super.commitMessage());

    message.append("\n\nChange-Id: I");

    Hasher changeIdHasher = Hashing.sha1().newHasher()
        .putString(toString(), Charsets.UTF_8)
        .putLong(System.nanoTime());
    return message
        .append(changeIdHasher.hash())
        .append("\n")
        .toString();
  }

  public static final class Yaml extends AbstractYaml {
    @Override
    public GerritDestination withOptions(Options options) {
      return new GerritDestination(url, pullFromRef, options.getOption(GitOptions.class),
          options.getOption(GeneralOptions.class).isVerbose());
    }
  }
}
