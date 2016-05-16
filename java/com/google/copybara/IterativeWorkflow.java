package com.google.copybara;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.Origin.Reference;
import com.google.copybara.Origin.ReferenceFiles;
import com.google.copybara.transform.Transformation;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.copybara.util.console.Console;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.annotation.Nullable;

/**
 * For each pending change in the origin, runs the transformations and sends to the destination
 * individually.
 */
class IterativeWorkflow<O extends Origin<O>> extends Workflow<O> {

  private final String configName;
  @Nullable
  private final String lastRevision;

  IterativeWorkflow(String configName, String workflowName, Origin<O> origin,
      Destination destination,
      ImmutableList<Transformation> transformations, @Nullable String lastRevision,
      Console console, PathMatcherBuilder excludedOriginPaths) {
    super(workflowName, origin, destination, transformations, console, excludedOriginPaths);
    this.configName = Preconditions.checkNotNull(configName);
    this.lastRevision = lastRevision;
  }

  @Override
  public void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException {
    console.progress("Getting last revision");
    Reference<O> from = getLastRevision();
    console.progress("Resolving " + ((sourceRef == null) ? "origin reference" : sourceRef));
    Reference<O> to = getOrigin().resolve(sourceRef);

    ImmutableList<Change<O>> changes = getOrigin().changes(from, to);
    logger.log(Level.INFO, String.format("Running Copybara for config '%s', workflow '%s' (%s)",
        configName, getName(), WorkflowMode.ITERATIVE));
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

      Long timestamp = ref.readTimestamp();
      if (timestamp == null) {
        timestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
      }
      String message = change.getMessage();
      if (!message.endsWith("\n")) {
        message += "\n";
      }
      // TODO(malcon): Show the prefix on destination
      getDestination().process(workdir, ref, timestamp, message);
    }
  }

  private Reference<O> getLastRevision() throws RepoException {
    if (lastRevision != null) {
      return getOrigin().resolve(lastRevision);
    }
    String labelName = getOrigin().getLabelName();
    String previousRef = getDestination().getPreviousRef(labelName);
    if (previousRef == null) {
      throw new RepoException(String.format(
          "Previous revision label %s could not be found in %s and --last_revision flag"
              + " was not passed", labelName, getDestination()));
    }
    return getOrigin().resolve(previousRef);
  }
}
