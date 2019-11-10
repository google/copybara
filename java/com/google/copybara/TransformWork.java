/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara;

import static com.google.copybara.exception.ValidationException.checkCondition;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.authoring.Author;
import com.google.copybara.doc.annotations.DocSignaturePrefix;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.treestate.FileSystemTreeState;
import com.google.copybara.treestate.TreeState;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.Starlark;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Contains information related to an on-going process of repository transformation.
 *
 * <p>This object is passed to the user defined functions in Skylark so that they can personalize
 * the commit message, change the author or in the future run custom transformations.
 */
@SuppressWarnings("unused")
@SkylarkModule(
    name = "TransformWork",
    category = SkylarkModuleCategory.BUILTIN,
    doc =
        "Data about the set of changes that are being migrated. "
            + "It includes information about changes like: the author to be used for commit, "
            + "change message, etc. You receive a TransformWork object as an argument to the <code>"
            + "transformations</code> functions used in <code>core.workflow</code>")
@DocSignaturePrefix("ctx")
public final class TransformWork implements SkylarkContext<TransformWork>, SkylarkValue {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String COPYBARA_CONTEXT_REFERENCE_LABEL = "COPYBARA_CONTEXT_REFERENCE";
  static final String COPYBARA_LAST_REV = "COPYBARA_LAST_REV";
  static final String COPYBARA_CURRENT_REV = "COPYBARA_CURRENT_REV";
  static final String COPYBARA_CURRENT_MESSAGE = "COPYBARA_CURRENT_MESSAGE";
  static final String COPYBARA_AUTHOR = "COPYBARA_AUTHOR";
  static final String COPYBARA_CURRENT_MESSAGE_TITLE = "COPYBARA_CURRENT_MESSAGE_TITLE";

  private final Path checkoutDir;
  private Metadata metadata;
  private final Changes changes;
  private final Console console;
  private final MigrationInfo migrationInfo;
  private final Revision resolvedReference;
  private final TreeState treeState;
  private final boolean insideExplicitTransform;
  private final boolean ignoreNoop;
  @Nullable
  private final Revision lastRev;
  @Nullable private final Revision currentRev;
  private TransformWork skylarkTransformWork;
  private final SkylarkDict<?, ?> skylarkTransformParams;

  public TransformWork(Path checkoutDir, Metadata metadata, Changes changes, Console console,
      MigrationInfo migrationInfo, Revision resolvedReference, boolean ignoreNoop) {
    this(checkoutDir, metadata, changes, console, migrationInfo, resolvedReference,
        new FileSystemTreeState(checkoutDir), /*insideExplicitTransform*/ false,
        /*lastRev=*/null, /*currentRev=*/null, SkylarkDict.empty(), ignoreNoop);
  }

  private TransformWork(
      Path checkoutDir,
      Metadata metadata,
      Changes changes,
      Console console,
      MigrationInfo migrationInfo,
      Revision resolvedReference,
      TreeState treeState,
      boolean insideExplicitTransform,
      @Nullable Revision lastRev,
      @Nullable Revision currentRev,
      SkylarkDict<?, ?> skylarkTransformParams,
      boolean ignoreNoop) {
    this.checkoutDir = Preconditions.checkNotNull(checkoutDir);
    this.metadata = Preconditions.checkNotNull(metadata);
    this.changes = changes;
    this.console = console;
    this.migrationInfo = migrationInfo;
    this.resolvedReference = Preconditions.checkNotNull(resolvedReference);
    this.treeState = treeState;
    this.insideExplicitTransform = insideExplicitTransform;
    this.lastRev = lastRev;
    this.currentRev = currentRev;
    this.skylarkTransformWork = this;
    this.skylarkTransformParams = skylarkTransformParams;
    this.ignoreNoop = ignoreNoop;
  }

  /**
   * The path containing the repository state to transform. Transformation should be done in-place.
   */
  public Path getCheckoutDir() {
    return checkoutDir;
  }

  /**
   * A description of the migrated changes to include in the destination's change description. The
   * destination may add more boilerplate text or metadata.
   */
  @SkylarkCallable(name = "message", doc = "Message to be used in the change", structField = true)
  public String getMessage() {
    return metadata.getMessage();
  }

  @SkylarkCallable(name = "author", doc = "Author to be used in the change", structField = true)
  public Author getAuthor() {
    return metadata.getAuthor();
  }

  @SkylarkCallable(
      name = "params",
      doc = "Parameters for the function if created with" + " core.dynamic_transform",
      structField = true)
  public SkylarkDict<?, ?> getParams() {
    return skylarkTransformParams;
  }

  @SkylarkCallable(name = "set_message", doc = "Update the message to be used in the change",
      parameters = { @Param(name = "message", type = String.class)}
  )
  public void setMessage(String message) {
    this.metadata = this.metadata.withMessage(message);
  }

  public Metadata getMetadata() {
    return metadata;
  }

  public void addHiddenLabels(ImmutableMultimap<String, String> hiddenLabels) {
    this.metadata = metadata.addHiddenLabels(hiddenLabels);
  }

  @SkylarkCallable(
      name = "run", doc = "Run a glob or a transform. For example:<br>"
      + "<code>files = ctx.run(glob(['**.java']))</code><br>or<br>"
      + "<code>ctx.run(core.move(\"foo\", \"bar\"))</code><br>or<br>",
      parameters = {
          @Param(name = "runnable", type = Object.class,
              doc = "A glob or a transform (Transforms still not implemented)"),
      }, useLocation = true)
  public Object run(Object runnable, Location location)
      throws EvalException, IOException, ValidationException {
    if (runnable instanceof Glob) {
      PathMatcher pathMatcher = ((Glob) runnable).relativeTo(checkoutDir);

      try (Stream<Path> stream = Files.walk(checkoutDir)) {
        return SkylarkList.createImmutable(
            stream
                .filter(Files::isRegularFile)
                .filter(pathMatcher::matches)
                .map(p -> new CheckoutPath(checkoutDir.relativize(p), checkoutDir))
                .collect(Collectors.toList()));
      }
    } else if (runnable instanceof Transformation) {
      // Works like Sequence. We keep always the latest transform work to allow
      // catching for two sequential replaces.
      skylarkTransformWork = skylarkTransformWork.withUpdatedTreeState();
      ((Transformation) runnable).transform(skylarkTransformWork);
      this.updateFrom(skylarkTransformWork);
      return Starlark.NONE;
    }

    throw new EvalException(location, String.format(
        "Only globs or transforms can be run, but '%s' is of type %s",
        runnable, runnable.getClass()));
  }

  @SkylarkCallable(
      name = "new_path",
      doc = "Create a new path",
      parameters = {
        @Param(name = "path", type = String.class, doc = "The string representing the path"),
      }, useLocation = true)
  public CheckoutPath newPath(String path, Location location) throws EvalException {
    return CheckoutPath.createWithCheckoutDir(checkoutDir.getFileSystem().getPath(path),
        checkoutDir, location);
  }

  @SkylarkCallable(
      name = "create_symlink",
      doc = "Create a symlink",
      parameters = {
        @Param(name = "link", type = CheckoutPath.class, doc = "The link path"),
        @Param(name = "target", type = CheckoutPath.class, doc = "The target path"),
      },
      useLocation = true)
  public void createSymlink(CheckoutPath link, CheckoutPath target, Location location)
      throws EvalException {
    try {
      Path linkFullPath = asCheckoutPath(link, location);
      // Verify target is inside checkout dir
      asCheckoutPath(target, location);

      if (Files.exists(linkFullPath)) {
        throw new EvalException(
            location,
            String.format(
                "'%s' already exist%s",
                link.getPath(),
                Files.isDirectory(linkFullPath)
                    ? " and is a directory"
                    : Files.isSymbolicLink(linkFullPath)
                        ? " and is a symlink"
                        : Files.isRegularFile(linkFullPath)
                            ? " and is a regular file"
                            // Shouldn't happen:
                            : " and we don't know what kind of file is"));
      }

      Path relativized = link.getPath().getParent() == null
          ? target.getPath()
          : link.getPath().getParent().relativize(target.getPath());
      Files.createDirectories(linkFullPath.getParent());

      // Shouldn't happen.
      Verify.verify(
          linkFullPath.getParent().resolve(relativized).normalize().startsWith(checkoutDir),
          "%s path escapes the checkout dir", relativized);
      Files.createSymbolicLink(linkFullPath, relativized);
    } catch (IOException e) {
      String msg = "Cannot create symlink: " + e.getMessage();
      logger.atSevere().withCause(e).log(msg);
      throw new EvalException(location, msg, e);
    }
  }

  @SkylarkCallable(
      name = "write_path",
      doc = "Write an arbitrary string to a path (UTF-8 will be used)",
      parameters = {
        @Param(name = "path", type = CheckoutPath.class, doc = "The string representing the path"),
        @Param(name = "content", type = String.class, doc = "The content of the file"),
      },
      useLocation = true)
  public void writePath(CheckoutPath path, String content, Location location)
      throws IOException, EvalException {
    Path fullPath = asCheckoutPath(path, location);
    if (fullPath.getParent() != null) {
      Files.createDirectories(fullPath.getParent());
    }
    Files.write(fullPath, content.getBytes(UTF_8));
  }

  @SkylarkCallable(
      name = "read_path",
      doc = "Read the content of path as UTF-8",
      parameters = {
        @Param(name = "path", type = CheckoutPath.class, doc = "The string representing the path"),
      },
      useLocation = true)
  public String readPath(CheckoutPath path, Location location) throws IOException, EvalException {
    return new String(Files.readAllBytes(asCheckoutPath(path, location)), UTF_8);
  }

  private Path asCheckoutPath(CheckoutPath path, Location location) throws EvalException {
    Path normalized = checkoutDir.resolve(path.getPath()).normalize();
    if (!normalized.startsWith(checkoutDir)) {
      throw new EvalException(location, path + " is not inside the checkout directory");
    }
    return normalized;
  }

  @SkylarkCallable(name = "add_label",
      doc = "Add a label to the end of the description",
      parameters = {
          @Param(name = "label", type = String.class, doc = "The label to replace"),
          @Param(name = "value", type = String.class, doc = "The new value for the label"),
          @Param(name = "separator", type = String.class,
              doc = "The separator to use for the label", defaultValue = "\"=\""),
          @Param(name = "hidden", type = Boolean.class,
              doc = "Don't show the label in the message but only keep it internally",
              named = true, positional = false, defaultValue = "False"),
      })
  public void addLabel(String label, String value, String separator, Boolean hidden) {
    if (hidden) {
      addHiddenLabels(ImmutableListMultimap.of(label, value));
    } else {
      setMessage(ChangeMessage.parseMessage(getMessage())
          .withLabel(label, separator, value)
          .toString());
    }
  }

  @SkylarkCallable(name = "add_or_replace_label",
      doc = "Replace an existing label or add it to the end of the description",
      parameters = {
          @Param(name = "label", type = String.class, doc = "The label to replace"),
          @Param(name = "value", type = String.class, doc = "The new value for the label"),
          @Param(name = "separator", type = String.class,
              doc = "The separator to use for the label", defaultValue = "\"=\""),
      })
  public void addOrReplaceLabel(String label, String value, String separator) {
    setMessage(ChangeMessage.parseMessage(getMessage())
        .withNewOrReplacedLabel(label, separator, value)
        .toString());
  }

  @SkylarkCallable(name = "add_text_before_labels",
      doc = "Add a text to the description before the labels paragraph",
      parameters = { @Param(name = "text", type = String.class) })
  public void addTextBeforeLabels(String text) {
    ChangeMessage message = ChangeMessage.parseMessage(getMessage());
    message = message.withText(message.getText() + '\n' + text);
    setMessage(message.toString());
  }

  @SkylarkCallable(name = "replace_label", doc = "Replace a label if it exist in the message",
      parameters = {
          @Param(name = "label", type = String.class, doc = "The label to replace"),
          @Param(name = "value", type = String.class, doc = "The new value for the label"),
          @Param(name = "separator", type = String.class,
              doc = "The separator to use for the label", defaultValue = "\"=\""),
          @Param(name = "whole_message", type = Boolean.class,
              doc = "By default Copybara only looks in the last paragraph for labels. This flag"
                  + "make it replace labels in the whole message.", defaultValue = "False"),
      })
  public void replaceLabel(String labelName, String value, String separator, Boolean wholeMessage) {
    setMessage(parseMessage(wholeMessage)
        .withReplacedLabel(labelName, separator, value)
        .toString());
  }

  @SkylarkCallable(name = "remove_label", doc = "Remove a label from the message if present",
      parameters = {
          @Param(name = "label", type = String.class, doc = "The label to delete"),
          @Param(name = "whole_message", type = Boolean.class,
              doc = "By default Copybara only looks in the last paragraph for labels. This flag"
                  + "make it replace labels in the whole message.", defaultValue = "False"),
      }
  )
  public void removeLabel(String label, Boolean wholeMessage) {
    setMessage(parseMessage(wholeMessage).withRemovedLabelByName(label).toString());
  }

  public void removeLabelWithValue(String label, String value, Boolean wholeMessage) {
    setMessage(parseMessage(wholeMessage).withRemovedLabelByNameAndValue(label, value).toString());
  }

  @SkylarkCallable(name = "now_as_string", doc = "Get current date as a string",
      parameters = {
          @Param(name = "format", type = String.class, doc = "The format to use. See:"
              + " https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html"
              + " for details.",
              defaultValue = "\"yyyy-MM-dd\""),
          @Param(name = "zone", doc = "The timezone id to use. "
              + "See https://docs.oracle.com/javase/8/docs/api/java/time/ZoneId.html. By default"
              + " UTC ", defaultValue = "\"UTC\"")
      }
  )
  public String formatDate(String format, String zone) {
    return DateTimeFormatter.ofPattern(format).format(ZonedDateTime.now(ZoneId.of(zone)));
  }

  private ChangeMessage parseMessage(Boolean wholeMessage) {
    return wholeMessage
        ? ChangeMessage.parseAllAsLabels(getMessage())
        : ChangeMessage.parseMessage(getMessage());
  }

  @SkylarkCallable(name = "find_label", doc = ""
      + "Tries to find a label. First it looks at the generated message (IOW labels that might"
      + " have been added by previous steps), then looks in all the commit messages being imported"
      + " and finally in the resolved reference passed in the CLI.",
      parameters = { @Param(name = "label", type = String.class) },
      allowReturnNones = true)
  @Nullable
  public String getLabel(String label) {
    Collection<String> labelValues = findLabelValues(label, /*all=*/false);
    return labelValues.isEmpty() ? null : Iterables.getLast(labelValues);
  }

  @SkylarkCallable(name = "find_all_labels", doc = ""
      + "Tries to find all the values for a label. First it looks at the generated message (IOW"
      + " labels that might have been added by previous steps), then looks in all the commit"
      + " messages being imported and finally in the resolved reference passed in the CLI.",
      parameters = { @Param(name = "message", type = String.class) },
      allowReturnNones = true)
  public SkylarkList<String> getAllLabels(String label) {
    return findLabelValues(label, /*all=*/true);
  }

  private SkylarkList<String> findLabelValues(String label, boolean all) {
    Map<String, ImmutableList<String>> coreLabels = getCoreLabels();
    if (coreLabels.containsKey(label)) {
      return SkylarkList.createImmutable(coreLabels.get(label));
    }
    ArrayList<String> result = new ArrayList<>();
    ImmutableList<LabelFinder> msgLabel = getLabelInMessage(label);
    if (!msgLabel.isEmpty()) {
      result.addAll(Lists.transform(msgLabel, LabelFinder::getValue));
      if (!all) {
        return SkylarkList.createImmutable(result);
      }
    }
    ImmutableSet<String> values = metadata.getHiddenLabels().get(label);
    if (!values.isEmpty()) {
      if (!all) {
        return SkylarkList.createImmutable(ImmutableList.of(Iterables.getLast(values)));
      } else {
        result.addAll(values);
      }
    }

    // Try to find the label in the current changes migrated. We prioritize current
    // changes over resolvedReference. Since in iterative mode this would be more
    // specific to the current migration.
    for (Change<?> change : changes.getCurrent()) {
      SkylarkList<String> val = change.getLabelsAllForSkylark().get(label);
      if (val != null) {
        result.addAll(val);
        if (!all) {
          return SkylarkList.createImmutable(result);
        }
      }
      ImmutableList<String> revVal = change.getRevision().associatedLabel(label);
      if (!revVal.isEmpty()) {
        result.addAll(revVal);
        if (!all) {
          return SkylarkList.createImmutable(result);
        }
      }
    }

    // Try to find the label in the resolved reference
    ImmutableList<String> resolvedRefLabel = resolvedReference.associatedLabels().get(label);
    if (result.addAll(resolvedRefLabel) && !all) {
      return SkylarkList.createImmutable(result);
    }

    return SkylarkList.createImmutable(result);
  }

  /**
   * Search for a label in the current message. We are less strict and look in the whole message.
   */
  private ImmutableList<LabelFinder> getLabelInMessage(String name) {
    return parseMessage(/*wholeMessage= */true).getLabels().stream()
        .filter(label -> label.isLabel(name)).collect(ImmutableList.toImmutableList());
  }

  @SkylarkCallable(name = "set_author", doc = "Update the author to be used in the change",
      parameters = { @Param(name = "author", type = Author.class) })
  public void setAuthor(Author author) {
    this.metadata = this.metadata.withAuthor(author);
  }

  @SkylarkCallable(name = "changes", doc = "List of changes that will be migrated",
      structField = true)
  public Changes getChanges() {
    return changes;
  }

  @SkylarkCallable(name = "console", doc = "Get an instance of the console to report errors or"
      + " warnings", structField = true)
  public Console getConsole() {
    return console;
  }

  public MigrationInfo getMigrationInfo() {
    return migrationInfo;
  }

  public Revision getResolvedReference() {
    return resolvedReference;
  }

  public boolean isInsideExplicitTransform() {
    return insideExplicitTransform;
  }

  /**
   * Create a clone of the transform work but use a different console.
   */
  public TransformWork withConsole(Console newConsole) {
    return new TransformWork(checkoutDir, metadata, changes, Preconditions.checkNotNull(newConsole),
        migrationInfo, resolvedReference, treeState, insideExplicitTransform, lastRev,
        currentRev, skylarkTransformParams, ignoreNoop);
  }

  /**
   * Creates a new {@link TransformWork} object that contains a new {@link TreeState}
   * ready to be used by a transform.
   */
  public TransformWork withUpdatedTreeState() {
    return new TransformWork(checkoutDir, metadata, changes, console,
                             migrationInfo, resolvedReference, treeState.newTreeState(),
                             insideExplicitTransform, lastRev, currentRev, skylarkTransformParams, ignoreNoop);
  }

  @Override
  public TransformWork withParams(SkylarkDict<?, ?> params) {
    Preconditions.checkNotNull(params);
    return new TransformWork(checkoutDir, metadata, changes, console, migrationInfo,
                             resolvedReference, treeState, insideExplicitTransform, lastRev,
                             currentRev, params, ignoreNoop);
  }

  @VisibleForTesting
  public TransformWork withChanges(Changes changes) {
    Preconditions.checkNotNull(changes);
    return new TransformWork(checkoutDir, metadata, changes, console, migrationInfo,
                             resolvedReference, treeState, insideExplicitTransform, lastRev,
                             currentRev, skylarkTransformParams, ignoreNoop);
  }

  @VisibleForTesting
  public TransformWork withLastRev(@Nullable Revision previousRef) {
    return new TransformWork(checkoutDir, metadata, changes, console, migrationInfo,
                             resolvedReference, treeState, insideExplicitTransform, previousRef,
                             currentRev, skylarkTransformParams, ignoreNoop);
  }

  @VisibleForTesting
  public TransformWork withResolvedReference(Revision resolvedReference) {
    Preconditions.checkNotNull(resolvedReference);
    return new TransformWork(checkoutDir, metadata, changes, console, migrationInfo,
                             resolvedReference, treeState, insideExplicitTransform, lastRev,
                             currentRev, skylarkTransformParams, ignoreNoop);
  }

  public TransformWork insideExplicitTransform(boolean ignoreNoop) {
    Preconditions.checkNotNull(resolvedReference);
    return new TransformWork(checkoutDir, metadata, changes, console, migrationInfo,
                             resolvedReference, treeState, /*insideExplicitTransform=*/true,
                             lastRev, currentRev, skylarkTransformParams, ignoreNoop);
  }

  public <O extends Revision> TransformWork withCurrentRev(Revision currentRev) {
    Preconditions.checkNotNull(currentRev);
    return new TransformWork(checkoutDir, metadata, changes, console, migrationInfo,
                             resolvedReference, treeState, insideExplicitTransform,
                             lastRev, currentRev, skylarkTransformParams, ignoreNoop);
  }

  /**
   * Update mutable state from another worker data.
   */
  public void updateFrom(TransformWork skylarkWork) {
    metadata = skylarkWork.metadata;
  }

  public TreeState getTreeState() {
    return treeState;
  }

  public boolean getIgnoreNoop() {
    return ignoreNoop;
  }

  /**
   * Return the map of internal core labels.
   *
   * <p/> We use a Map instead of Multimap so that we can differentiate the label exists but
   * we don't have a value.
   */
  private Map<String, ImmutableList<String>> getCoreLabels() {
    Map<String, ImmutableList<String>> labels = new HashMap<>();
    String ctxRef = resolvedReference.contextReference();
    labels.put(COPYBARA_CONTEXT_REFERENCE_LABEL, ctxRef == null
        ? ImmutableList.of()
        : ImmutableList.of(ctxRef));

    labels.put(COPYBARA_LAST_REV, lastRev == null
        ? ImmutableList.of()
        // Skip anything after space in the revision, since we might include metadata like
        // snapshot number, etc. that is subject to change.
        : ImmutableList.of(lastRev.asString().replaceAll(" .*", "")));

    labels.put(COPYBARA_CURRENT_REV, currentRev == null
        ? ImmutableList.of()
        // Skip anything after space in the revision, since we might include metadata like
        // snapshot number, etc. that is subject to change.
        : ImmutableList.of(
            currentRev.asString().replaceAll(" .*", "")));

    labels.put(COPYBARA_CURRENT_MESSAGE, ImmutableList.of(getMessage()));
    labels.put(COPYBARA_AUTHOR, ImmutableList.of(getMessage()));
    labels.put(COPYBARA_CURRENT_MESSAGE_TITLE,
        ImmutableList.of(Change.extractFirstLine(metadata.getMessage())));
    return labels;
  }

  @Override
  public void onFinish(Object result, SkylarkContext<?> actionContext) throws ValidationException {
    checkCondition(
        result == null || result.equals(Starlark.NONE),
        "Transform work cannot return any result but returned: %s",
        result);
  }
}
