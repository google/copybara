// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.Origin.Reference;
import com.google.copybara.config.ConfigValidationException;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * A repository which a source of truth can be copied to.
 */
public interface Destination {

  interface Yaml {

    Destination withOptions(Options options, String configName) throws ConfigValidationException;
  }

  /**
   * Writes the fully-transformed repository stored at {@code workdir} to this destination.
   *  @param workdir directory containing the tree of files to put in destination
   * @param originRef reference to the origin revision being moved
   * @param timestamp when the code was submitted to the origin repository, expressed as seconds
 * since the UNIX epoch
   * @param changesSummary commit message to use for destination. Note that the destination can
   */
  void process(Path workdir, Reference<?> originRef, long timestamp,
      String changesSummary) throws RepoException, IOException;

  /**
   * Returns the latest origin ref that was pushed to this destination.
   *
   * <p>Returns null if the last origin ref cannot be identified or the destination doesn't support
   * this feature. This requires that the {@code Destination} stores information about the origin
   * ref.
   */
  @Nullable
  String getPreviousRef(String labelName) throws RepoException;
}
