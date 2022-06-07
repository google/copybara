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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.RepoException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * A walker which adds all files not matching a glob to the index of a Git repo using {@code git
 * add}.
 */
final class AddExcludedFilesToIndex {
  private final GitRepository repo;
  private final PathMatcher pathMatcher;
  private ArrayList<String> addBackSubmodules;

  AddExcludedFilesToIndex(GitRepository repo, PathMatcher pathMatcher) {
    this.repo = Preconditions.checkNotNull(repo);
    this.pathMatcher = Preconditions.checkNotNull(pathMatcher);
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
      if (!pathMatcher.matches(repo.getWorkTree().resolve(submoduleName))) {
        addBackSubmodules.add(submoduleName);
      }
    }
  }

  /**
   * Adds all the excluded files and submodules.
   */
  void add() throws RepoException, IOException {
    ExcludesFinder visitor = new ExcludesFinder(repo.getGitDir(), pathMatcher);
    Files.walkFileTree(repo.getWorkTree(), visitor);

    int size = 0;
    List<String> current = new ArrayList<>();
    for (String path : visitor.excluded) {
      current.add(path);
      size += path.length();
      // Split the executions in chunks of 6K. 8K triggers arg max in some systems, so
      // this is a reasonable number to get some batching benefit.
      if (size > 6 * 1024) {
        repo.add().force().files(current).run();
        current = new ArrayList<>();
        size = 0;
      }
    }
    if (!current.isEmpty()) {
      repo.add().force().files(current).run();
    }

    for (String addBackSubmodule : addBackSubmodules) {
      repo.simpleCommand("reset", "--", "--quiet", addBackSubmodule);
      repo.add().force().files(ImmutableList.of(addBackSubmodule)).run();
    }
  }

  private static final class ExcludesFinder extends SimpleFileVisitor<Path> {

    private final Path gitDir;
    private final PathMatcher destinationFiles;
    private final List<String> excluded = new ArrayList<>();

    private ExcludesFinder(Path gitDir, PathMatcher destinationFiles) {
      this.gitDir = gitDir;
      this.destinationFiles = destinationFiles;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      if (dir.equals(gitDir)) {
        return FileVisitResult.SKIP_SUBTREE;
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      if (!destinationFiles.matches(file)) {
        excluded.add(file.toString());
      }
      return FileVisitResult.CONTINUE;
    }

  }
}
