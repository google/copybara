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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * Transformation the moves (renames) a single file or directory.
 */
public class Move implements Transformation {

  private final String before;
  private final String after;
  private final Glob paths;
  private final boolean overwrite;
  @Nullable
  private final Location location;
  private final WorkflowOptions workflowOptions;

  private Move(String before, String after, Glob paths, boolean overwrite,
      @Nullable Location location, WorkflowOptions workflowOptions) {
    this.before = Preconditions.checkNotNull(before);
    this.after = Preconditions.checkNotNull(after);
    this.paths = paths;
    this.overwrite = overwrite;
    this.location = location;
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
  }

  public static Move fromConfig(
      String before, String after, WorkflowOptions workflowOptions, Glob paths,
      boolean overwrite, Location location)
      throws EvalException {
    return new Move(
        validatePath(location, before),
        validatePath(location, after),
        paths,
        overwrite,
        location,
        workflowOptions);
  }

  @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("before", before)
          .add("after", after)
          .toString();
    }

  @Override
  public void transform(TransformWork work) throws IOException, ValidationException {
      work.getConsole().progress("Moving " + this.before);
      Path before = work.getCheckoutDir().resolve(this.before);
      if (!Files.exists(before)) {
        workflowOptions.reportNoop(
            work.getConsole(),
            String.format("Error moving '%s'. It doesn't exist in the workdir", this.before));
        return;
      }
      Path after = work.getCheckoutDir().resolve(this.after);
      if (Files.isDirectory(after, LinkOption.NOFOLLOW_LINKS)
          && after.startsWith(before)) {
        // When moving from a parent dir to a sub-directory, make sure after doesn't already have
        // files in it - this is most likely a mistake.
        new VerifyDirIsEmptyVisitor(after).walk();
      }
      createParentDirs(after);
      try {
        boolean beforeIsDir = Files.isDirectory(before);
        if (paths != Glob.ALL_FILES && !beforeIsDir) {
          throw new ValidationException(
              "Cannot use user defined 'paths' filter when the 'before' is not a directory: "
                  + paths);
        }
        Files.walkFileTree(before,
            new MovingVisitor(before, after, beforeIsDir ? paths.relativeTo(before) : null,
                overwrite));
      } catch (FileAlreadyExistsException e) {
        throw new ValidationException(
            String.format("Cannot move file to '%s' because it already exists", e.getFile()));
      }
  }

  @Override
  public Move reverse() throws NonReversibleValidationException {
    if (overwrite) {
      throw new NonReversibleValidationException(location, "core.move() with overwrite set is not"
          + " automatically reversible. Use core.transform to define an explicit reverse");
    }
    return new Move(after, before, paths, /*overwrite=*/false, location, workflowOptions);
  }

  private void createParentDirs(Path after) throws IOException, ValidationException {
    try {
      Files.createDirectories(after.getParent());
    } catch (FileAlreadyExistsException e) {
      // This exception message is particularly bad and we don't want to treat it as unhandled
      throw new ValidationException(String.format(
          "Cannot create '%s' because '%s' already exists and is not a directory",
          after.getParent(), e.getFile()));
    }
  }

  @Override
  public String describe() {
    return "Moving " + before;
  }

  private static String validatePath(Location location, String strPath) throws EvalException {
    try {
      return FileUtil.checkNormalizedRelative(strPath);
    } catch (IllegalArgumentException e) {
      throw new EvalException(location, "'" + strPath + "' is not a valid path", e);
    }
  }
}
