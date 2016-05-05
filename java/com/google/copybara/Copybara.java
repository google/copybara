// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.config.Config;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * Entry point for Copybara library.
 */
class Copybara {

  private final Path workdir;

  public Copybara(Path workdir) {
    this.workdir = workdir;
  }

  void runForSourceRef(Config config, @Nullable String sourceRef)
      throws RepoException, IOException {
    Workflow workflow = config.getActiveWorkflow();
    workflow.run(workdir, sourceRef);
  }
}
