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
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.SkylarkContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.EvalException.EvalExceptionWithJavaCause;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import java.util.function.Supplier;

/**
 * An implementation of {@link Action} that delegates to a Skylark function.
 */
public class SkylarkAction implements Action {

  private final BaseFunction function;
  private final SkylarkDict<?, ?> params;
  private final Supplier<Environment> env;

  public SkylarkAction(BaseFunction function, SkylarkDict<?, ?> params, Supplier<Environment> env) {
    this.function = Preconditions.checkNotNull(function);
    this.params = Preconditions.checkNotNull(params);
    this.env = Preconditions.checkNotNull(env);
  }

  @Override
  public void run(SkylarkContext<?> context) throws ValidationException, RepoException {
    try {
      //noinspection unchecked
      SkylarkContext<?> actionContext = (SkylarkContext<?>) context.withParams(params);
      Object result = function.call(ImmutableList.of(actionContext), null,
          /*ast*/null, env.get());
      context.onFinish(result, actionContext);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Error calling Skylark:", e);
    } catch (EvalException e) {
      Throwable cause = e.getCause();
      if (cause instanceof EvalExceptionWithJavaCause) {
        cause = cause.getCause();
      }
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
