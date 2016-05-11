package com.google.copybara;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Origin.Reference;
import com.google.copybara.transform.Transformation;
import com.google.copybara.util.console.Console;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;

import javax.annotation.Nullable;

/**
 * Migrates all pending changes as one change.
 */
public class SquashWorkflow extends Workflow {

  private final String configName;

  SquashWorkflow(String configName, String workflowName, Origin<?> origin, Destination destination,
      ImmutableList<Transformation> transformations, Console console) {
    super(workflowName, origin, destination, transformations, console);
    this.configName = configName;
  }

  @Override
  public void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException {
    console.progress("Resolving " + ((sourceRef == null) ? "origin reference" : sourceRef));
    Reference<?> resolvedRef = getOrigin().resolve(sourceRef);
    logger.log(Level.INFO,
        "Running Copybara for config '" + configName
            + "', workflow '" + getName()
            + "' (" + WorkflowMode.SQUASH + ")"
            + " and ref '" + resolvedRef.asString() + "': " + this.toString());
    resolvedRef.checkout(workdir);

    runTransformations(workdir);

    Long timestamp = resolvedRef.readTimestamp();
    if (timestamp == null) {
      timestamp = System.currentTimeMillis() / 1000;
    }
    getDestination().process(workdir, resolvedRef.asString(), timestamp, "Copybara commit\n");
  }
}
