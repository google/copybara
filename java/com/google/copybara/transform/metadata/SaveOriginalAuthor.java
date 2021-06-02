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
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import java.io.IOException;
import net.starlark.java.syntax.Location;

/**
 * Saves the original author of the change in the message with a label
 */
public class SaveOriginalAuthor implements Transformation {

  private final String label;
  private final Location location;

  SaveOriginalAuthor(String label, Location location) {
    this.label = Preconditions.checkNotNull(label);
    this.location = Preconditions.checkNotNull(location);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    work.addOrReplaceLabel(label, work.getAuthor().toString(), "=");
    return TransformationStatus.success();
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new RestoreOriginalAuthor(label, /*searchAllChanges=*/false, location);
  }

  @Override
  public String describe() {
    return "Saving original author";
  }

  @Override
  public Location location() {
    return location;
  }
}
