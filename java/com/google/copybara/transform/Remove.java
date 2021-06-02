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

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import java.io.IOException;
import net.starlark.java.syntax.Location;

/**
 * We might promote this to a Skylark transform. But because we already have origin_files,
 * that works better with reversible workflows, this is a bad idea except for explicit reversals
 * of core.copy.
 */
public class Remove implements Transformation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Glob glob;
  private final Location location;

  public Remove(Glob glob, Location location) {
    this.glob = Preconditions.checkNotNull(glob);
    this.location = location;
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    // TODO(malcon): Fix ConfigValidator and move this logic there.
    checkCondition(work.isInsideExplicitTransform(),
        "core.remove() is only mean to be used inside core.transform for reversing"
            + " transformations like core.copy(). Please use origin_files exclude for"
            + " filtering out files.");

    int numDeletes = FileUtil.deleteFilesRecursively(work.getCheckoutDir(),
        glob.relativeTo(work.getCheckoutDir()));
    logger.atInfo().log("Deleted %d files for glob: %s", numDeletes, glob);
    if (numDeletes  == 0) {
      return TransformationStatus.noop(glob + " didn't delete any file");
    }
    return TransformationStatus.success();
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    throw new NonReversibleValidationException("core.remove is not reversible");
  }

  @Override
  public String describe() {
    return "Removing " + glob;
  }

  @Override
  public Location location() {
    return location;
  }
}
