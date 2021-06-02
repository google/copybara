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

package com.google.copybara.buildozer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.buildozer.BuildozerOptions.BuildozerCommand;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import java.io.IOException;

/**
 * A transformation that runs many buildozer transformation in batch
 */
public class BuildozerBatch implements BuildozerTransformation {

  private final BuildozerOptions options;
  private final WorkflowOptions workflowOptions;
  private final Iterable<BuildozerTransformation> transformations;

  private BuildozerBatch(BuildozerOptions options, WorkflowOptions workflowOptions,
      Iterable<BuildozerTransformation> transformations) {
    this.options = Preconditions.checkNotNull(options);
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
    this.transformations = Preconditions.checkNotNull(transformations);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    Iterable<BuildozerCommand> commands = ImmutableList.of();
    for (BuildozerTransformation transformation : transformations) {
      transformation.beforeRun(work);
      commands = Iterables.concat(commands, transformation.getCommands());
    }
    try {
      options.run(work.getConsole(), work.getCheckoutDir(), commands);
      return TransformationStatus.success();
    } catch (TargetNotFoundException e) {
      return TransformationStatus.noop(e.getMessage());
    }
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    throw new IllegalStateException("Reverse should never be called for join transformations");
  }

  @Override
  public String describe() {
    return "buildozer batch of " + Iterables.size(transformations) + " buildozer transformations";
  }

  @Override
  public boolean canJoin(Transformation transformation) {
    return isBuildozer(transformation);
  }

  static boolean isBuildozer(Transformation transformation) {
    return transformation instanceof BuildozerTransformation;
  }

  @Override
  public Transformation join(Transformation next) {
    return join(options, workflowOptions, this, (BuildozerTransformation) next);
  }

  static BuildozerBatch join(BuildozerOptions buildozerOptions,
      WorkflowOptions workflowOptions, BuildozerTransformation current,
      BuildozerTransformation next) {

    ImmutableList.Builder<BuildozerTransformation> transformationBuilder = ImmutableList.builder();
    if (current instanceof BuildozerBatch) {
      transformationBuilder.addAll(((BuildozerBatch) current).transformations);
    } else {
      transformationBuilder.add(current);
    }
    if (next instanceof BuildozerBatch) {
      transformationBuilder.addAll(((BuildozerBatch) next).transformations);
    } else {
      transformationBuilder.add(next);
    }
    return new BuildozerBatch(buildozerOptions, workflowOptions, transformationBuilder.build());
  }

  @Override
  public void beforeRun(TransformWork transformWork) throws ValidationException, IOException {
    for (BuildozerTransformation transformation : transformations) {
      transformation.beforeRun(transformWork);
    }
  }

  @Override
  public Iterable<BuildozerCommand> getCommands() {
    return Iterables.concat(
        Iterables.transform(transformations, BuildozerTransformation::getCommands));
  }

}
