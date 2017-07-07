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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.SkylarkType;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A transformation that runs a sequence of delegate transformations
 */
public class Sequence implements Transformation {

  private final Profiler profiler;
  private final ImmutableList<Transformation> sequence;

  protected final Logger logger = Logger.getLogger(Sequence.class.getName());

  @VisibleForTesting
  Sequence(Profiler profiler, ImmutableList<Transformation> sequence) {
    this.profiler = Preconditions.checkNotNull(profiler);
    this.sequence = Preconditions.checkNotNull(sequence);
  }

  @Override
  public void transform(TransformWork work)
      throws IOException, ValidationException {
    if (sequence.size() == 1) {
      Transformation transform = sequence.get(0);
      logger.log(Level.INFO, transform.describe());
      work.getConsole().progress(transform.describe());
      runOneTransform(work, transform);
      return;
    }

    // Force to create a new fresh copy of the tree state to leave the
    // old one untouched so that a upper level call would return a fs based implementation.
    TransformWork localWork = work.withUpdatedTreeState();
    for (int i = 0; i < sequence.size(); i++) {
      Transformation transformation = sequence.get(i);
      String transformMsg = String.format(
          "[%2d/%d] Transform %s", i + 1, sequence.size(),
          transformation.describe());
      logger.log(Level.INFO, transformMsg);

      localWork.getConsole().progress(transformMsg);
      runOneTransform(localWork, transformation);
      localWork = localWork.withUpdatedTreeState();
    }
    // Update parent work with potentially modified metadata.
    work.updateFrom(localWork);
  }

  private void runOneTransform(TransformWork work, Transformation transform)
      throws IOException, ValidationException {
    try(ProfilerTask ignored = profiler.start(transform.describe().replace('/', ' '))) {
      transform.transform(work);
    }
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    ImmutableList.Builder<Transformation> list = ImmutableList.builder();
    for (Transformation element : sequence) {
      list.add(element.reverse());
    }
    return new Sequence(profiler, list.build().reverse());
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
   * @param description a description of the argument being converted, such as its name
   * @param env skylark environment for user defined transformations
   */
  public static Sequence fromConfig(Profiler profiler, SkylarkList<?> elements, String description, Environment env)
      throws EvalException {
    ImmutableList.Builder<Transformation> transformations = ImmutableList.builder();
    for (Object element : elements) {
      transformations.add(convertToTransformation(description, env, element));
    }
    return new Sequence(profiler, transformations.build());
  }

  private static Transformation convertToTransformation(String description, Environment env,
      Object element) throws EvalException {
    if (element instanceof BaseFunction) {
      return new SkylarkTransformation((BaseFunction) element, env);
    }
    SkylarkType.checkType(element, Transformation.class, "'" + description + "' element");
    return (Transformation) element;
  }
}
