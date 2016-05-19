package com.google.copybara;

import com.google.common.collect.ImmutableList;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.git.CannotFindReferenceException;

import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * A {@code Origin} represents a source control repository from which source is copied.
 */
public interface Origin<O extends Origin<O>> {

  interface Yaml<O extends Origin<O>> {

    Origin<O> withOptions(Options options) throws ConfigValidationException;
  }

  /**
   * A reference of Origin. For example in Git it would be a referenc to a commit SHA-1.
   *
   * @param <O> the origin type of the reference
   */
  interface Reference<O extends Origin<O>> {

    /**
     * Reads the timestamp of this reference from the repository, or {@code null} if this repo type
     * does not support it. This is the number of seconds from the UNIX epoch when the reference was
     * submitted to the source repository.
     */
    @Nullable
    Long readTimestamp() throws RepoException;

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

  /**
   * A reference of origin that allows checkouts.
   *
   * @param <O> the origin type of the reference
   */
  interface ReferenceFiles<O extends Origin<O>> extends Reference<O> {

    /**
     * Checks out the reference from the repository into {@code workdir} directory.
     *
     * @throws RepoException if any error happens during the checkout or workdir preparation.
     */
    void checkout(Path workdir) throws RepoException;
  }

  /**
   * Resolves a reference using the {@code Origin} configuration and flags

   * <p> If reference is null or empty then it will try to use the configured default for the
   * Origin.
   * @throws RepoException if any error happens during the resolve.
   */
  ReferenceFiles<O> resolve(@Nullable String reference) throws RepoException;

  /**
   * Returns the changes that happen in the interval (fromRef, toRef].
   *
   * <p>If {@code fromRef} is null, returns all the changes from the first commit of the parent
   * branch to {@code toRef}, both included.
   *
   * @param fromRef the reference used in the latest invocation. If null it means that no
   * previous ref could be found or that the destination didn't store the ref.
   * @param toRef current reference to transform.
   * @throws CannotComputeChangesException if the change list cannot be computed.
   * @throws RepoException if any error happens during the computation of the diff.
   */
  ImmutableList<Change<O>> changes(@Nullable Reference<O> fromRef, Reference<O> toRef)
      throws RepoException;

  /**
   * Returns a change identified by {@code ref}.
   *
   * @param ref current reference to transform.
   * @throws CannotFindReferenceException if the ref is invalid.
   * @throws RepoException if any error happens during the computation of the diff.
   */
  Change<O> change(Reference<O> ref) throws RepoException;

  /**
   * Label name to be used in when creating a commit message in the destination to refer to a
   * reference. For example "Git-RevId".
   */
  String getLabelName();
}
