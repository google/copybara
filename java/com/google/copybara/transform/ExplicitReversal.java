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

package com.google.copybara.transform;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import java.io.IOException;
import net.starlark.java.syntax.Location;

/**
 * A transformation which delegates to some arbitrary transformation and reverses to some arbitrary
 * transformation.
 */
public final class ExplicitReversal implements Transformation {

  private final Transformation forward;
  private final Transformation reverse;

  public ExplicitReversal(
      Transformation forward,
      Transformation reverse) {
    this.forward = checkNotNull(forward);
    this.reverse = checkNotNull(reverse);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException, RepoException {
    TransformWork newWork;
    newWork = work.insideExplicitTransform();
    TransformationStatus status = forward.transform(newWork);
    work.updateFrom(newWork);
    return status;
  }

  /**
   * Returns the forward transformation, for introspection and config validation.
   */
  public Transformation getForward() {
    return forward;
  }

  /**
   * Returns the reverse transformation, for introspection and config validation.
   */
  public Transformation getReverse() {
    return reverse;
  }

  @Override
  public Transformation reverse() {
    return new ExplicitReversal(reverse, forward);
  }

  @Override
  public String describe() {
    return forward.describe();
  }

  @Override
  public Location location() {
    return forward.location();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forward", forward)
        .add("reverse", reverse)
        .toString();
  }
}
