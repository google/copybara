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

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Preconditions;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.IntentionalNoop;
import java.io.IOException;
import java.util.LinkedHashSet;
import net.starlark.java.syntax.Location;

/**
 * Given a label that is not present in the change message but it is in the changes
 * metadata, expose it as a text label.
 */
public class ExposeLabelInMessage implements Transformation {

  private final String label;
  private final String newLabelName;
  private final String separator;
  private final boolean ignoreNotFound;
  private final boolean all;
  private final Location location;

  ExposeLabelInMessage(String label, String newLabelName, String separator,
      boolean ignoreNotFound, boolean all, Location location) {
    this.label = Preconditions.checkNotNull(label);
    this.newLabelName = Preconditions.checkNotNull(newLabelName);
    this.separator = Preconditions.checkNotNull(separator);
    this.ignoreNotFound = ignoreNotFound;
    this.all = all;
    this.location = Preconditions.checkNotNull(location);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    if (all) {
      return exposeAllLabels(work);
    }

    String value = work.getLabel(this.label);
    if (value == null) {
      checkCondition(ignoreNotFound, "Cannot find label %s", this.label);
      return TransformationStatus.success();
    }
    if (label.equals(newLabelName)) {
      work.removeLabelWithValue(this.label, value, /*wholeMessage=*/true);
    }
    work.addLabel(newLabelName, value, separator, /*hidden=*/false);
    return TransformationStatus.success();
  }

  private TransformationStatus exposeAllLabels(TransformWork work) throws ValidationException {
    LinkedHashSet<String> values = new LinkedHashSet<>(work.getAllLabels(label));

    if (values.isEmpty()) {
      checkCondition(ignoreNotFound, "Cannot find label %s", label);
      return TransformationStatus.success();
    }
    //If the label name is the same, we remove it and add it at the end, since the format
    //of the message will be more consistent.
    if (label.equals(newLabelName)) {
      // Remove the old label since we want it with different name/separator.
      work.removeLabel(label, /*wholeMessage=*/true);
    }
    for (String value : values) {
      work.addLabel(newLabelName, value, separator, /*hidden=*/false);
    }

    return TransformationStatus.success();
  }

  @Override
  public Transformation reverse() {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  @Override
  public String describe() {
    return String.format("Exposing label %s as %s", label, newLabelName);
  }

  @Override
  public Location location() {
    return location;
  }
}
