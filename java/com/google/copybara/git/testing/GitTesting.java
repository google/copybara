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

  /**
   * Asserts that the author timestamp of a certain commit equals {@code timestamp} (seconds since
   * UNIX epoch).
   */
  public static void assertAuthorTimestamp(GitRepository repo, String commitRef, long timestamp)
      throws RepoException {
    String[] commitLines = repo.simpleCommand("cat-file", "-p", commitRef).getStdout().split("\n");
    int authorLineCount = 0;

    for (String line : commitLines) {
      if (line.startsWith("author ")) {
        assertThat(line)
            .matches(String.format("author .* %d [+]0000", timestamp));
        authorLineCount++;
      }
    }

    assertThat(authorLineCount).isEqualTo(1);
  }
}
