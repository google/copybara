package com.google.copybara;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Origin.ReferenceFiles;
import com.google.copybara.transform.Transformation;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.copybara.util.console.Console;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * Cherry-picks a single change from the origin, calculates the diff with the baseline, and applies
 * it to the destination, as a single change.
 */
class CherrypickWorkflow<O extends Origin<O>> extends Workflow<O> {

  CherrypickWorkflow(String configName, String workflowName, Origin<O> origin,
      Destination destination, ImmutableList<Transformation> transformations,
      @Nullable String lastRevision, Console console, PathMatcherBuilder excludedOriginPaths) {
    super(configName, workflowName, origin, destination, transformations, lastRevision,
        console, excludedOriginPaths);
  }

  @Override
  public void runForRef(Path workdir, ReferenceFiles<O> resolvedRef)
      throws RepoException, IOException {
    // TODO(danielromero): Implement workflow

    getDestination().process(workdir, resolvedRef, 0L, "");
 }

}
