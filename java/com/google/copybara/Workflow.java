// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.copybara.Destination.WriterResult;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.NonReversibleValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.transform.Sequence;
import com.google.copybara.transform.Transformation;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.PathMatcherBuilder;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
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
@AutoValue
public abstract class Workflow<R extends Origin.Reference> {

  private final Logger logger = Logger.getLogger(this.getClass().getName());

  abstract String configName();
  abstract String name();

  /**
   * The repository that represents the source of truth
   */
  public abstract Origin<R> origin();

  /**
   * The destination repository to copy to.
   */
  public abstract Destination destination();

  abstract Authoring authoring();

  /**
   * Transformation to run before writing them to the destination.
   */
  public abstract Transformation transformation();

  @Nullable abstract String lastRevisionFlag();
  abstract Console console();
  abstract PathMatcherBuilder excludedOriginPaths();
  abstract PathMatcherBuilder excludedDestinationPaths();
  abstract WorkflowMode mode();
  abstract boolean includeChangeListNotes();
  abstract WorkflowOptions workflowOptions();
  abstract boolean reversibleCheck();
  abstract boolean verbose();

  public void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException, EnvironmentException, ValidationException {
    console().progress("Cleaning working directory");
    FileUtil.deleteAllFilesRecursively(workdir);

    console().progress("Getting last revision: "
        + "Resolving " + ((sourceRef == null) ? "origin reference" : sourceRef));
    R resolvedRef = origin().resolve(sourceRef);
    logger.log(Level.INFO,
        String.format(
            "Running Copybara for config '%s', workflow '%s' and ref '%s': %s",
            configName(), name(), resolvedRef.asString(),
            this.toString()));
    logger.log(Level.INFO, String.format("Using working directory : %s", workdir));
    mode().run(new RunHelper(workdir, resolvedRef));
  }

  final class RunHelper {
    private final Path workdir;
    private final R resolvedRef;
    private final Destination.Writer writer;

    /**
     * @param workdir working directory to use for the transformations
     * @param resolvedRef reference to migrate
     */
    RunHelper(Path workdir, R resolvedRef) {
      this.workdir = Preconditions.checkNotNull(workdir);
      this.resolvedRef = Preconditions.checkNotNull(resolvedRef);
      this.writer = destination().newWriter();
    }

    R getResolvedRef() {
      return resolvedRef;
    }

    /**
     * Authoring configuration.
     */
    Authoring getAuthoring() {
      return authoring();
    }

    /** Console to use for printing messages. */
    Console getConsole() {
      return console();
    }

    /**
     * Options that change how workflows behave.
     */
    WorkflowOptions workflowOptions() {
      return Workflow.this.workflowOptions();
    }

    Destination getDestination() {
      return destination();
    }

    Origin<R> getOrigin() {
      return origin();
    }

    /**
     * Performs a full migration, including checking out files from the origin, deleting excluded
     * files, transforming the code, and writing to the destination. This writes to the destination
     * exactly once.
     * @param ref reference to the version which will be written to the destination
     * @param author the author that the destination change will be attributed to
     * @param processConsole console to use to print progress messages
     * @param message change message to write to the destination
     *
     * @return The result of this migration
     */
    WriterResult migrate(R ref, Author author, Console processConsole, String message)
        throws EnvironmentException, IOException, RepoException, ValidationException {
      return migrate(ref, author, processConsole, message, /*destinationBaseline=*/ null);
    }

    WriterResult migrate(R ref, Author author, Console processConsole, String message,
        @Nullable String destinationBaseline)
        throws EnvironmentException, IOException, RepoException, ValidationException {
      processConsole.progress("Cleaning working directory");
      FileUtil.deleteAllFilesRecursively(workdir);
      Path checkoutDir = workdir.resolve("checkout");
      Files.createDirectories(checkoutDir);

      processConsole.progress("Checking out the change");
      origin().checkout(ref, checkoutDir);

      // Remove excluded origin files.
      if (!excludedOriginPaths().isEmpty()) {
        PathMatcher pathMatcher = excludedOriginPaths().relativeTo(checkoutDir);
        processConsole.progress("Removing excluded origin files");

        int result = FileUtil.deleteFilesRecursively(checkoutDir, pathMatcher);
        logger.log(Level.INFO,
            String.format("Removed %s files from workdir that were excluded", result));

        if (result == 0) {
          throw new RepoException(
              String.format("Nothing was deleted in the workdir for excludedOriginPaths: '%s'",
                  pathMatcher));
        }
      }

      Path originCopy = null;
      if (reversibleCheck()) {
        console().progress("Making a copy or the workdir for reverse checking");
        originCopy = Files.createDirectories(workdir.resolve("origin"));
        FileUtil.copyFilesRecursively(checkoutDir, originCopy);
      }

      transform(transformation(), checkoutDir, processConsole);

      if (reversibleCheck()) {
        console().progress("Checking that the transformations can be reverted");
        Path reverse = Files.createDirectories(workdir.resolve("reverse"));
        FileUtil.copyFilesRecursively(checkoutDir, reverse);
        transform(transformation().reverse(), reverse, processConsole);
        String diff = new String(DiffUtil.diff(originCopy, reverse, verbose()),
            StandardCharsets.UTF_8);
        if (!diff.trim().isEmpty()) {
          console().error("Non reversible transformations:\n"
              + DiffUtil.colorize(console(), diff));
          throw new NonReversibleValidationException(String.format(
              "Workflow '%s' is not reversible", workflowOptions().getWorkflowName()));
        }
      }

      TransformResult transformResult =
          new TransformResult(checkoutDir, ref, author, message, excludedDestinationPaths());
      if (destinationBaseline != null) {
        transformResult = transformResult.withBaseline(destinationBaseline);
      }
      WriterResult result = writer.write(transformResult, processConsole);
      Verify.verifyNotNull(result, "Destination returned a null result.");
      return result;
    }

    /**
     * Creates a commit message to correspond to an import of any number of changes in the origin.
     */
    String changesSummaryMessage() throws RepoException {
      return String.format(
          "Imports '%s'.\n\n"
              + "This change was generated by Copybara (go/copybara).\n%s\n",
          configName(),
          getChangeListNotes());
    }

    ImmutableList<Change<R>> changesSinceLastImport() throws RepoException {
      R lastRev = getLastRev();
      if (lastRev == null) {
        throw new RepoException(String.format(
                "Previous revision label %s could not be found in %s and --last-rev flag"
                + " was not passed", origin().getLabelName(), destination()));
      }
      return origin().changes(getLastRev(), resolvedRef);
    }

    /**
     * Returns the last revision that was imported from this origin to the destination. Returns
     * {@code null} if it cannot be determined.
     *
     * <p>If {@code --last-rev} is specified, that revision will be used. Otherwise, the previous
     * reference will be resolved in the destination with the origin label.
     */
    @Nullable private R getLastRev() throws RepoException {
      if (lastRevisionFlag() != null) {
        try {
          return origin().resolve(lastRevisionFlag());
        } catch (RepoException e) {
          throw new RepoException(
              "Could not resolve --last-rev flag. Please make sure it exists in the origin: "
                  + lastRevisionFlag(),
              e);
        }
      }

      String previousRef = destination().getPreviousRef(origin().getLabelName());
      return (previousRef == null) ? null : origin().resolve(previousRef);
    }

    private String getChangeListNotes() throws RepoException {
      if (!includeChangeListNotes()) {
        return "";
      }
      R lastRev = getLastRev();
      if (lastRev == null) {
        logger.log(Level.WARNING, "Previous reference couldn't be resolved");
        return "(List of included changes could not be computed)\n";
      }

      StringBuilder result = new StringBuilder("List of included changes:\n");
      for (Change<R> change : origin().changes(lastRev, resolvedRef)) {
        result.append(String.format("  - %s %s by %s\n",
            change.getReference().asString(),
            change.firstLineMessage(),
            change.getAuthor()));
      }

      return result.toString();
    }
  }

  private void transform(Transformation transformation, Path checkout, Console console)
      throws ValidationException, EnvironmentException {
    // Runs the transformation for the workflow
    try {
      transformation.transform(checkout, console);
    } catch (IOException e) {
      throw new EnvironmentException("Error applying transformation: " + transformation, e);
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
    private boolean askConfirmation;
    private Boolean reversibleCheck;

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
      this.transformations = ImmutableList.<Transformation.Yaml>copyOf(transformations);
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

    @DocField(description = "Indicates that the tool should show the diff and require user's "
        + "confirmation before making a change in the destination.",
        required = false, defaultValue = "false")
    public void setAskConfirmation(boolean askConfirmation) {
      this.askConfirmation = askConfirmation;
    }

    @DocField(description = "Indicates if the tool should try to to reverse all the transformations"
        + " at the end to check that they are reversible.",
        required = false, defaultValue = "true for CHANGE_REQUEST mode. False otherwise",
        undocumented = true)
    public void setReversibleCheck(boolean reversibleCheck) {
      this.reversibleCheck = reversibleCheck;
    }

    public Workflow withOptions(Options options, String configName)
        throws ConfigValidationException, EnvironmentException {

      Sequence.Yaml sequence = new Sequence.Yaml();
      sequence.setTransformations(this.transformations);
      Transformation transformation = sequence.withOptions(options);

      ConfigValidationException.checkNotMissing(this.authoring,"authoring");
      ConfigValidationException.checkNotMissing(this.origin,"origin");
      ConfigValidationException.checkNotMissing(this.destination,"destination");

      if (reversibleCheck == null) {
        reversibleCheck = mode == WorkflowMode.CHANGE_REQUEST;
      }
      if (reversibleCheck) {
        // Check that we can reverse the transform since we will automatically reverse to check
        // the reverse gives back the original input.
        sequence.checkReversible();
      }

      Authoring authoring = this.authoring.withOptions(options, configName);
      Origin<?> origin = this.origin.withOptions(options, authoring);
      Destination destination =
          this.destination.withOptions(options, configName, askConfirmation);
      GeneralOptions generalOptions = options.get(GeneralOptions.class);
      Console console = generalOptions.console();
      WorkflowOptions workflowOptions = options.get(WorkflowOptions.class);
      PathMatcherBuilder excludedOriginPaths = PathMatcherBuilder.create(
          FileSystems.getDefault(), this.excludedOriginPaths);
      PathMatcherBuilder excludedDestinationPaths = PathMatcherBuilder.create(
          FileSystems.getDefault(), this.excludedDestinationPaths);
      return new AutoValue_Workflow<>(configName, name, origin, destination, authoring, transformation,
          workflowOptions.getLastRevision(), console,
          excludedOriginPaths, excludedDestinationPaths, mode, includeChangeListNotes,
          options.get(WorkflowOptions.class), reversibleCheck, generalOptions.isVerbose());
    }
  }

}
