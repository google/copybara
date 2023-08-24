/*
 * Copyright (C) 2023 Google Inc.
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
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.google.copybara.CheckoutPath;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.starlark.java.syntax.Location;

/**
 * Transformation for doing rename of files
 */
public class Rename implements Transformation {

  private final String before;
  private final String after;
  private final Glob paths;
  private final boolean overwrite;
  private final boolean suffix;
  private final Location location;

  public Rename(String before, String after, Glob paths, boolean overwrite,
      boolean suffix, Location location) {
    this.before = before;
    this.after = after;
    this.paths = paths;
    this.overwrite = overwrite;
    this.suffix = suffix;
    this.location = location;
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException, RepoException {
    boolean noop = true;
    for (CheckoutPath p : work.list(paths)) {
      Path file = p.getCheckoutDir().resolve(p.getPath());
      if (!Files.isRegularFile(file)) {
        continue;
      }
      Path destination;
      if (suffix) {
        if (!file.toString().endsWith(before)) {
          continue;
        }
        destination = file.getFileSystem()
            .getPath(file.toString().replace(before, after)).normalize();
      } else {
        if (!file.endsWith(before)) {
          continue;
        }
        destination = file.getFileSystem()
            .getPath(file.toString().replace(before, after)).normalize();
      }
      checkCondition(destination.startsWith(p.getCheckoutDir()),
          "Destination file for " + destination
              + " is out of the checkout directory");
      noop = false;
      if (overwrite) {
        Files.move(file, destination, REPLACE_EXISTING);
      } else {
        Files.move(file, destination);
      }
    }
    if (noop) {
      return work.noop(String.format("Couldn't find any file to rename with '%s'", before));
    }
    return TransformationStatus.success();
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    if (overwrite) {
      throw new NonReversibleValidationException(
          "core.rename() with overwrite set is not"
              + " automatically reversible. Use core.transform to define an explicit reverse");
    }

    return
        new ExplicitReversal(new Rename(after, before, paths, overwrite, suffix, location), this);
  }

  @Override
  public String describe() {
    return "Renaming " + before;
  }
}
