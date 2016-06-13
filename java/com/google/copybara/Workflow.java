// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.Origin.ReferenceFiles;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.transform.Sequence;
import com.google.copybara.transform.Transformation;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.copybara.util.console.Console;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Represents a particular migration operation that can occur for a project. Each project can have
 * multiple workflows. Each workflow has a particular origin and destination.
 */
public abstract class Workflow<O extends Origin<O>> {

  protected final Logger logger = Logger.getLogger(this.getClass().getName());

  private final String configName;
  private final String name;
  private final Origin<O> origin;
  private final Destination destination;
  protected final Transformation transformation;
  private final PathMatcherBuilder excludedOriginPaths;
  @Nullable
  final String lastRevisionFlag;
  final Console console;

  Workflow(String configName, String name, Origin<O> origin, Destination destination,
      Transformation transformation, @Nullable String lastRevisionFlag,
      Console console, PathMatcherBuilder excludedOriginPaths) {
    this.configName = Preconditions.checkNotNull(configName);
    this.name = Preconditions.checkNotNull(name);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.transformation = Preconditions.checkNotNull(transformation);
    this.lastRevisionFlag = lastRevisionFlag;
    this.console = Preconditions.checkNotNull(console);
    this.excludedOriginPaths = excludedOriginPaths;
  }

  @VisibleForTesting
  String getName() {
    return name;
  }

  /**
   * The repository that represents the source of truth
   */
  public Origin<O> getOrigin() {
    return origin;
  }

  /**
   * The destination repository to copy to.
   */
  public Destination getDestination() {
    return destination;
  }

  /**
   * Transformation to run before writing them to the destination.
   */
  public Transformation getTransformation() {
    return transformation;
  }

  public final void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException, EnvironmentException, ValidationException {
    console.progress("Cleaning working directory");
    FileUtil.deleteAllFilesRecursively(workdir);

    console.progress("Getting last revision: "
        + "Resolving " + ((sourceRef == null) ? "origin reference" : sourceRef));
    ReferenceFiles<O> resolvedRef = getOrigin().resolve(sourceRef);
    logger.log(Level.INFO,
        String.format(
            "Running Copybara for config '%s', workflow '%s' (%s) and ref '%s': %s",
            configName, name, this.getClass().getSimpleName(), resolvedRef.asString(),
            this.toString()));
    logger.log(Level.INFO, String.format("Using working directory : %s", workdir));
    runForRef(workdir, resolvedRef);
  }

  abstract void runForRef(Path workdir, ReferenceFiles<O> resolvedRef)
      throws RepoException, IOException, EnvironmentException, ValidationException;
  /**
   * Runs the transformation for the workflow
   *
   * @param workdir working directory to use for the transformations
   * @param console console to use for printing messages
   */
  protected void transform(Path workdir, Console console)
      throws ValidationException, EnvironmentException {
    try {
      transformation.transform(workdir, console);
    } catch (IOException e) {
      throw new EnvironmentException("Error applying transformation: " + transformation, e);
    }
  }

  void removeExcludedFiles(Path workdir) throws IOException, RepoException {
    if (excludedOriginPaths.isEmpty()) {
      return;
    }
    PathMatcher pathMatcher = excludedOriginPaths.relativeTo(workdir);
    console.progress("Removing excluded files");

    int result = FileUtil.deleteFilesRecursively(workdir, pathMatcher);
    logger.log(Level.INFO,
        String.format("Removed %s files from workdir that were excluded", result));

    if (result == 0) {
      throw new RepoException(
          String.format("Nothing was deleted in the workdir for excludedOriginPaths: '%s'",
              pathMatcher));
    }
  }

  /**
   * Returns the last revision that was imported from this origin to the destination.
   *
   * <p>If {@code --last-rev} is specified, that revision will be used. Otherwise, the previous
   * reference will be resolved in the destination with the origin label.
   */
  ReferenceFiles<O> getLastRev() throws RepoException {
    if (lastRevisionFlag != null) {
      return getOrigin().resolve(lastRevisionFlag);
    }
    String labelName = getOrigin().getLabelName();
    String previousRef = getDestination().getPreviousRef(labelName);
    if (previousRef == null) {
      throw new RepoException(String.format(
          "Previous revision label %s could not be found in %s and --last-rev flag"
              + " was not passed", labelName, getDestination()));
    }
    return getOrigin().resolve(previousRef);
  }

  /**
   * Returns the timestamp of {@code ref}, if present. Otherwise returns the current time.
   */
  long getTimestamp(ReferenceFiles<O> ref) throws RepoException {
    Long timestamp = ref.readTimestamp();
    if (timestamp == null) {
      timestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }
    return timestamp;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("configName", configName)
        .add("name", name)
        .add("origin", origin)
        .add("destination", destination)
        .add("transformation", transformation)
        .add("excludedOriginPaths", excludedOriginPaths)
        .toString();
  }

  protected final String getConfigName() {
    return configName;
  }

  /**
   * Config builder used by YAML.
   */
  @DocElement(yamlName = "!Workflow",
      description = "Defines a migration pipeline which can be invoked via the Copybara command.",
      elementKind = Workflow.class)
  public static final class Yaml {
    private String name = "default";
    private Origin.Yaml<?> origin;
    private Destination.Yaml destination;
    private WorkflowMode mode = WorkflowMode.SQUASH;
    private boolean includeChangeListNotes = false;
    private ImmutableList<Transformation.Yaml> transformations = ImmutableList.of();
    private List<String> excludedOriginPaths = new ArrayList<>();

    public String getName() {
      return name;
    }

    @DocField(description = "The name of the workflow.", required = false, defaultValue = "default")
    public void setName(String name) {
      this.name = name;
    }

    @DocField(description = "Where to read the migration code from.", required = true)
    public void setOrigin(Origin.Yaml<?> origin) {
      this.origin = origin;
    }

    @DocField(description = "Where to write the migration code to after transforms.",
        required = true)
    public void setDestination(Destination.Yaml destination) {
      this.destination = destination;
    }

    @DocField(description = "Transformations to run on the migration code.",
        required = false)
    public void setTransformations(List<? extends Transformation.Yaml> transformations)
        throws ConfigValidationException {
      this.transformations = ImmutableList.copyOf(transformations);
    }

    @DocField(description = "An list of expressions representing globs of paths relative to the workdir that will be excluded from the origin during the import. For example \"**.java\", all java files, recursively.", required = false, defaultValue = "[]")
    public void setExcludedOriginPaths(List<String> excludedOriginPaths) {
      this.excludedOriginPaths = excludedOriginPaths;
    }

    @DocField(description = "Include a list of change list messages that were imported",
        required = false, defaultValue = "false")
    public void setIncludeChangeListNotes(boolean includeChangeListNotes) {
      this.includeChangeListNotes = includeChangeListNotes;
    }

    @DocField(description = "Import/export mode of the changes. For example if the changes should be imported by squashing all the pending changes or imported individually",
        required = false, defaultValue = "SQUASH")
    public void setMode(WorkflowMode mode) {
      this.mode = mode;
    }

    public Workflow withOptions(Options options, String configName)
        throws ConfigValidationException, EnvironmentException {

      Sequence.Yaml sequence = new Sequence.Yaml();
      sequence.setTransformations(this.transformations);
      Transformation transformation = sequence.withOptions(options);

      Origin<?> origin = this.origin.withOptions(options);
      Destination destination = this.destination.withOptions(options, configName);
      Console console = options.get(GeneralOptions.class).console();
      GeneralOptions generalOptions = options.get(GeneralOptions.class);
      PathMatcherBuilder excludedOriginPaths = PathMatcherBuilder.create(
          FileSystems.getDefault(), this.excludedOriginPaths);
      switch (mode) {
        case SQUASH:
          return new SquashWorkflow<>(configName, name, origin, destination, transformation,
              console, generalOptions.getLastRevision(), includeChangeListNotes,
              excludedOriginPaths);
        case ITERATIVE:
          return new IterativeWorkflow<>(configName, name, origin, destination, transformation,
              generalOptions.getLastRevision(), console, excludedOriginPaths);
        default:
          throw new UnsupportedOperationException(mode + " still not implemented");
      }
    }
  }
}
