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

import static com.google.copybara.transform.Transformations.toTransformation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkThread;

/**
 * A transformation that runs a sequence of delegate transformations
 */
public class Sequence implements Transformation {

  private final Profiler profiler;
  private final WorkflowOptions workflowOptions;
  private final ImmutableList<Transformation> sequence;
  private final NoopBehavior noopBehavior;

  protected final Logger logger = Logger.getLogger(Sequence.class.getName());

  @VisibleForTesting
  Sequence(
      Profiler profiler,
      WorkflowOptions workflowOptions,
      ImmutableList<Transformation> sequence,
      NoopBehavior noopBehavior) {
    this.profiler = Preconditions.checkNotNull(profiler);
    this.workflowOptions = workflowOptions;
    this.sequence = Preconditions.checkNotNull(sequence);
    this.noopBehavior = noopBehavior;
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException, RepoException {

    List<Transformation> transformationList = getTransformations();

    boolean someTransformWasSuccess = false;
    for (int i = 0; i < transformationList.size(); i++) {
      // Only check the cache in between consecutive Transforms
      if (i != 0) {
        work.validateTreeStateCache();
      }

      Transformation transformation = transformationList.get(i);
      work.getConsole().progress(getTransformMessage(transformation, i, transformationList.size()));
      TransformationStatus status = runOneTransform(work, transformation);

      if (status.isNoop()) {
        if (noopBehavior == NoopBehavior.FAIL_IF_ANY_NOOP) {
          status.throwException(work.getConsole(), workflowOptions.ignoreNoop);
        } else if (noopBehavior == NoopBehavior.NOOP_IF_ANY_NOOP) {
          if (workflowOptions.ignoreNoop) {
            status.warn(work.getConsole());
          } else {
            return status;
          }
        } else if (work.getConsole().isVerbose()) {
          status.warn(work.getConsole());
        }
      }

      someTransformWasSuccess |= status.isSuccess();
    }

    if (noopBehavior == NoopBehavior.NOOP_IF_ALL_NOOP && !someTransformWasSuccess) {
      return TransformationStatus.noop(
          String.format("%s was a no-op because all wrapped transforms were no-ops", this));
    }

    return TransformationStatus.success();
  }

  private String getTransformMessage(
      Transformation transform, int currentTransformIndex, int transformListSize) {
      String transformMsg = transform.describe();
      if (transformListSize > 1) {
        transformMsg =
          String.format(
              "[%2d/%d] Transform %s", currentTransformIndex + 1, transformListSize, transformMsg);
      }
      return transformMsg;
  }

  private ImmutableList<Transformation> getTransformations() {
    if (!workflowOptions.joinTransformations()) {
      return sequence;
    }
    List<Transformation> result = new ArrayList<>(sequence.size());
    Transformation prev = null;
    for (Transformation transformation : sequence) {
      if (prev != null && prev.canJoin(transformation)) {
        prev = prev.join(transformation);
      } else {
        if (prev != null) {
          result.add(prev);
        }
        prev = transformation;
      }
    }
    if (prev != null) {
      result.add(prev);
    }
    return ImmutableList.copyOf(result);
  }

  private TransformationStatus runOneTransform(TransformWork work, Transformation transform)
      throws IOException, ValidationException, RepoException {
    try (ProfilerTask ignored = profiler.start(transform.describe().replace('/', ' '))) {
      return transform.transform(work);
    }
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    ImmutableList.Builder<Transformation> list = ImmutableList.builder();
    for (Transformation element : sequence) {
      list.add(element.reverse());
    }
    return new Sequence(profiler, workflowOptions, list.build().reverse(), noopBehavior);
  }

  @VisibleForTesting
  public ImmutableList<Transformation> getSequence() {
    return sequence;
  }

  /**
   * returns a string like "Sequence[a, b, c]"
   */
  @Override
  public String toString() {
    return "Sequence" + sequence;
  }

  @Override
  public String describe() {
    return "sequence";
  }

  /**
   * Create a sequence from a list of native and Skylark transforms.
   *
   * @param description a description of the argument being converted, such as its name
   */
  public static Sequence fromConfig(
      Profiler profiler,
      WorkflowOptions workflowOptions,
      net.starlark.java.eval.Sequence<?> elements,
      String description,
      StarlarkThread.PrintHandler printHandler,
      Function<Transformation, Transformation> transformWrapper,
      NoopBehavior noopBehavior)
      throws EvalException {
    ImmutableList.Builder<Transformation> transformations = ImmutableList.builder();
    for (Object element : elements) {
      transformations.add(
          transformWrapper.apply(toTransformation(element, description, printHandler)));
    }
    return new Sequence(profiler, workflowOptions, transformations.build(), noopBehavior);
  }

  /**
   * An enum to specify how a {@link Sequence} should handle a no-op occurring in one of its child
   * {@link Transformation}s.
   */
  public enum NoopBehavior {
    /**
     * No matter how many of the wrapped Transformation no-op, this Sequence is always considered to
     * be a successful op.
     */
    IGNORE_NOOP,
    /**
     * If at least 1 of the wrapped transformations is a no-op, this Sequence will also be
     * considered a no-op. The remainder of the wrapped transformations will not be run.
     */
    NOOP_IF_ANY_NOOP,
    /**
     * This Sequence will run all of the wrapped transformations. If all of them are no-ops
     * (including the case where the transfomation list is empty), this Sequence is considered to be
     * a no-op.
     */
    NOOP_IF_ALL_NOOP,
    /**
     * If at least 1 of the wrapped transformation is a no-op, this Sequence will fail immediately,
     * even if another Sequence with {@code IGNORE_NOOP} is wrapping this one.
     */
    FAIL_IF_ANY_NOOP
  }
}
