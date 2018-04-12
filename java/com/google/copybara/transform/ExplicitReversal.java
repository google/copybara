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
import com.google.copybara.exception.ValidationException;
import java.io.IOException;

/**
 * A transformation which delegates to some arbitrary transformation and reverses to some arbitrary
 * transformation.
 */
public final class ExplicitReversal implements Transformation {

  private final Transformation forward;
  private final Transformation reverse;
  private final Boolean ignoreNoop;

  public ExplicitReversal(Transformation forward, Transformation reverse) {
    this(forward, reverse, /*ignoreNoop=*/null);
  }

  public ExplicitReversal(
      Transformation forward,
      Transformation reverse,
      Boolean ignoreNoop) {
    this.forward = checkNotNull(forward);
    this.reverse = checkNotNull(reverse);
    this.ignoreNoop = ignoreNoop;
  }

  @Override
  public void transform(TransformWork work)
      throws IOException, ValidationException {
    TransformWork newWork;
    if(ignoreNoop == null){
      // use parent ignoreNoop if current ignoreNoop is null
      newWork = work.insideExplicitTransform(work.getIgnoreNoop());
    } else {
      newWork = work.insideExplicitTransform(ignoreNoop);
    }
    forward.transform(newWork);
    work.updateFrom(newWork);
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
    return new ExplicitReversal(reverse, forward, ignoreNoop);
  }

  @Override
  public String describe() {
    return forward.describe();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forward", forward)
        .add("reverse", reverse)
        .toString();
  }
}
