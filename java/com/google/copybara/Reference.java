package com.google.copybara;

import java.time.Instant;
import javax.annotation.Nullable;

/**
 * A reference of {@link Origin}.
 *
 * <p>For example, in Git it would be a reference to a commit SHA-1.
 */
public interface Reference {

  /**
   * Reads the timestamp of this reference from the repository, or {@code null} if this repo type
   * does not support it. This is the {@link Instant} from the UNIX epoch when the reference was
   * submitted to the source repository.
   */
  @Nullable
  Instant readTimestamp() throws RepoException;

  /**
   * String representation of the reference that can be parsed by {@link Origin#resolve(String)}.
   *
   * <p> Unlike {@link #toString()} method, this method is guaranteed to be stable.
   */
  String asString();

  /**
   * Label name to be used in when creating a commit message in the destination to refer to a
   * reference. For example "Git-RevId".
   */
  String getLabelName();
}
