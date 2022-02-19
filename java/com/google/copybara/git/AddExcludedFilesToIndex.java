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
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

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
  void add(Console console) throws RepoException, IOException {
    console.progress("Git Destination: Walking Tree for Exclusions");
    ExcludesFinder visitor = new ExcludesFinder(repo.getGitDir(), pathMatcher);
    Files.walkFileTree(repo.getWorkTree(), visitor);

    console.progress("Git Destination: Compressing Tree");
    visitor.excludedTree.Compress();

    console.progress("Git Destination: Adding Excluded Files");
    int size = 0;
    List<String> current = new ArrayList<>();
    for (String path : visitor.excludedTree.Excluded()) {
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

    console.progress("Git Destination: Adding submodules");
    for (String addBackSubmodule : addBackSubmodules) {
      repo.simpleCommand("reset", "--", "--quiet", addBackSubmodule);
      repo.add().force().files(ImmutableList.of(addBackSubmodule)).run();
    }
  }

  /**
   * A tree representation of the set of paths in the repo.
   *
   * Used to track which paths we're excluding, or equivalently the set of paths we're
   * going to `git add` above.
   *
   * Each interior node is a directory, and each leaf is a path (either a directory or
   * a file), with the leaves marked as included or not
   */
  private static final class PathTree {
    /**
     * Internal representation of each leaf of the tree - the last component of its
     * path, and whether it's included or not
     */
    private static final class PathTreeLeaf {
      private final String filename;
      private boolean included;

      private PathTreeLeaf(String filename, boolean included) {
        this.filename = filename;
        this.included = included;
      }
    }

    private String dirname;
    private final ArrayList<PathTree> kids;
    private final ArrayList<PathTreeLeaf> leaves;

    public PathTree(String dirname) {
      this.dirname = dirname;
      this.kids = new ArrayList<>();
      this.leaves = new ArrayList<>();
    }

    /**
     * Add a new path to the tree.
     *
     * The new path will be added as a leaf node, and marked as included/excluded.
     */
    public void AddPath(Path path, boolean included) {
      // Set the root dirname lazily when we get the first path
      if (dirname == null) {
        dirname = path.getRoot().toString();
      }

      // Walk down the tree from the root of the tree (and the root of the path)
      PathTree currTree = this;

      for (Path component : path.getParent()) {
        // Do we already have an entry for this subdirectory?
        boolean foundKid = false;
        for (PathTree kid : currTree.kids) {
          if (kid.dirname.equals(component.toString())) {
            currTree = kid;
            foundKid = true;
            break;
          }
        }

        // We don't, so create it
        if (!foundKid) {
          PathTree newTree = new PathTree(component.toString());
          currTree.kids.add(newTree);
          currTree = newTree;
        }
      }

      // Now add the filename to the bottom subdirectory
      currTree.leaves.add(new PathTreeLeaf(path.getFileName().toString(), included));
    }

    /**
     * Compresses the tree.
     *
     * This takes subtrees where all files are excluded and replaces them with a single
     * entry for the root of the subtree.  This means when we go to call `git add`, we
     * can just pass the root of the subdirectory, instead of every file inside it.
     *
     * This is done bottom-up, recursively.  Each directory that has no complex
     * subdirectories under it, and where all files are excluded, can be deleted from
     * its parent's `kids` list and put instead in its `leaves` list as a single entry
     * for the directory.  We go bottom-up, so that entire trees can be replaced this
     * way.
     *
     * Returns `true` if the PathTree can be compressed to a single entry
     */
    public boolean Compress() {
      Iterator<PathTree> itr = kids.iterator();
      while (itr.hasNext()) {
        PathTree subTree = itr.next();
        boolean compressed = subTree.Compress();
        if (compressed) {
          leaves.add(new PathTreeLeaf(subTree.dirname, false));
          itr.remove();
        }
      }

      if (!kids.isEmpty()) {
        return false;
      }

      for (PathTreeLeaf leaf : leaves) {
        if (leaf.included) {
          return false;
        }
      }

      return true;
    }

    /**
     * Helper to recursively add the excluded paths in this tree to the list `results`.
     *
     * Each path is prepended with the string `prefix`, i.e. the path from the root to
     * this PathTree
     */
    private void AddExcluded(ArrayList<String> results, String prefix) {
      for (PathTree kid : kids) {
        String childPath = prefix == null ? dirname : Paths.get(prefix, dirname).toString();
        kid.AddExcluded(results, childPath);
      }

      for (PathTreeLeaf leaf : leaves) {
        if (!leaf.included) {
          String childPath = prefix == null
                                 ? Paths.get(dirname, leaf.filename).toString()
                                 : Paths.get(prefix, dirname, leaf.filename).toString();
          results.add(childPath);
        }
      }
    }

    /**
     * Return the list of excluded paths in this tree.
     */
    public ArrayList<String> Excluded() {
      ArrayList<String> result = new ArrayList<>();
      AddExcluded(result, dirname);
      return result;
    }
  }

  private static final class ExcludesFinder extends SimpleFileVisitor<Path> {

    private final Path gitDir;
    private final PathMatcher destinationFiles;
    private final PathTree excludedTree = new PathTree(null);

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
      excludedTree.AddPath(file, destinationFiles.matches(file));
      return FileVisitResult.CONTINUE;
    }
  }
}
