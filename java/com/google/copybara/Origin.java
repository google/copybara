package com.google.copybara;

import javax.annotation.Nullable;

import java.nio.file.Path;

/**
 * A {@code Origin} represents a source control repository from which source is copied.
 */
public interface Origin {

  interface Yaml {

    Origin withOptions(Options options);
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
