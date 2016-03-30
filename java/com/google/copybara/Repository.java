package com.google.copybara;

import com.beust.jcommander.internal.Nullable;

import java.nio.file.Path;

/**
 * A {@code Repository} represents a source control repository
 */
public interface Repository {

  interface Yaml {

    Repository withOptions(Options options);
  }

  /**
   * Checks out {@code reference} from the repository into {@code workdir} directory.
   *
   * <p> If reference is null or empty then it will try to use the configured default for the
   * repository.
   *
   * @throws RepoException if any error happens during the checkout or workdir preparation.
   */
  void checkoutReference(@Nullable String reference, Path workdir) throws RepoException;
}
