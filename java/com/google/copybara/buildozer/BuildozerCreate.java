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
import com.google.copybara.exception.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;

/**
 * A transformation which creates a new build target and reverses to delete the same target.
 */
public final class BuildozerCreate implements BuildozerTransformation {

  private final BuildozerOptions options;
  private final WorkflowOptions workflowOptions;
  private final Target target;
  private final String ruleType;
  private final RelativeTo relativeTo;
  private final ImmutableList<String> commands;

  static final class RelativeTo {
    final String args;

    private static void validateTargetName(String targetName) throws EvalException {
      if (targetName.contains(":")) {
        throw Starlark.errorf(
            "unexpected : in target name (did you include the package by mistake?) - '%s'",
            targetName);
      }
    }

    RelativeTo(String before, String after) throws EvalException {
      if (!before.isEmpty() && !after.isEmpty()) {
        throw new EvalException(
            "cannot specify both 'before' and 'after' in the target create arguments");
      }

      if (!before.isEmpty()) {
        validateTargetName(before);
        this.args = "before " + before;
      } else if (!after.isEmpty()) {
        validateTargetName(after);
        this.args = "after " + after;
      } else {
        this.args = "";
      }
    }

    @Override
    public String toString() {
      return args;
    }
  }

  BuildozerCreate(
      BuildozerOptions options,
      WorkflowOptions workflowOptions,
      Target target,
      String ruleType,
      RelativeTo relativeTo,
      // TODO(matvore): Accept Iterable<Command> once YAML support is removed.
      Iterable<String> commands) {
    this.options = checkNotNull(options, "options");
    this.workflowOptions = checkNotNull(workflowOptions, "workflowOptions");
    this.target = checkNotNull(target, "target");
    this.ruleType = checkNotNull(ruleType, "ruleType");
    this.relativeTo = checkNotNull(relativeTo, "relativeTo");
    this.commands = ImmutableList.copyOf(commands);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    beforeRun(work);
    try {
      options.run(work.getConsole(), work.getCheckoutDir(), getCommands());
      return TransformationStatus.success();
    } catch (TargetNotFoundException e) {
      // This should not happen for creation. If it happens, it is due to a file error.
      throw new ValidationException(e.getMessage());
    }
  }

  @Override
  public void beforeRun(TransformWork work) throws ValidationException, IOException {
    Path buildFilePath = work.getCheckoutDir().resolve(getTargetBuildFile());
    if (!Files.exists(buildFilePath)) {
      // Alert the user that the package to contain this target doesn't have a BUILD file, since
      // this may be a configuration error.
      work.getConsole().info(
          String.format("BUILD file to contain %s doesn't exist. Creating now.", target));
      Files.createDirectories(buildFilePath.getParent());
      Files.write(buildFilePath, new byte[0]);
    }
  }

  private String getTargetBuildFile() {
    String pkg = target.getPackage();
    // pkg can be empty (e.g. ":foo"), which should create targets in the workdir root, i.e. ./BUILD
    return pkg + (pkg.isEmpty() ? "." : "") + "/BUILD";
  }

  @Override
  public Iterable<BuildozerCommand> getCommands() {
    List<BuildozerCommand> result = new ArrayList<>();
    result.add(new BuildozerCommand(ImmutableList.of(getTargetBuildFile()),
        String.format("new %s %s %s", ruleType, target.getName(), relativeTo.args)));
    for (String command : this.commands) {
      result.add(new BuildozerCommand(ImmutableList.of(target.toString()), command));
    }
    return result;
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
  public String describe() {
    return "buildozer.create " + target;
  }

  @Override
  public BuildozerDelete reverse() {
    return new BuildozerDelete(options, workflowOptions, target, this);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("target", target)
        .add("ruleType", ruleType)
        .add("relativeTo", relativeTo)
        .add("commands", commands)
        .toString();
  }
}
