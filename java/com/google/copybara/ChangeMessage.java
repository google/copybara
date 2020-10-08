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

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.copybara.doc.annotations.DocSignaturePrefix;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/**
 * An object that represents a well formed message: No superfluous new lines, a group of labels,
 * etc.
 *
 * <p>This class is immutable.
 */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "ChangeMessage",
    doc = "Represents a well formed parsed change message with its associated labels.")
@DocSignaturePrefix("message")
public final class ChangeMessage implements StarlarkValue {

  private static final String DOUBLE_NEWLINE = "\n\n";
  private static final String DASH_DASH_SEPARATOR = "\n--\n";
  private static final CharMatcher TRIM = CharMatcher.is('\n');

  private final String text;
  private final String groupSeparator;
  private final ImmutableList<LabelFinder> labels;

  private ChangeMessage(String text, String groupSeparator, List<LabelFinder> labels) {
    this.text = TRIM.trimFrom(text);
    this.groupSeparator = Preconditions.checkNotNull(groupSeparator);
    this.labels = ImmutableList.copyOf(Preconditions.checkNotNull(labels));
  }

  /**
   * Create a new message object looking for labels in just the last paragraph.
   *
   * <p>Use this for Copybara well-formed messages.
   */
  public static ChangeMessage parseMessage(String message) {
    String trimMsg = TRIM.trimFrom(message);
    int doubleNewLine = trimMsg.lastIndexOf(DOUBLE_NEWLINE);
    int dashDash = trimMsg.lastIndexOf(DASH_DASH_SEPARATOR);
    if (doubleNewLine == -1 && dashDash == -1) {
      // Empty message like "\n\nfoo: bar" or "\n\nfoo bar baz"
      if (message.startsWith(DOUBLE_NEWLINE)) {
        return new ChangeMessage("", DOUBLE_NEWLINE, linesAsLabels(trimMsg));
      }
      return new ChangeMessage(trimMsg, DOUBLE_NEWLINE, new ArrayList<>());
    } else if (doubleNewLine > dashDash) {
      return new ChangeMessage(trimMsg.substring(0, doubleNewLine), DOUBLE_NEWLINE,
          linesAsLabels(trimMsg.substring(doubleNewLine + 2)));
    } else {
      return new ChangeMessage(trimMsg.substring(0, dashDash), DASH_DASH_SEPARATOR,
          linesAsLabels(trimMsg.substring(dashDash + 4)));
    }
  }

  /**
   * Create a new message object treating all the lines as possible labels instead of looking
   * just in the last paragraph for labels.
   */
  public static ChangeMessage parseAllAsLabels(String message) {
    Preconditions.checkNotNull(message);
    return new ChangeMessage("", DOUBLE_NEWLINE, linesAsLabels(message));
  }

  private static List<LabelFinder> linesAsLabels(String message) {
    Preconditions.checkNotNull(message);
    return Splitter.on('\n').splitToList(TRIM.trimTrailingFrom(message)).stream()
        .map(LabelFinder::new)
        .collect(Collectors.toList());
  }

  @StarlarkMethod(name = "first_line", doc = "First line of this message", structField = true)
  public String firstLine() {
    int idx = text.indexOf('\n');
    return idx == -1 ? text : text.substring(0, idx);
  }

  @StarlarkMethod(
      name = "text",
      doc = "The text description this message, not including the labels.",
      structField = true)
  public String getText() {
    return text;
  }

  public ImmutableList<LabelFinder> getLabels() {
    return labels;
  }

  /**
   * Returns all the labels in the message. If a label appears multiple times, it respects the
   * order of appearance.
   */
  public ImmutableListMultimap<String, String> labelsAsMultimap(){
    // We overwrite duplicates
    ImmutableListMultimap.Builder<String, String> result = ImmutableListMultimap.builder();
    for (LabelFinder label : labels) {
      if (label.isLabel()) {
        result.put(label.getName(), label.getValue());
      }
    }
    return result.build();
  }

  @StarlarkMethod(
      name = "label_values",
      doc = "Returns a list of values associated with the label name.",
      parameters = {
        @Param(name = "label_name", named = true, doc = "The label name."),
      })
  public Sequence<String> getLabelValues(String labelName) {
    ImmutableListMultimap<String, String> localLabels = labelsAsMultimap();
    if (localLabels.containsKey(labelName)) {
      return StarlarkList.immutableCopyOf(localLabels.get(labelName));
    }
    return StarlarkList.empty();
  }


  @CheckReturnValue
  public ChangeMessage withLabel(String name, String separator, String value) {
    List<LabelFinder> newLabels = new ArrayList<>(labels);
    // Add an additional line if none of the previous elements are labels
    if (!newLabels.isEmpty() && newLabels.stream().noneMatch(LabelFinder::isLabel)) {
      newLabels.add(new LabelFinder(""));
    }
    newLabels.add(new LabelFinder(validateLabelName(name) + Preconditions
        .checkNotNull(separator) + Preconditions.checkNotNull(value)));
    return new ChangeMessage(this.text, this.groupSeparator, newLabels);
  }

  @CheckReturnValue
  public ChangeMessage withReplacedLabel(String labelName, String separator , String value) {
    validateLabelName(labelName);
    List<LabelFinder> newLabels = labels.stream().map(label -> label.isLabel(labelName)
        ? new LabelFinder(labelName + separator + value)
        : label)
        .collect(Collectors.toList());
    return new ChangeMessage(this.text, this.groupSeparator, newLabels);
  }

  @CheckReturnValue
  public ChangeMessage withNewOrReplacedLabel(String labelName, String separator, String value) {
    validateLabelName(labelName);
    List<LabelFinder> newLabels = new ArrayList<>();
    boolean wasReplaced = false;

    for (LabelFinder originalLabel : labels) {
      if (originalLabel.isLabel(labelName)) {
        newLabels.add(new LabelFinder(labelName + separator + value));
        wasReplaced = true;
      } else {
        newLabels.add(originalLabel);
      }
    }

    ChangeMessage newChangeMessage = new ChangeMessage(this.text, this.groupSeparator, newLabels);
    if (!wasReplaced) {
      return newChangeMessage.withLabel(labelName, separator, value);
    }
    return newChangeMessage;
  }

  /**
   * Remove a label by name if it exist.
   */
  @CheckReturnValue
  public ChangeMessage withRemovedLabelByName(String name) {
    validateLabelName(name);
    ImmutableList<LabelFinder> filteredLabels =
        labels
            .stream()
            .filter(label -> !label.isLabel(name))
            .collect(ImmutableList.toImmutableList());
    return new ChangeMessage(this.text, this.groupSeparator, filteredLabels);
  }

  /**
   * Remove a label by name and value if it exist.
   */
  @CheckReturnValue
  public ChangeMessage withRemovedLabelByNameAndValue(String name, String value) {
    validateLabelName(name);
    ImmutableList<LabelFinder> filteredLabels =
        labels
            .stream()
            .filter(label -> !label.isLabel(name) || !label.getValue().equals(value))
            .collect(ImmutableList.toImmutableList());
    return new ChangeMessage(this.text, this.groupSeparator, filteredLabels);
  }

  private static String validateLabelName(String label) {
    Preconditions.checkArgument(LabelFinder.VALID_LABEL.matcher(label).matches(),
        "Label '%s' is not a valid label", label);
    return label;
  }

  /**
   * Set the text part of the message, leaving the labels untouched.L
   */
  @CheckReturnValue
  public ChangeMessage withText(String text) {
    return new ChangeMessage(TRIM.trimFrom(text), this.groupSeparator, this.labels);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    if (!text.isEmpty()) {
      sb.append(text).append(labels.isEmpty() ? "\n" : groupSeparator);
    }
    for (LabelFinder label : labels) {
      sb.append(label.getLine()).append('\n');
    }
    // Lets normalize in case parseAllAsLabels was used and all the labels where
    // removed.
    return TRIM.trimFrom(sb.toString()) + '\n';
  }
}
