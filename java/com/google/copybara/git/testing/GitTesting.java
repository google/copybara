/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.git.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;

import com.google.common.collect.Iterables;
import com.google.copybara.exception.RepoException;
import com.google.copybara.git.GitRepository;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.testing.FileSubjects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;

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
   * Asserts that the author timestamp of a certain commit equals {@code timestamp}.
   */
  public static void assertAuthorTimestamp(GitRepository repo, String commitRef,
      ZonedDateTime timestamp)
      throws RepoException {
    GitLogEntry commit = Iterables.getOnlyElement(repo.log(commitRef).withLimit(1).run());
    assertThat(commit.getAuthorDate()).isEquivalentAccordingToCompareTo(timestamp);
  }

  /**
   * Asserts that the committer line of the given commit matches the given regex.
   */
  public static void assertCommitterLineMatches(
      GitRepository repo, String commitRef, String regex) throws RepoException {
    assertLineWithPrefixMatches(repo, commitRef, "committer ", regex);
  }

  /**
   * Returns a Truth subject that allows asserting about the files at a particular commit reference.
   * This method checks out the given ref in a temporary directory to allow asserting about it.
   */
  public static FileSubjects.PathSubject assertThatCheckout(GitRepository repo, String ref)
      throws IOException, RepoException {
    Path tempWorkTree = Files.createTempDirectory("assertAboutCheckout");
    repo.withWorkTree(tempWorkTree).forceCheckout(ref);
    return assertThatPath(tempWorkTree);
  }
}
