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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An object that represents a well formed message: No superfluous new lines, a group of labels,
 * etc.
 */
public class ChangeMessage {

  private static final String DOUBLE_NEWLINE = "\n\n";
  private static final String DASH_DASH_SEPARATOR = "\n--\n";
  private static final CharMatcher TRIM = CharMatcher.is('\n');

  private String text;
  private final String groupSeparator;
  private List<LabelFinder> labels;

  private ChangeMessage(String text, String groupSeparator, List<LabelFinder> labels) {
    this.text = TRIM.trimFrom(text);
    this.groupSeparator = Preconditions.checkNotNull(groupSeparator);
    this.labels = Preconditions.checkNotNull(labels);
  }

  /**
   * Create a new message object looking for labels in just the last paragraph.
   *
   * <p>Use this for Copybara well-formed messages.
   */
  public static ChangeMessage parseMessage(String message) {
    message = TRIM.trimFrom(message);
    int doubleNewLine = message.lastIndexOf(DOUBLE_NEWLINE);
    int dashDash = message.lastIndexOf(DASH_DASH_SEPARATOR);
    if (doubleNewLine == -1 && dashDash == -1) {
      return new ChangeMessage(message, DOUBLE_NEWLINE, new ArrayList<>());
    } else if (doubleNewLine > dashDash) {
      return new ChangeMessage(message.substring(0, doubleNewLine), DOUBLE_NEWLINE,
          linesAsLabels(message.substring(doubleNewLine + 2)));
    } else {
      return new ChangeMessage(message.substring(0, dashDash), DASH_DASH_SEPARATOR,
          linesAsLabels(message.substring(dashDash + 4)));
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

  public String firstLine() {
    int idx = text.indexOf('\n');
    return idx == -1 ? text : text.substring(0, idx);
  }

  public String getText() {
    return text;
  }

  public ImmutableList<LabelFinder> getLabels() {
    return ImmutableList.copyOf(labels);
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

  public ChangeMessage addLabel(String name, String separator, String value) {
    // Add an additional line if none of the previous elements are labels
    if (!labels.isEmpty() && labels.stream().noneMatch(LabelFinder::isLabel)) {
      labels.add(new LabelFinder(""));
    }
    labels.add(new LabelFinder(validateLabelName(name) + Preconditions
        .checkNotNull(separator) + Preconditions.checkNotNull(value)));
    return this;
  }

  public ChangeMessage replaceLabel(String labelName, String separator , String value) {
    validateLabelName(labelName);
    labels = labels.stream().map(label -> label.isLabel(labelName)
        ? new LabelFinder(labelName + separator + value)
        : label)
        .collect(Collectors.toList());
    return this;
  }

  public ChangeMessage addOrReplaceLabel(String labelName, String separator, String value) {
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

    labels = newLabels;

    if (!wasReplaced) {
      addLabel(labelName, separator, value);
    }
    return this;
  }

  /**
   * Remove a label by name if it exist.
   */
  public ChangeMessage removeLabelByName(String name) {
    validateLabelName(name);
    labels.removeIf(label -> label.isLabel(name));
    return this;
  }

  /**
   * Remove a label by name and value if it exist.
   */
  public ChangeMessage removeLabelByNameAndValue(String name, String value) {
    validateLabelName(name);
    labels.removeIf(label -> label.isLabel(name) && label.getValue().equals(value));
    return this;
  }

  private static String validateLabelName(String label) {
    Preconditions.checkArgument(LabelFinder.VALID_LABEL.matcher(label).matches(),
        "Label '%s' is not a valid label", label);
    return label;
  }

  /**
   * Set the text part of the message, leaving the labels untouched.L
   */
  public void setText(String text) {
    this.text = TRIM.trimFrom(text);
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
