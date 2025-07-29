/*
 * Copyright (C) 2025 Google LLC
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
package com.google.copybara.testing;

import static com.google.copybara.util.FileUtil.CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS;

import com.google.copybara.CheckoutPath;
import com.google.copybara.DestinationReader;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.starlark.java.eval.EvalException;

/** A {@link DestinationReader} that reads files from a given path for testing. */
public class PathDestinationReader extends DestinationReader {
  private final Path path;

  public PathDestinationReader(Path path) {
    this.path = path;
  }

  @Override
  public String readFile(String path) throws RepoException {
    try {
      return Files.readString(this.path.resolve(path));
    } catch (IOException e) {
      throw new RepoException("Failed to read file: " + path, e);
    }
  }

  @Override
  public void copyDestinationFiles(Object glob, Object path)
      throws RepoException, ValidationException, EvalException {
    CheckoutPath checkoutPath = SkylarkUtil.convertFromNoneable(path, null);
    Glob globObj = Glob.wrapGlob(glob, null);
    copyDestinationFilesToDirectory(
        globObj, checkoutPath.getCheckoutDir().resolve(checkoutPath.getPath()));
  }

  @Override
  public void copyDestinationFilesToDirectory(Glob glob, Path directory) throws RepoException {
    try {
      Files.createDirectories(directory.toAbsolutePath());
      FileUtil.copyFilesRecursively(
          this.path, directory.toAbsolutePath(), FAIL_OUTSIDE_SYMLINKS, glob);
    } catch (IOException | NullPointerException e) {
      throw new RepoException("Cannot copy destination files " + glob + " " + directory, e);
    }
  }

  @Override
  public boolean exists(String path) {
    return Files.exists(this.path.resolve(path));
  }
}
