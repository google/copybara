package com.google.copybara.git;

import com.google.common.base.Preconditions;
import com.google.copybara.Origin.Reference;
import com.google.copybara.RepoException;

import java.util.regex.Pattern;

/**
 * A Git repository reference
 */
public final class GitReference implements Reference {

  private static final Pattern COMPLETE_SHA1_PATTERN = Pattern.compile("[a-f0-9]{40}");

  private final GitRepository repository;
  private final String reference;

  /**
   * Create a git reference from a complete (40 characters) git SHA-1 string.
   *
   * @param repository git repository that should contain the {@code reference}
   * @param reference a 40 characters SHA-1
   */
  GitReference(GitRepository repository, String reference) {
    Preconditions.checkArgument(COMPLETE_SHA1_PATTERN.matcher(reference).matches(),
        "Reference '%s' is not a 40 characters SHA-1", reference);

    this.repository = repository;
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

  @Override
  public String asString() {
    return reference;
  }

  @Override
  public String getLabelName() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  @Override
  public String toString() {
    return reference;
  }
}
