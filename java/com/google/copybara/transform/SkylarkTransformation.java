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

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.ValidationException;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.StarlarkThread;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * A transformation that uses a Skylark function to transform the code.
 */
public class SkylarkTransformation implements Transformation {

  private final BaseFunction function;
  private final SkylarkDict<?, ?> params;
  private final Supplier<StarlarkThread> dynamicThread;

  public SkylarkTransformation(
      BaseFunction function, SkylarkDict<?, ?> params, Supplier<StarlarkThread> dynamicThread) {
    this.function = Preconditions.checkNotNull(function);
    this.params = Preconditions.checkNotNull(params);
    this.dynamicThread = Preconditions.checkNotNull(dynamicThread);
  }

  @Override
  public void transform(TransformWork work)
      throws IOException, ValidationException {
    SkylarkConsole skylarkConsole = new SkylarkConsole(work.getConsole());
    TransformWork skylarkWork = work.withConsole(skylarkConsole)
        .withParams(params);
    try {
      Object result =
          function.call(
              ImmutableList.of(skylarkWork), /*kwargs=*/ null, /*ast*/ null, dynamicThread.get());
      checkCondition(result instanceof NoneType,
          "Message transformer functions should not return anything, but '%s' returned: %s",
          function.getName(), result);
    } catch (EvalException e) {
      if (e.getCause() instanceof EmptyChangeException) {
        throw ((EmptyChangeException) e.getCause());
      }
      throw new ValidationException(
          String.format(
              "Error while executing the skylark transformation %s: %s. Location: %s",
              function.getName(), e.getMessage(), e.getLocation()), e
      );
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("This should not happen.", e);
    } finally {
      work.updateFrom(skylarkWork);
    }

    checkCondition(skylarkConsole.getErrorCount() == 0, "%d error(s) while executing %s",
        skylarkConsole.getErrorCount(), function.getName());
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }

  @Override
  public String describe() {
    return function.getName();
  }

  @Override
  public Location location() {
    return function.getLocation();
  }
}
