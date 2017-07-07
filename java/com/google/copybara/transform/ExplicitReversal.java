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
import com.google.copybara.ValidationException;
import com.google.copybara.VoidOperationException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * A transformation which delegates to some arbitrary transformation and reverses to some arbitrary
 * transformation.
 */
public final class ExplicitReversal implements Transformation {

  private final Transformation forward;
  private final Transformation reverse;
  private final boolean ignoreNoop;
  @Nullable
  private final Console console;

  public ExplicitReversal(Transformation forward, Transformation reverse) {
    this(forward, reverse, /*ignoreNoop=*/false, /*console=*/null);
  }

  public ExplicitReversal(Transformation forward, Transformation reverse, boolean ignoreNoop,
      @Nullable Console console) {
    this.forward = checkNotNull(forward);
    this.reverse = checkNotNull(reverse);
    this.ignoreNoop = ignoreNoop;
    this.console = console;
    if (ignoreNoop) {
      checkNotNull(console, "A console is needed if ignore_noop is used");
    }
  }

  @Override
  public void transform(TransformWork work)
      throws IOException, ValidationException {
    try {
      TransformWork newWork = work.insideExplicitTransform();
      forward.transform(newWork);
      work.updateFrom(newWork);
    } catch (VoidOperationException e) {
      if (ignoreNoop) {
        checkNotNull(console).warn("Ignored noop because of 'ignore_noop' field: "
            + e.getMessage());
      } else {
        throw e;
      }
    }
  }

  @Override
  public Transformation reverse() {
    return new ExplicitReversal(reverse, forward, ignoreNoop, console);
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
