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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Represents a particular migration operation that can occur for a project. Each project can have
 * multiple workflows. Each workflow has a particular origin and destination.
 */
public final class Workflow<O extends Origin<O>> {

  private final Logger logger = Logger.getLogger(this.getClass().getName());

  private final String configName;
  private final String name;
  private final Origin<O> origin;
  private final Destination destination;
  private final Authoring authoring;
  private final Transformation transformation;
  private final PathMatcherBuilder excludedOriginPaths;
  private final PathMatcherBuilder excludedDestinationPaths;
  @Nullable
  private final String lastRevisionFlag;
  private final Console console;
  private final WorkflowMode mode;
  private final boolean includeChangeListNotes;
  private final WorkflowOptions workflowOptions;

  private Workflow(String configName, String name, Origin<O> origin, Destination destination,
      Authoring authoring, Transformation transformation, @Nullable String lastRevisionFlag,
      Console console, PathMatcherBuilder excludedOriginPaths,
      PathMatcherBuilder excludedDestinationPaths, WorkflowMode mode,
      boolean includeChangeListNotes, WorkflowOptions workflowOptions) {
    this.configName = Preconditions.checkNotNull(configName);
    this.name = Preconditions.checkNotNull(name);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.authoring = Preconditions.checkNotNull(authoring);
    this.transformation = Preconditions.checkNotNull(transformation);
    this.lastRevisionFlag = lastRevisionFlag;
    this.console = Preconditions.checkNotNull(console);
    this.excludedOriginPaths = excludedOriginPaths;
    this.excludedDestinationPaths = Preconditions.checkNotNull(excludedDestinationPaths);
    this.mode = Preconditions.checkNotNull(mode);
    this.includeChangeListNotes = includeChangeListNotes;
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
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

  public void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException, EnvironmentException, ValidationException {
    console.progress("Cleaning working directory");
    FileUtil.deleteAllFilesRecursively(workdir);

    console.progress("Getting last revision: "
        + "Resolving " + ((sourceRef == null) ? "origin reference" : sourceRef));
    ReferenceFiles<O> resolvedRef = origin.resolve(sourceRef);
    logger.log(Level.INFO,
        String.format(
            "Running Copybara for config '%s', workflow '%s' and ref '%s': %s",
            configName, name, resolvedRef.asString(),
            this.toString()));
    logger.log(Level.INFO, String.format("Using working directory : %s", workdir));
    mode.run(new RunHelper(workdir, resolvedRef));
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
        .add("excludedDestinationPaths", excludedDestinationPaths)
        .add("mode", mode)
        .toString();
  }

  final class RunHelper {
    private final Path workdir;
    private final ReferenceFiles<O> resolvedRef;

    /**
     * @param workdir working directory to use for the transformations
     * @param resolvedRef reference to migrate
     */
    RunHelper(Path workdir, ReferenceFiles<O> resolvedRef) {
      this.workdir = Preconditions.checkNotNull(workdir);
      this.resolvedRef = Preconditions.checkNotNull(resolvedRef);
    }

    ReferenceFiles<O> getResolvedRef() {
      return resolvedRef;
    }

    /**
     * Authoring configuration.
     */
    Authoring getAuthoring() {
      return authoring;
    }

    /** Console to use for printing messages. */
    Console getConsole() {
      return console;
    }

    /**
     * Options that change how workflows behave.
     */
    WorkflowOptions workflowOptions() {
      return workflowOptions;
    }

    Destination getDestination() {
      return destination;
    }

    Origin<O> getOrigin() {
      return origin;
    }

    /**
     * Performs a full migration, including checking out files from the origin, deleting excluded
     * files, transforming the code, and writing to the destination. This writes to the destination
     * exactly once.
     *
     * @param ref reference to the version which will be written to the destination
     * @param author the author that the destination change will be attributed to
     * @param processConsole console to use to print progress messages
     * @param message change message to write to the destination
     */
    void migrate(ReferenceFiles<O> ref, Author author, Console processConsole, String message)
        throws EnvironmentException, IOException, RepoException, ValidationException {
      migrate(ref, author, processConsole, message, /*destinationBaseline=*/ null);
    }

    void migrate(ReferenceFiles<O> ref, Author author, Console processConsole, String message,
        @Nullable String destinationBaseline)
        throws EnvironmentException, IOException, RepoException, ValidationException {
      processConsole.progress("Cleaning working directory");
      FileUtil.deleteAllFilesRecursively(workdir);

      processConsole.progress("Checking out the change");
      ref.checkout(workdir);

      // Remove excluded origin files.
      if (!excludedOriginPaths.isEmpty()) {
        PathMatcher pathMatcher = excludedOriginPaths.relativeTo(workdir);
        processConsole.progress("Removing excluded origin files");

        int result = FileUtil.deleteFilesRecursively(workdir, pathMatcher);
        logger.log(Level.INFO,
            String.format("Removed %s files from workdir that were excluded", result));

        if (result == 0) {
          throw new RepoException(
              String.format("Nothing was deleted in the workdir for excludedOriginPaths: '%s'",
                  pathMatcher));
        }
      }

      // Runs the transformation for the workflow
      try {
        transformation.transform(workdir, processConsole);
      } catch (IOException e) {
        throw new EnvironmentException("Error applying transformation: " + transformation, e);
      }

      TransformResult transformResult = new TransformResult(workdir, ref, author, message,
          excludedDestinationPaths);
      if (destinationBaseline != null) {
        transformResult = transformResult.withBaseline(destinationBaseline);
      }
      destination.process(transformResult, processConsole);
    }

    /**
     * Creates a commit message to correspond to an import of any number of changes in the origin.
     */
    String changesSummaryMessage() throws RepoException {
      return String.format(
          "Imports '%s'.\n\n"
              + "This change was generated by Copybara (go/copybara).\n%s\n",
          configName,
          getChangeListNotes());
    }

    ImmutableList<Change<O>> changesSinceLastImport() throws RepoException {
      ReferenceFiles<O> lastRev = getLastRev();
      if (lastRev == null) {
        throw new RepoException(String.format(
                "Previous revision label %s could not be found in %s and --last-rev flag"
                + " was not passed", origin.getLabelName(), destination));
      }
      return origin.changes(getLastRev(), resolvedRef);
    }

    /**
     * Returns the last revision that was imported from this origin to the destination. Returns
     * {@code null} if it cannot be determined.
     *
     * <p>If {@code --last-rev} is specified, that revision will be used. Otherwise, the previous
     * reference will be resolved in the destination with the origin label.
     */
    @Nullable private ReferenceFiles<O> getLastRev() throws RepoException {
      if (lastRevisionFlag != null) {
        try {
          return origin.resolve(lastRevisionFlag);
        } catch (RepoException e) {
          throw new RepoException(
              "Could not resolve --last-rev flag. Please make sure it exists in the origin: "
                  + lastRevisionFlag,
              e);
        }
      }

      String previousRef = destination.getPreviousRef(origin.getLabelName());
      return (previousRef == null) ? null : origin.resolve(previousRef);
    }

    private String getChangeListNotes() throws RepoException {
      if (!includeChangeListNotes) {
        return "";
      }
      ReferenceFiles<O> lastRev = getLastRev();
      if (lastRev == null) {
        logger.log(Level.WARNING, "Previous reference couldn't be resolved");
        return "(List of included changes could not be computed)\n";
      }

      StringBuilder result = new StringBuilder("List of included changes:\n");
      for (Change<O> change : origin.changes(lastRev, resolvedRef)) {
        result.append(String.format("  - %s %s by %s\n",
            change.getReference().asString(),
            change.firstLineMessage(),
            change.getAuthor()));
      }

      return result.toString();
    }
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
    private List<String> excludedDestinationPaths = new ArrayList<>();
    private Authoring.Yaml authoring;

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

    @DocField(
        description = "A list of file globs relative to the workdir that will be excluded from the"
        + " origin during the import. For example \"**.java\", all java files, recursively.",
        required = false,
        defaultValue = "[]")
    public void setExcludedOriginPaths(List<String> excludedOriginPaths) {
      this.excludedOriginPaths.clear();
      this.excludedOriginPaths.addAll(excludedOriginPaths);
    }

    @DocField(
        description = "A list of file globs relative to the root of the destination repository that"
        + " will not be removed even if the file does not exist in the source. For example"
        + " '**/BUILD', all BUILD files, recursively.",
        required = false,
        defaultValue = "[]")
    public void setExcludedDestinationPaths(List<String> excludedDestinationPaths) {
      this.excludedDestinationPaths.clear();
      this.excludedDestinationPaths.addAll(excludedDestinationPaths);
    }

    @DocField(description = "Include a list of change list messages that were imported",
        required = false, defaultValue = "false")
    public void setIncludeChangeListNotes(boolean includeChangeListNotes) {
      this.includeChangeListNotes = includeChangeListNotes;
    }

    @DocField(description = "Import/export mode of the changes. For example if the changes should "
        + "be imported by squashing all the pending changes or imported individually",
        required = false, defaultValue = "SQUASH")
    public void setMode(WorkflowMode mode) {
      this.mode = mode;
    }

    @DocField(description = "The author mapping configuration from origin to destination",
        required = true)
    public void setAuthoring(Authoring.Yaml authoring) throws ConfigValidationException {
        this.authoring = authoring;
    }

    public Workflow withOptions(Options options, String configName)
        throws ConfigValidationException, EnvironmentException {

      Sequence.Yaml sequence = new Sequence.Yaml();
      sequence.setTransformations(this.transformations);
      Transformation transformation = sequence.withOptions(options);

      Authoring authoring = this.authoring.withOptions(options, configName);
      Origin<?> origin = this.origin.withOptions(options, authoring);
      Destination destination = this.destination.withOptions(options, configName);
      Console console = options.get(GeneralOptions.class).console();
      WorkflowOptions workflowOptions = options.get(WorkflowOptions.class);
      PathMatcherBuilder excludedOriginPaths = PathMatcherBuilder.create(
          FileSystems.getDefault(), this.excludedOriginPaths);
      PathMatcherBuilder excludedDestinationPaths = PathMatcherBuilder.create(
          FileSystems.getDefault(), this.excludedDestinationPaths);
      return new Workflow<>(configName, name, origin, destination, authoring, transformation,
          workflowOptions.getLastRevision(), console,
          excludedOriginPaths, excludedDestinationPaths, mode, includeChangeListNotes,
          options.get(WorkflowOptions.class));
    }
  }
}
