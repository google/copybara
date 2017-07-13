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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.copybara.authoring.Author;
import com.google.copybara.treestate.FileSystemTreeState;
import com.google.copybara.treestate.TreeState;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.FuncallExpression.FuncallException;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SkylarkList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Contains information related to an on-going process of repository transformation.
 *
 * This object is passed to the user defined functions in Skylark so that they can personalize the
 * commit message, change the author or in the future run custom transformations.
 */
@SkylarkModule(name = "TransformWork",
    category = SkylarkModuleCategory.BUILTIN,
    doc = "Data about the set of changes that are being migrated. "
        + "It includes information about changes like: the author to be used for commit, "
        + "change message, etc. You receive a TransformWork object as an argument to the <code>"
        + "transformations</code> functions used in <code>core.workflow</code>")
public final class TransformWork {

  static final String COPYBARA_CONTEXT_REFERENCE_LABEL = "COPYBARA_CONTEXT_REFERENCE";

  private final Path checkoutDir;
  private Metadata metadata;
  private final Changes changes;
  private final Console console;
  private final MigrationInfo migrationInfo;
  private final Revision resolvedReference;
  private final TreeState treeState;
  private final boolean insideExplicitTransform;

  public TransformWork(Path checkoutDir, Metadata metadata, Changes changes, Console console,
      MigrationInfo migrationInfo, Revision resolvedReference) {
    this(checkoutDir, metadata, changes, console, migrationInfo, resolvedReference,
        new FileSystemTreeState(checkoutDir), /*insideExplicitTransform*/ false);
  }
  public TransformWork(Path checkoutDir, Metadata metadata, Changes changes, Console console,
      MigrationInfo migrationInfo, Revision resolvedReference, TreeState treeState,
      boolean insideExplicitTransform) {
    this.checkoutDir = Preconditions.checkNotNull(checkoutDir);
    this.metadata = Preconditions.checkNotNull(metadata);
    this.changes = changes;
    this.console = console;
    this.migrationInfo = migrationInfo;
    this.resolvedReference = Preconditions.checkNotNull(resolvedReference);
    this.treeState = treeState;
    this.insideExplicitTransform = insideExplicitTransform;
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

  @SkylarkCallable(name = "set_message", doc = "Update the message to be used in the change")
  public void setMessage(String message) {
    this.metadata = new Metadata(Preconditions.checkNotNull(message, "Message cannot be null"),
        metadata.getAuthor());
  }

  @SkylarkCallable(
      name = "run", doc = "Run a glob or a transform. For example:<br>"
      + "<code>files = ctx.run(glob(['**.java']))</code><br>or<br>"
      + "<code>ctx.run(core.move(\"foo\", \"bar\"))</code><br>or<br>",
      parameters = {
          @Param(name = "runnable", type = Object.class,
              doc = "A glob or a transform (Transforms still not implemented)"),
      })
  public Object run(Object runnable) throws EvalException, IOException, ValidationException {
    if (runnable instanceof Glob) {
      PathMatcher pathMatcher = ((Glob) runnable).relativeTo(checkoutDir);

      return SkylarkList.createImmutable(Files.walk(checkoutDir)
          .filter(Files::isRegularFile)
          .filter(pathMatcher::matches)
          .map(p -> new CheckoutPath(checkoutDir.relativize(p), checkoutDir))
          .collect(Collectors.toList()));
    } else if (runnable instanceof Transformation) {
      ((Transformation) runnable).transform(this);
      return Runtime.NONE;
    }

    throw new EvalException(null, String.format(
        "Only globs or transforms can be run, but '%s' is of type %s",
        runnable, runnable.getClass()));
  }

  @SkylarkCallable(
      name = "new_path", doc = "Create a new path",
      parameters = {
          @Param(name = "path", type = String.class, doc = "The string representing the path"),
      })
  public CheckoutPath newPath(String path) throws FuncallException {
    return CheckoutPath.createWithCheckoutDir(checkoutDir.getFileSystem().getPath(path),
        checkoutDir);
  }

  @SkylarkCallable(name = "add_label",
      doc = "Add a label to the end of the description",
      parameters = {
          @Param(name = "label", type = String.class, doc = "The label to replace"),
          @Param(name = "value", type = String.class, doc = "The new value for the label"),
          @Param(name = "separator", type = String.class,
              doc = "The separator to use for the label", defaultValue = "\"=\""),
      })
  public void addLabel(String label, String value, String separator) {
    setMessage(ChangeMessage.parseMessage(getMessage())
        .addLabel(label, separator, value)
        .toString());
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
        .addOrReplaceLabel(label, separator, value)
        .toString());
  }

  @SkylarkCallable(name = "add_text_before_labels",
      doc = "Add a text to the description before the labels paragraph")
  public void addTextBeforeLabels(String text) {
    ChangeMessage message = ChangeMessage.parseMessage(getMessage());
    message.setText(message.getText() + '\n' + text);
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
        .replaceLabel(labelName, separator, value)
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
    setMessage(parseMessage(wholeMessage).removeLabelByName(label).toString());
  }

  private ChangeMessage parseMessage(Boolean wholeMessage) {
    return wholeMessage
        ? ChangeMessage.parseAllAsLabels(getMessage())
        : ChangeMessage.parseMessage(getMessage());
  }

  @SkylarkCallable(name = "find_label", doc = ""
      + "Tries to find a label. First it looks at the generated message (IOW labels that might"
      + " have been added by previous steps), then looks in all the commit messages being imported"
      + " and finally in the resolved reference passed in the CLI."
      , allowReturnNones = true)
  @Nullable
  public String getLabel(String label) {
    if (label.equals(COPYBARA_CONTEXT_REFERENCE_LABEL)) {
      return resolvedReference.contextReference();
    }
    Optional<LabelFinder> msgLabel = getLabelInMessage(label);
    if (msgLabel.isPresent()) {
      return msgLabel.get().getValue();
    }
    // Try to find the label in the current changes migrated. We prioritize current
    // changes over resolvedReference. Since in iterative mode this would be more
    // specific to the current migration.
    for (Change<?> change : changes.getCurrent()) {
      String val = change.getLabels().get(label);
      if (val != null) {
        return val;
      }
    }

    // Try to find the label in the resolved reference
    String resolvedRefLabel = resolvedReference.associatedLabels().get(label);
    if (resolvedRefLabel != null) {
      return resolvedRefLabel;
    }
    return null;
  }

  /**
   * Search for a label in the current message. We are less strict and look in the whole message.
   */
  public Optional<LabelFinder> getLabelInMessage(String name) {
    return parseMessage(/*wholeMessage= */true).getLabels().stream()
        .filter(label -> label.isLabel(name)).findFirst();
  }

  @SkylarkCallable(name = "set_author", doc = "Update the author to be used in the change")
  public void setAuthor(Author author) {
    this.metadata = new Metadata(getMessage(),
        Preconditions.checkNotNull(author, "Author cannot be null"));
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
                             migrationInfo, resolvedReference);
  }

  /**
   * Creates a new {@link TransformWork} object that contains a new {@link TreeState}
   * ready to be used by a transform.
   */
  public TransformWork withUpdatedTreeState() {
    return new TransformWork(checkoutDir, metadata, changes, console,
        migrationInfo, resolvedReference, treeState.newTreeState(),
        insideExplicitTransform);
  }

  @VisibleForTesting
  public TransformWork withChanges(Changes changes) {
    Preconditions.checkNotNull(changes);
    return new TransformWork(checkoutDir, metadata, changes, console, migrationInfo,
        resolvedReference, treeState, insideExplicitTransform);
  }

  @VisibleForTesting
  public TransformWork withResolvedReference(Revision resolvedReference) {
    Preconditions.checkNotNull(resolvedReference);
    return new TransformWork(checkoutDir, metadata, changes, console, migrationInfo,
        resolvedReference, treeState, insideExplicitTransform);
  }

  public TransformWork insideExplicitTransform() {
    Preconditions.checkNotNull(resolvedReference);
    return new TransformWork(checkoutDir, metadata, changes, console, migrationInfo,
        resolvedReference, treeState, /*insideExplicitTransform=*/true);
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
}
