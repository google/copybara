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

package com.google.copybara.git;

import com.google.common.base.Splitter;
import com.google.copybara.RepoException;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;

/**
 * A walker which adds all files not matching a glob to the index of a Git repo using
 * {@code git add}.
 */
final class AddExcludedFilesToIndexVisitor extends SimpleFileVisitor<Path> {
  private final GitRepository repo;
  private final PathMatcher destinationFiles;
  private ArrayList<String> addBackSubmodules;

  AddExcludedFilesToIndexVisitor(GitRepository repo, Glob destinationFilesGlob) {
    this.repo = repo;
    this.destinationFiles = destinationFilesGlob.relativeTo(repo.getWorkTree());
  }

  /**
   * Finds and records the path of all submodules. This should be called when they are not staged
   * for deletion.
   */
  void findSubmodules(Console console) throws RepoException {
    addBackSubmodules = new ArrayList<>();

    String submoduleStatus = repo.simpleCommand("submodule", "status").getStdout();
    for (String line : Splitter.on('\n').omitEmptyStrings().split(submoduleStatus)) {
      String submoduleName = line.replaceFirst("^-[0-9a-f]{40} ", "");
      if (submoduleName.equals(line)) {
        console.warn("Cannot parse line from 'git submodule status': " + line);
        continue;
      }
      if (!destinationFiles.matches(repo.getWorkTree().resolve(submoduleName))) {
        addBackSubmodules.add(submoduleName);
      }
    }
  }

  @Override
  public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
      throws IOException {
    if (dir.equals(repo.getGitDir())) {
      return FileVisitResult.SKIP_SUBTREE;
    }
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
    if (!destinationFiles.matches(file)) {
      try {
        repo.simpleCommand("add", "-f", "--", file.toString());
      } catch (RepoException e) {
        throw new IOException(e);
      }
    }
    return FileVisitResult.CONTINUE;
  }

  /**
   * Adds all the excluded files and submodules.
   */
  void add() throws RepoException, IOException {
    Files.walkFileTree(repo.getWorkTree(), this);
    for (String addBackSubmodule : addBackSubmodules) {
      repo.simpleCommand("reset", "--", "--quiet", addBackSubmodule);
      repo.simpleCommand("add", "-f", "--", addBackSubmodule);
    }
  }
}
