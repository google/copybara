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

import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import java.io.IOException;

/**
 * Saves the original author of the change in the message with a label
 */
public class SaveOriginalAuthor implements Transformation {

  private final String label;

  SaveOriginalAuthor(String label) {
    this.label = label;
  }

  @Override
  public void transform(TransformWork work)
      throws IOException, ValidationException {
    if (work.getLabelInMessage(label) != null) {
      work.replaceLabel(label, work.getAuthor().toString());
    } else {
      work.addLabel(label, work.getAuthor().toString());
    }
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new RestoreOriginalAuthor(label);
  }

  @Override
  public String describe() {
    return "Saving original author";
  }
}
