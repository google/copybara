package com.google.copybara;

import com.google.copybara.Origin.ReferenceFiles;
import com.google.copybara.transform.Transformation;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.ProgressPrefixConsole;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * Cherry-picks a single change from the origin, calculates the diff with the baseline, and applies
 * it to the destination, as a single change.
 */
class CherrypickWorkflow<O extends Origin<O>> extends Workflow<O> {

  CherrypickWorkflow(String configName, String workflowName, Origin<O> origin,
      Destination destination, Transformation transformation,
      @Nullable String lastRevision, Console console, PathMatcherBuilder excludedOriginPaths) {
    super(configName, workflowName, origin, destination, transformation, lastRevision,
        console, excludedOriginPaths);
  }

  @Override
  public void runForRef(Path workdir, ReferenceFiles<O> cherrypickRef)
      throws RepoException, IOException, EnvironmentException, ValidationException {
    console.progress("Checking out the cherrypick ref: " + cherrypickRef.asString());
    cherrypickRef.checkout(workdir);

    // TODO(danielromero): Calculate diff with baseline and use that as workdir

    try {
      //TODO(danielromero): Prefix console with "change" and "parent"
      transformation.transform(workdir, new ProgressPrefixConsole("", console));
    } catch (IOException e) {
      throw new EnvironmentException("Error applying transformation: " + transformation, e);
    }

    Change<O> change = getOrigin().change(cherrypickRef);
    getDestination().process(workdir, cherrypickRef, getTimestamp(cherrypickRef),
        change.getMessage(), console);
 }

}
