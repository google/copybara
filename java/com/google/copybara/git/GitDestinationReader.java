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


package com.google.copybara.git;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.copybara.DestinationReader;
import com.google.copybara.exception.RepoException;
import com.google.copybara.util.Glob;
import java.nio.file.Path;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkDocumentationCategory;

/**
 * A DestinationReader for reading files from a GitDestination.
 */
@StarlarkBuiltin(
    name = "git_destination_reader",
    doc = "Handle to read from a git destination",
    category = StarlarkDocumentationCategory.TOP_LEVEL_TYPE,
    documented = false)
public class GitDestinationReader extends DestinationReader {

  private final GitRepository repository;
  private final Path workDir;
  private final GitRevision baseline;

  public GitDestinationReader(GitRepository repository, GitRevision baseline, Path workDir) {
    this.repository = checkNotNull(repository);
    this.baseline = checkNotNull(baseline);
    this.workDir = checkNotNull(workDir);
  }

  @Override
  public String readFile(String path) throws RepoException {
    return repository.readFile(baseline.getSha1(), path);
  }

  @Override
  public void copyDestinationFiles(Glob glob) throws RepoException {
    repository.checkout(glob, workDir, baseline);
  }

  @Override
  public boolean exists(String path) {
    try {
      return repository.readFile(baseline.getSha1(), path) != null;
    } catch (RepoException e) {
      return false;
    }
  }
}
