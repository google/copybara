// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.RepoException;
import com.google.copybara.git.GitRepository;

import java.nio.file.Path;

/**
 * Utility methods for testing related to Git.
 */
public class GitTesting {
  private GitTesting() {}

  private static void assertLineWithPrefixMatches(
      GitRepository repo, String commitRef, String linePrefix, String lineRegex)
      throws RepoException {
    String[] commitLines = repo.simpleCommand("cat-file", "-p", commitRef).getStdout().split("\n");
    int matchingPrefixCount = 0;

    for (String line : commitLines) {
      if (line.startsWith(linePrefix)) {
        assertThat(line).matches(linePrefix + lineRegex);
        matchingPrefixCount++;
      }
    }

    assertThat(matchingPrefixCount).isEqualTo(1);
  }

  /**
   * Asserts that the author timestamp of a certain commit equals {@code timestamp} (seconds since
   * UNIX epoch).
   */
  public static void assertAuthorTimestamp(GitRepository repo, String commitRef, long timestamp)
      throws RepoException {
    assertLineWithPrefixMatches(
        repo, commitRef, "author ", String.format(".* %d [+]0000", timestamp));
  }

  /**
   * Asserts that the committer line of the given commit matches the given regex.
   */
  public static void assertCommitterLineMatches(
      GitRepository repo, String commitRef, String regex) throws RepoException {
    assertLineWithPrefixMatches(repo, commitRef, "committer ", regex);
  }
}
