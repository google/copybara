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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.LabelFinder;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.IntentionalNoop;
import java.io.IOException;
import java.util.Optional;

/**
 * Given a label that is not present in the change message but it is in the changes
 * metadata, expose it as a text label.
 */
public class ExposeLabelInMessage implements Transformation {

  private final String label;
  private final String newLabelName;
  private final String separator;
  private final boolean ignoreNotFound;

  ExposeLabelInMessage(String label, String newLabelName, String separator,
      boolean ignoreNotFound) {
    this.label = Preconditions.checkNotNull(label);
    this.newLabelName = Preconditions.checkNotNull(newLabelName);
    this.separator = Preconditions.checkNotNull(separator);
    this.ignoreNotFound = ignoreNotFound;
  }

  @Override
  public void transform(TransformWork work) throws IOException, ValidationException {
    ImmutableList<LabelFinder> labelInMessage = work.getLabelInMessage(label);
    String value;
    if (!labelInMessage.isEmpty()) {
      LabelFinder last = Iterables.getLast(labelInMessage);
      value = last.getValue();
      if (!label.equals(newLabelName) || !separator.equals(last.getSeparator())) {
        // Remove the old label since we want it with different name/separator.
        work.removeLabel(label,/*wholeMessage=*/true);
      }
    } else  {
      value = work.getLabel(label);
    }

    if (value == null) {
      ValidationException.checkCondition(ignoreNotFound, "Cannot find label " + label);
      return;
    }

    work.addOrReplaceLabel(newLabelName, value, separator);
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  @Override
  public String describe() {
    return String.format("Exposing label %s as %s", label, newLabelName);
  }
}
