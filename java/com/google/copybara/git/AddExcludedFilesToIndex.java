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
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.git.GitRepository.TreeElement;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

/**
 * A walker which adds all files not matching a glob to the index of a Git repo using {@code git
 * add}.
 */
final class AddExcludedFilesToIndex {
  private final GitRepository repo;
  private final PathMatcher pathMatcher;
  private final Path workTree;
  private ArrayList<String> addBackSubmodules;
  private final TreeSet<Path> toExclude = new TreeSet<>();

  AddExcludedFilesToIndex(GitRepository repo, PathMatcher pathMatcher) {
    this.repo = Preconditions.checkNotNull(repo);
    this.workTree = Preconditions.checkNotNull(repo.getWorkTree());
    this.pathMatcher = Preconditions.checkNotNull(pathMatcher);
  }

  void prepare(Path workdir) throws RepoException, IOException {

    HashSet<Path> included = new HashSet<>();
    ArrayList<Path> prevExcluded = new ArrayList<>();
    ImmutableList<TreeElement> head;
    try {
      head = repo.lsTree(repo.resolveReference("HEAD"), null, true, true);
    } catch (CannotResolveRevisionException e) {
      // Destination is empty. Nothing to revert
      return;
    }
    for (TreeElement treeElement : head) {
      Path relative = Paths.get(treeElement.getPath());
      if (pathMatcher.matches(workTree.resolve(treeElement.getPath()))) {
        addPathAndParents(included, relative);
      } else {
        prevExcluded.add(relative);
        if (Files.isHidden(relative)) {
          // File is not included but 'git add dir' doesn't work for 'dir/.file'.
          addPathAndParents(included, relative.getParent());
        }
      }
    }

    Files.walkFileTree(
        workdir,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            addPathAndParents(included, workdir.relativize(file));
            return super.visitFile(file, attrs);
          }
        });

    for (Path path : prevExcluded) {
      Path search = Paths.get("");
      for (int i = 0; i < path.getNameCount(); i++) {
        search = search.resolve(path.getName(i));
        if (search.equals(path)) {
          toExclude.add(search);
          break;
        } else if (!included.contains(search)) {
          toExclude.add(search);
          break;
        }
      }
    }
  }

  private void addPathAndParents(HashSet<Path> included, Path path) {
    while (path != null && !included.contains(path)) {
      Preconditions.checkArgument(!path.isAbsolute());
      included.add(path);
      path = path.getParent();
    }
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
    int size = 0;
    List<String> current = new ArrayList<>();
    for (Path path : toExclude) {
      current.add(path.toString());
      size += path.toString().length();
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
}
