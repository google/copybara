// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.Origin.Reference;
import com.google.copybara.config.Config;
import com.google.copybara.transform.Transformation;
import com.google.copybara.util.console.Console;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Entry point for Copybara library.
 */
class Copybara {

  private static final Logger logger = Logger.getLogger(Copybara.class.getName());
  private final Path workdir;
  private final Console console;

  public Copybara(Path workdir, Console console) {
    this.workdir = workdir;
    this.console = console;
  }

  void runForSourceRef(Config config, @Nullable String sourceRef)
      throws RepoException, IOException {
    Workflow workflow = config.getActiveWorkflow();
    logger.log(Level.INFO, "Running Copybara for " + config.getName() + " " + workflow);
    Reference<?> resolvedRef = workflow.getOrigin().resolve(sourceRef);
    resolvedRef.checkout(workdir);

    List<Transformation> transformations = workflow.getTransformations();
    for (int i = 0; i < transformations.size(); i++) {
      Transformation transformation = transformations.get(i);
      String transformMsg = String.format(
          "[%2d/%d] Transformation %s", i + 1, transformations.size(), transformation);
      logger.log(Level.INFO, transformMsg);

      console.progress(transformMsg);
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
    workflow.getDestination().process(workdir, resolvedRef.asString(), timestamp,
        "Copybara commit\n");
  }
}
