// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.config.Config;
import com.google.copybara.config.Transformation;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for Copybara library.
 */
class Copybara {

  private static final Logger logger = Logger.getLogger(Copybara.class.getName());

  void runForSourceRef(Config config, String sourceRef) {
    logger.log(Level.INFO, "Running Copybara for " + config.getName()
        + " [" + config.getRepository() + " ref:" + sourceRef + "]");
    for (Transformation transformation : config.getTransformations()) {
      logger.log(Level.INFO, " transforming: " + transformation.toString());
    }
  }
}
