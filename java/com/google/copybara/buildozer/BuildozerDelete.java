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

package com.google.copybara.buildozer;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.buildozer.BuildozerOptions.BuildozerCommand;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * A transformation which deletes build target and reverses to create the same target.
 */
public final class BuildozerDelete implements BuildozerTransformation {

  private final BuildozerOptions options;
  private final WorkflowOptions workflowOptions;
  private final Target target;
  @Nullable private final BuildozerCreate recreateAs;

  BuildozerDelete(
      BuildozerOptions options,
      WorkflowOptions workflowOptions,
      Target target,
      @Nullable BuildozerCreate recreateAs) {
    this.options = checkNotNull(options, "options");
    this.workflowOptions = checkNotNull(workflowOptions, "workflowOptions");
    this.target = checkNotNull(target, "target");
    this.recreateAs = recreateAs;
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    try {
      options.run(work.getConsole(), work.getCheckoutDir(), getCommands());
      return TransformationStatus.success();
    } catch (TargetNotFoundException e) {
      return TransformationStatus.noop(e.getMessage());
    }
  }

  @Override
  public String describe() {
    return "buildozer.delete " + target;
  }

  @Override
  public boolean canJoin(Transformation transformation) {
    return BuildozerBatch.isBuildozer(transformation);
  }

  @Override
  public Transformation join(Transformation next) {
    return BuildozerBatch.join(options, workflowOptions, this, (BuildozerTransformation) next);
  }

  @Override
  public Iterable<BuildozerCommand> getCommands() {
    return ImmutableList.of(new BuildozerCommand(target.toString(), "delete"));
  }

  @Override
  public BuildozerCreate reverse() throws NonReversibleValidationException {
    if (recreateAs == null) {
      throw new NonReversibleValidationException(
          "This buildozer.delete is not reversible. Please specify at least rule_type to make it"
              + " reversible.");
    }
    return recreateAs;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("target", target)
        .add("recreateAs", recreateAs)
        .toString();
  }
}
