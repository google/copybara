package com.google.copybara;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Origin.ReferenceFiles;
import com.google.copybara.transform.Transformation;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.copybara.util.console.Console;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.annotation.Nullable;

/**
 * For each pending change in the origin, runs the transformations and sends to the destination
 * individually.
 */
class IterativeWorkflow<O extends Origin<O>> extends Workflow<O> {

  IterativeWorkflow(String configName, String workflowName, Origin<O> origin,
      Destination destination, ImmutableList<Transformation> transformations,
      @Nullable String lastRevisionFlag, Console console, PathMatcherBuilder excludedOriginPaths) {
    super(configName, workflowName, origin, destination, transformations, lastRevisionFlag,
        console, excludedOriginPaths);
  }

  @Override
  public void runForRef(Path workdir, ReferenceFiles<O> to)
      throws RepoException, IOException {
    ImmutableList<Change<O>> changes = getOrigin().changes(getLastRev(), to);
    for (int i = 0; i < changes.size(); i++) {
      Change<O> change = changes.get(i);
      String prefix = String.format(
          "[%2d/%d] Migrating change %s: ", i + 1, changes.size(),
          change.getReference().asString());
      ReferenceFiles<O> ref = change.getReference();
      logger.log(Level.INFO, String.format("%s %s", prefix, ref.asString()));
      console.progress(prefix + "Cleaning working directory");
      FileUtil.deleteAllFilesRecursively(workdir);
      console.progress(prefix + "Checking out the change");
      ref.checkout(workdir);
      removeExcludedFiles(workdir);
      runTransformations(workdir, prefix);

      String message = change.getMessage();
      if (!message.endsWith("\n")) {
        message += "\n";
      }
      // TODO(malcon): Show the prefix on destination
      getDestination().process(workdir, ref, getTimestamp(ref), message);
    }
  }
}
