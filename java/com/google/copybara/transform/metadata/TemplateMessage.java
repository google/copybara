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

package com.google.copybara.transform.metadata;

import com.google.common.base.Preconditions;
import com.google.copybara.LabelFinder;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.IntentionalNoop;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Adds a header text in top of the change message.
 *
 * <p>This transforms allows to refer to change labels both from the current message or the set of
 * commits being imported.
 */
public class TemplateMessage implements Transformation {

  private final String header;
  private final boolean ignoreIfLabelNotFound;
  private final boolean newLine;
  private boolean replaceMessage;
  private static final Pattern VAR_PATTERN =
      Pattern.compile("\\$\\{(" + LabelFinder.VALID_LABEL + ")}");

  private final Set<String> labels = new HashSet<>();

  TemplateMessage(String header, boolean ignoreIfLabelNotFound, boolean newLine,
      boolean replaceMessage) {
    this.header = Preconditions.checkNotNull(header);
    this.ignoreIfLabelNotFound = ignoreIfLabelNotFound;
    this.newLine = newLine;
    this.replaceMessage = replaceMessage;
    Matcher matcher = VAR_PATTERN.matcher(header);
    while (matcher.find()) {
      labels.add(matcher.group(1));
    }
  }

  @Override
  public void transform(TransformWork work)
      throws IOException, ValidationException {
    Map<String, String> labelValues = new HashMap<>();
    for (String label : labels) {
      String value = work.getLabel(label);
      if (value != null) {
        labelValues.put(label, value);
        continue;
      }
      if (ignoreIfLabelNotFound) {
        return;
      }
      throw new ValidationException(
          "Cannot find label '%s' in message:\n %s\nor any of the original commit messages",
          label, work.getMessage());
    }
    String msgPrefix = header;
    for (Entry<String, String> entry : labelValues.entrySet()) {
      msgPrefix = msgPrefix.replace("${" + entry.getKey() + "}", entry.getValue());
    }
    String newMsg = msgPrefix;
    if (!replaceMessage) {
      newMsg += (newLine ? "\n" : "") + work.getMessage();
    }
    work.setMessage(newMsg);
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  @Override
  public String describe() {
    return "Adding header to the message";
  }
}
