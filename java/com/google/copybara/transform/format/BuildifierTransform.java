/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.copybara.transform.format;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.IntentionalNoop;
import com.google.copybara.treestate.TreeState.FileState;
import com.google.copybara.util.BadExitStatusWithOutputException;
import com.google.copybara.util.CommandRunner;
import com.google.copybara.util.Glob;
import java.io.IOException;

/**
 * Transformation for running <a
 * href="https://github.com/bazelbuild/buildtools/tree/master/buildifier">buildifier</a> on source
 * files.
 */
public class BuildifierTransform implements Transformation {
  private static final Joiner JOINER = Joiner.on(",");

  private final Glob glob;

  private final String mode;
  private final String lint;
  private final ImmutableList<String> warnings;
  private final String type;

  BuildifierTransform(
      Glob glob, String mode, String lint, ImmutableList<String> warnings, String type) {
    this.glob = checkNotNull(glob);

    this.mode = checkNotNull(mode);
    this.lint = checkNotNull(lint);
    this.warnings = checkNotNull(warnings);
    this.type = checkNotNull(type);
  }

  @Override
  public String describe() {
    return "format.buildifier";
  }

  @Override
  public void transform(TransformWork work) throws IOException, ValidationException {
    Iterable<FileState> files = work.getTreeState().find(glob.relativeTo(work.getCheckoutDir()));
    transform(files, false);
    work.getTreeState().notifyModify(files);
  }

  private void transform(Iterable<FileState> files, boolean verbose) throws IOException {
    ImmutableList.Builder<String> params = ImmutableList.builder();

    // TODO(yannic): Make binary configurable.
    params.add("buildifier");

    params.add(String.format("--mode=%s", mode));
    params.add(String.format("--lint=%s", lint));
    if (warnings.size() > 0) {
      params.add(String.format("--warnings=%s", JOINER.join(warnings)));
    }
    params.add(String.format("--type=%s", type));

    for (FileState file : files) {
      params.add(file.getPath().toString());
    }

    Command command = new Command(params.build().toArray(new String[0]));
    try {
      new CommandRunner(command).withVerbose(verbose).execute();
    } catch (BadExitStatusWithOutputException e) {
      throw new IOException(
          String.format(
              "Error executing 'buildifier': %s. Stderr: \n%s",
              e.getMessage(), e.getOutput().getStderr()),
          e);
    } catch (CommandException e) {
      throw new IOException("Error executing 'buildifier'", e);
    }
  }

  @Override
  public Transformation reverse() {
    return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
  }
}
