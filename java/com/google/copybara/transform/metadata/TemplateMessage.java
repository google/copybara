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
import com.google.copybara.exception.ValidationException;
import com.google.copybara.templatetoken.LabelTemplate;
import com.google.copybara.templatetoken.LabelTemplate.LabelNotFoundException;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.IntentionalNoop;
import java.io.IOException;
import net.starlark.java.syntax.Location;

/**
 * Adds a header text in top of the change message.
 *
 * <p>This transforms allows to refer to change labels both from the current message or the set of
 * commits being imported.
 */
public class TemplateMessage implements Transformation {

  private final boolean ignoreIfLabelNotFound;
  private final boolean newLine;
  private final boolean replaceMessage;
  private final LabelTemplate labelTemplate;
  private final Location location;

  TemplateMessage(String header, boolean ignoreIfLabelNotFound, boolean newLine,
      boolean replaceMessage, Location location) {
    this.ignoreIfLabelNotFound = ignoreIfLabelNotFound;
    this.newLine = newLine;
    this.replaceMessage = replaceMessage;
    labelTemplate = new LabelTemplate(header);
    this.location = Preconditions.checkNotNull(location);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    String newMsg;
    try {
      newMsg = labelTemplate.resolve(work::getLabel);
    } catch (LabelNotFoundException e) {
      if (ignoreIfLabelNotFound) {
        return TransformationStatus.success();
      }
      throw new ValidationException(String.format(
          "Cannot find label '%s' in message:\n %s\nor any of the original commit messages",
          e.getLabel(), work.getMessage())
      );
    }
    if (!replaceMessage) {
      newMsg += (newLine ? "\n" : "") + work.getMessage();
    }
    work.setMessage(newMsg);
    return TransformationStatus.success();
  }

  @Override
  public Transformation reverse() {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  @Override
  public String describe() {
    return "Adding header to the message";
  }

  @Override
  public Location location() {
    return location;
  }
}
