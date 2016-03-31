// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.config.Config;
import com.google.copybara.config.Transformation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for Copybara library.
 */
class Copybara {

  private static final Logger logger = Logger.getLogger(Copybara.class.getName());
  private final Path workdir;

  public Copybara(Path workdir) {
    this.workdir = workdir;
  }

  void runForSourceRef(Config config, String sourceRef) throws RepoException {
    logger.log(Level.INFO, "Running Copybara for " + config.getName()
        + " [" + config.getSourceOfTruth() + " ref:" + sourceRef + "]");

    config.getSourceOfTruth().checkoutReference(sourceRef, workdir);

    for (Transformation transformation : config.getTransformations()) {
      logger.log(Level.INFO, " transforming: " + transformation.toString());
      try {
        transformation.transform(workdir);
      } catch (IOException e) {
        throw new RepoException("Error applying transformation: " + transformation, e);
      }
    }
  }
}
