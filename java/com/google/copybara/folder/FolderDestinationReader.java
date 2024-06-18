/*
 * Copyright (C) 2023 Google LLC
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

package com.google.copybara.folder;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.copybara.CheckoutPath;
import com.google.copybara.DestinationReader;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.stream.Stream;
import net.starlark.java.eval.EvalException;

/** A {@link DestinationReader} for reading files from a {@link FolderDestination}. */
public class FolderDestinationReader extends DestinationReader {
  private final Path folderPath;
  private final Path workDir;

  public FolderDestinationReader(Path folderPath, Path workDir) {
    this.folderPath = folderPath;
    this.workDir = workDir;
  }

  @Override
  public String readFile(String path) throws RepoException {
    try {
      return Files.readString(folderPath.resolve(path));
    } catch (IOException e) {
      throw new RepoException(String.format("Unable to read file %s.", path), e);
    }
  }

  @Override
  public void copyDestinationFiles(Object globObj, Object path)
      throws RepoException, ValidationException, EvalException {
    CheckoutPath checkoutPath = SkylarkUtil.convertFromNoneable(path, null);
    Glob glob = Glob.wrapGlob(globObj, null);
    if (checkoutPath == null) {
      copyDestinationFilesToDirectory(glob, workDir);
    } else {
      copyDestinationFilesToDirectory(
          glob, checkoutPath.getCheckoutDir().resolve(checkoutPath.getPath()));
    }
  }

  @Override
  public void copyDestinationFilesToDirectory(Glob glob, Path directory)
      throws RepoException, ValidationException {
    PathMatcher pathMatcher = glob.relativeTo(folderPath);

    for (String root : glob.roots()) {
      try (Stream<Path> stream = Files.walk(folderPath.resolve(root))) {
        ImmutableList<Path> matchingPaths =
            stream
                .filter(Files::isRegularFile)
                .filter(pathMatcher::matches)
                .collect(toImmutableList());

        for (Path sourcePath : matchingPaths) {
          Path targetPath = directory.resolve(folderPath.relativize(sourcePath));
          Files.createDirectories(targetPath.getParent());
          Files.copy(sourcePath, targetPath);
        }
      } catch (IOException e) {
        throw new RepoException(String.format("Failed to copy files from %s.", folderPath), e);
      }
    }
  }

  @Override
  public boolean exists(String path) {
    return Files.exists(folderPath.resolve(path));
  }
}
