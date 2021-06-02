/*
 * Copyright (C) 2017 Google Inc.
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
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.IntentionalNoop;
import com.google.re2j.Pattern;
import java.io.IOException;
import net.starlark.java.syntax.Location;

/**
 * A checker that validates that the change description satisfies a Regex or that it doesn't
 * if verifyNoMatch is set.
 */
public class MetadataVerifyMatch implements Transformation {

  private final Pattern pattern;
  private final boolean verifyNoMatch;
  private final Location location;

  MetadataVerifyMatch(Pattern pattern, boolean verifyNoMatch,
      Location location) {
    this.pattern = Preconditions.checkNotNull(pattern);
    this.verifyNoMatch = verifyNoMatch;
    this.location = Preconditions.checkNotNull(location);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    boolean found = pattern.matcher(work.getMessage()).find();
    checkCondition(found || verifyNoMatch,
        "Could not find '%s' in the change message. Message was:\n%s", pattern, work.getMessage());

    checkCondition(!found || !verifyNoMatch,
        "'%s' found in the change message. Message was:\n%s", pattern, work.getMessage());
    return TransformationStatus.success();
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  @Override
  public String describe() {
    return String.format("Verify message %s '%s'",
        (verifyNoMatch ? "does not match" : "matches"),
        pattern);
  }

  @Override
  public Location location() {
    return location;
  }
}
