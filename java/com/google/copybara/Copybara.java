// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.Origin.Reference;
import com.google.copybara.config.Config;
import com.google.copybara.transform.Transformation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Entry point for Copybara library.
 */
class Copybara {

  private static final Logger logger = Logger.getLogger(Copybara.class.getName());
  private final Path workdir;

  public Copybara(Path workdir) {
    this.workdir = workdir;
  }

  void runForSourceRef(Config config, @Nullable String sourceRef)
      throws RepoException, IOException {
    logger.log(Level.INFO, "Running Copybara for " + config.getName()
        + " [" + config.getOrigin() + " ref:" + sourceRef + "]");
    Reference<?> resolvedRef = config.getOrigin().resolve(sourceRef);
    resolvedRef.checkout(workdir);

    for (Transformation transformation : config.getTransformations()) {
      logger.log(Level.INFO, " transforming: " + transformation.toString());
      try {
        transformation.transform(workdir);
      } catch (IOException e) {
        throw new RepoException("Error applying transformation: " + transformation, e);
      }
    }

    Long timestamp = resolvedRef.readTimestamp();
    if (timestamp == null) {
      timestamp = System.currentTimeMillis() / 1000;
    }
    config.getDestination().process(workdir, resolvedRef.asString(), timestamp);
  }
}
