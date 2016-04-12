package com.google.copybara;

import com.google.common.collect.ImmutableList;

import java.nio.file.Path;

import javax.annotation.Nullable;

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

  /**
   * Returns the changes that happen in the interval (previousRef, reference].
   *
   * @param previousRef the reference used in the latest invocation. If null it means that no
   * previous ref could be found or that the destination didn't store the ref.
   * @param reference current reference to transform. If null or empty then it will try to use the
   * configured default for the repository.
   * @throws CannotComputeChangesException if the change list cannot be computed.
   * @throws RepoException if any error happens during the computation of the diff.
   */
  ImmutableList<Change> changes(@Nullable String previousRef, @Nullable String reference)
      throws RepoException;
}
