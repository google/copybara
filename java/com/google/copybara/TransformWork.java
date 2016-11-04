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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.copybara.authoring.Author;
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
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
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

  private final Path checkoutDir;
  private Metadata metadata;
  private final Changes changes;
  private final Console console;

  public TransformWork(Path checkoutDir, Metadata metadata, Changes changes, Console console) {
    this.checkoutDir = Preconditions.checkNotNull(checkoutDir);
    this.metadata = Preconditions.checkNotNull(metadata);
    this.changes = changes;
    this.console = console;
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
          .map(checkoutDir::relativize)
          .map(CheckoutPath::new)
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
    return CheckoutPath.create(checkoutDir.getFileSystem().getPath(path));
  }

  /**
   * If a label group is already present (one or more labels preceded by an empty line or '--' and
   * no more text except for empty lines after that) we inject the label at the end of the label
   * group.
   *
   * <p>If we cannot find an existing label group we add an empty line and the label at the end of
   * the message.
   *
   * <p>The injection is supposed to work for the vast majority of common cases but there might be
   * cases that cannot be detected. In those cases it will be appended to the end.
   */
  @SkylarkCallable(name = "add_label", doc = "Add a label to the end of the description")
  public void addLabel(String label, String value) {
    validateLabelName(label);
    List<String> byLine = Splitter.on("\n").splitToList(getMessage());
    int idx = byLine.size() - 1;
    boolean alreadyEmptyLine = false;
    // Skip all the empty lines at the end
    while (idx >= 0) {
      String line = byLine.get(idx);
      if (line.isEmpty()) {
        idx--;
        alreadyEmptyLine = true;
      } else {
        break;
      }
    }
    // Assuming we find a valid group here, this is is the position for the new label.
    int position = idx + 1;
    boolean putInGroup = false;
    while (idx >= 0) {
      String line = byLine.get(idx);
      if (new LabelFinder(line).isLabel()) {
        // Put it at the end of the group of labels, since there is at least one label.
        putInGroup = true;
        idx--;
      } else {
        break;
      }
    }
    if (idx >= 0) {
      // A label group that is not preceded by a clear separator (empty line or '--')
      String current = byLine.get(idx);
      if (!current.isEmpty() && !current.equals("--")) {
        // False alarm. This was not a label group since a non-empty line was before so it is
        // probably a line wrap.
        putInGroup = false;
      }
    }

    // Inject the label in the correct location. Otherwise do the naive thing and inject at the
    // end of the message.
    if (putInGroup) {
      ArrayList<String> list = new ArrayList<>(byLine);
      list.add(position, label + "=" + value);
      setMessage(Joiner.on("\n").join(list));
    } else {
      setMessage(getMessage() +
          (alreadyEmptyLine ? "" : "\n\n")
          + label + "=" + value + "\n");
    }
  }

  @SkylarkCallable(name = "replace_label", doc = "Replace a label if it exist in the message")
  public void replaceLabel(String label, String value) {
    validateLabelName(label);
    Pattern pattern = Pattern.compile(labelRegex(label), Pattern.MULTILINE);
    setMessage(pattern.matcher(getMessage()).replaceAll("$1" + value + "$3"));
  }

  @SkylarkCallable(name = "remove_label", doc = "Remove a label from the message if present")
  public void removeLabel(String label) {
    validateLabelName(label);
    Pattern pattern = Pattern.compile(labelRegex(label), Pattern.MULTILINE);
    setMessage(pattern.matcher(getMessage()).replaceAll(""));
  }

  private String labelRegex(String label) {
    return "(^\\Q" + label + "\\E *[=:])(.*)(\n|$)";
  }

  @SkylarkCallable(name = "find_label", doc = "Looks the message for a label and returns its value"
      + " if it can be found. Otherwise it return None.", allowReturnNones = true)
  @Nullable
  public String getLabel(String label) {
    Pattern pattern = Pattern.compile(labelRegex(label), Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(getMessage());
    if (matcher.find()) {
      return matcher.group(2);
    }
    return null;
  }

  @SkylarkCallable(name = "set_author", doc = "Update the author to be used in the change")
  public void setAuthor(Author author) {
    this.metadata = new Metadata(metadata.getMessage(),
        Preconditions.checkNotNull(author, "Author cannot be null"));
  }

  @SkylarkCallable(name = "changes", doc = "List of changes that will be migrated",
      structField = true)
  public Changes getChanges() {
    return changes;
  }

  private void validateLabelName(String label) {
    Preconditions.checkArgument(LabelFinder.VALID_LABEL.matcher(label).matches(),
        "Label '%s' is not a valid label", label);
  }

  @SkylarkCallable(name = "console", doc = "Get an instance of the console to report errors or"
      + " warnings", structField = true)
  public Console getConsole() {
    return console;
  }

  /**
   * Create a clone of the transform work but use a different console.
   */
  public TransformWork withConsole(Console newConsole) {
    return new TransformWork(checkoutDir, metadata, changes,
        Preconditions.checkNotNull(newConsole));
  }

  /**
   * Update mutable state from another worker data. Should be used with an instance created with
   * {@link #withConsole(Console)}
   */
  public void updateFrom(TransformWork skylarkWork) {
    metadata = skylarkWork.metadata;
  }
}
