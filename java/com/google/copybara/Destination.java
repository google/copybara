// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * A repository which a source of truth can be copied to.
 */
public interface Destination {

  interface Yaml {

    Destination withOptions(Options options);
  }

  /**
   * Writes the fully-transformed repository stored at {@code workdir} to this destination.
   *
   * @param workdir directory containing the tree of files to put in destination
   * @param originRef reference to the origin revision being moved
   */
  void process(Path workdir, String originRef) throws RepoException, IOException;

  /**
   * Returns the latest origin ref that was pushed to this destination.
   *
   * <p>Returns null if the last origin ref cannot be identified or the destination doesn't support
   * this feature. This requires that the {@code Destination} stores information about the origin ref.
   */
  @Nullable
  String getPreviousRef() throws RepoException;
}
