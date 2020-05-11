/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.feedback;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.SkylarkContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.devtools.build.lib.syntax.Dict;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.Starlark;
import com.google.devtools.build.lib.syntax.StarlarkCallable;
import com.google.devtools.build.lib.syntax.StarlarkSemantics;
import com.google.devtools.build.lib.syntax.StarlarkThread;

/**
 * An implementation of {@link Action} that delegates to a Skylark function.
 */
public class SkylarkAction implements Action {

  private final StarlarkCallable function;
  private final Dict<?, ?> params;
  private final StarlarkThread.PrintHandler printHandler;

  public SkylarkAction(
      StarlarkCallable function, Dict<?, ?> params, StarlarkThread.PrintHandler printHandler) {
    this.function = Preconditions.checkNotNull(function);
    this.params = Preconditions.checkNotNull(params);
    this.printHandler = Preconditions.checkNotNull(printHandler);
  }

  @Override
  public void run(SkylarkContext<?> context) throws ValidationException, RepoException {
    //noinspection unchecked
    SkylarkContext<?> actionContext = (SkylarkContext<?>) context.withParams(params);
    try (Mutability mu = Mutability.create("dynamic_action")) {
      StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
      thread.setPrintHandler(printHandler);
      Object result =
          Starlark.call(
              thread, function, ImmutableList.of(actionContext), /*kwargs=*/ ImmutableMap.of());
      context.onFinish(result, actionContext);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Error calling Skylark:", e);
    } catch (EvalException e) {
      // TODO(copybara-team): display e's stack trace to users.
      Throwable cause = e.getCause();
      String error =
          String.format("Error while executing the skylark transformation %s: %s. Location: %s",
              function.getName(), e.getMessage(), e.getLocation());
      if (cause instanceof RepoException) {
        throw new RepoException(error, cause);
      }
      throw new ValidationException(error, cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("This should not happen.", e);
    }
  }

  @Override
  public String getName() {
    return function.getName();
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    ImmutableSetMultimap.Builder<String, String> builder = ImmutableSetMultimap.builder();
    for (Object paramKey : params.keySet()) {
      builder.put(paramKey.toString(), params.get(paramKey).toString());
    }
    return builder.build();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", function.getName())
        .toString();

  }
}
