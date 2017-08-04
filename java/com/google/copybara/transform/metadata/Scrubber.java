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
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.IntentionalNoop;
import com.google.re2j.Pattern;
import java.io.IOException;

/**
 * A transformer that removes matching substrings from the change description.
 */
public class Scrubber implements Transformation {

  private final Pattern pattern;
  private final String replacement;

  Scrubber(Pattern pattern, String replacement) {
    this.pattern = Preconditions.checkNotNull(pattern);
    this.replacement = Preconditions.checkNotNull(replacement);
  }

  @Override
  public void transform(TransformWork work)
      throws IOException, ValidationException {
    try {
      work.setMessage(pattern.matcher(work.getMessage()).replaceAll(replacement));
    } catch (IndexOutOfBoundsException e) {
      throw new ValidationException(
          String.format(
              "Could not find matching group. Are you missing a group in your regex '%s'?",
              pattern.toString()),
          e);
    }
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  @Override
  public String describe() {
    return "Description scrubber";
  }
}
