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

import com.google.common.base.CaseFormat;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import java.io.IOException;
import java.util.Map.Entry;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.syntax.Location;

/**
 * A transformation that uses a Skylark function to transform the code.
 */
public class SkylarkTransformation implements Transformation {

  private final StarlarkCallable function;
  private final Dict<?, ?> params;
  private final StarlarkThread.PrintHandler printHandler;

  public SkylarkTransformation(
      StarlarkCallable function, Dict<?, ?> params, StarlarkThread.PrintHandler printHandler) {
    this.function = Preconditions.checkNotNull(function);
    this.params = Preconditions.checkNotNull(params);
    this.printHandler = Preconditions.checkNotNull(printHandler);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException, RepoException {
    SkylarkConsole skylarkConsole = new SkylarkConsole(work.getConsole());
    TransformWork skylarkWork = work.withConsole(skylarkConsole)
        .withParams(params);
    TransformationStatus status = TransformationStatus.success();
    try (Mutability mu = Mutability.create("dynamic_transform")) {
      StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
      thread.setPrintHandler(printHandler);
      Object result =
          Starlark.call(
              thread, function, ImmutableList.of(skylarkWork), /*kwargs=*/ ImmutableMap.of());
      result = result == Starlark.NONE ? TransformationStatus.success() : result;
      checkCondition(
          result instanceof TransformationStatus,
          "Dynamic transforms functions should return nothing or objects of type %s, but '%s'"
              + " returned: %s",
          TransformationStatus.STARLARK_TYPE_NAME,
          describe(),
          result);
      status = (TransformationStatus) result;
    } catch (EvalException e) {
      if (e.getCause() instanceof EmptyChangeException) {
        throw ((EmptyChangeException) e.getCause());
      }
      if (e.getCause() instanceof RepoException) {
        throw new RepoException(
            String.format(
                "Error while executing the skylark transformation %s: %s",
                describe(), e.getMessageWithStack()),
            e);
      }
      throw new ValidationException(
          String.format(
              "Error while executing the skylark transformation %s: %s",
              describe(), e.getMessageWithStack()),
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("This should not happen.", e);
    } finally {
      work.updateFrom(skylarkWork);
    }

    checkCondition(
        skylarkConsole.getErrorCount() == 0,
        "%d error(s) while executing %s",
        skylarkConsole.getErrorCount(),
        describe());
    return status;
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
  public String toString() {
    String camelCaseName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.describe());
    if (camelCaseName.endsWith("Impl")) {
      camelCaseName = camelCaseName.substring(0, camelCaseName.length() - 4);
    }
    ToStringHelper builder = MoreObjects.toStringHelper(camelCaseName);
    for (Entry<?, ?> e : this.params.entrySet()) {
      builder.add(e.getKey().toString(), e.getValue().toString());
    }
    return builder.toString();
  }

  @Override
  public Location location() {
    return function.getLocation();
  }
}
