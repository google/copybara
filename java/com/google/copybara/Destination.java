// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.util.console.Console;

import java.io.IOException;

import javax.annotation.Nullable;

/**
 * A repository which a source of truth can be copied to.
 */
public interface Destination {

  interface Yaml {

    Destination withOptions(Options options, String configName, boolean askConfirmation)
        throws ConfigValidationException;
  }

  /**
   * Writes the fully-transformed repository stored at {@code workdir} to this destination.
   * @param transformResult what to write to the destination
   * @param console console to be used for printing messages
   */
  void process(TransformResult transformResult, Console console) throws RepoException, IOException;

  /**
   * Returns the latest origin ref that was pushed to this destination.
   *
   * <p>Returns null if the last origin ref cannot be identified or the destination doesn't support
   * this feature. This requires that the {@code Destination} stores information about the origin
   * ref.
   */
  @Nullable
  String getPreviousRef(String labelName) throws RepoException;

  /**
   * Given a reverse workflow with an {@code Origin} than is of the same type as this destination,
   * the label that that {@link Origin#getLabelName()} would return.
   *
   * <p>This label name is used by the origin in the reverse workflow to stamp it's original
   * revision id. Destinations return the origin label so that a baseline label can be found when
   * using {@link WorkflowMode#CHANGE_REQUEST}.
   */
  String getLabelNameWhenOrigin();
}
