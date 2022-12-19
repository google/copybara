/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.util;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.LocalParallelizer;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.copybara.shell.CommandException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;

/**
 * A tool that assists with Merge Imports
 *
 * <p>Merge Imports allow users to persist changes in non-source-of-truth destinations. Destination
 * only changes are defined by files that exist in the destination workdir, but not in the origin
 * baseline or in the origin workdir.
 */
public final class MergeImportTool {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Console console;
  private final CommandLineDiffUtil commandLineDiffUtil;
  private final int threadsForMergeImport;
  private static final int THREADS_MIN_SIZE = 5;

  // TODO refactor to accept a diffing tool
  public MergeImportTool(Console console, CommandLineDiffUtil commandLineDiffUtil,
      int threadsForMergeImport) {
    this.console = console;
    this.commandLineDiffUtil = commandLineDiffUtil;
    this.threadsForMergeImport = threadsForMergeImport;
  }

  /**
   * A command that shells out to a diffing tool to merge files in the working directories
   *
   * <p>The origin is treated as the source of truth. Files that exist at baseline and destination
   * but not in the origin will be deleted. Files that exist in the destination but not in the
   * origin or in the baseline will be considered "destination only" and propagated.
   *
   * @param originWorkdir The working directory for the origin repository, already populated by the
   *     caller
   * @param destinationWorkdir A copy of the destination repository state, already populated by the
   *     caller
   * @param baselineWorkdir A copy of the baseline repository state, already populated by the caller
   * @param diffToolWorkdir A working directory for the CommandLineDiffUtil
   */
  public void mergeImport(
      Path originWorkdir, Path destinationWorkdir, Path baselineWorkdir, Path diffToolWorkdir)
      throws IOException, ValidationException {
    HashSet<Path> visitedSet = new HashSet<>();
    HashSet<Path> mergeErrorPaths = new HashSet<>();
    HashSet<Path> troublePaths = new HashSet<>();
    HashSet<FilePathInformation> filesToProcess = new HashSet<>();

    SimpleFileVisitor<Path> originWorkdirFileVisitor =
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isSymbolicLink()) {
              return FileVisitResult.CONTINUE;
            }
            Path relativeFile = originWorkdir.relativize(file);
            Path baselineFile = baselineWorkdir.resolve(relativeFile);
            Path destinationFile = destinationWorkdir.resolve(relativeFile);
            if (!Files.exists(destinationFile) || !Files.exists(baselineFile)) {
              return FileVisitResult.CONTINUE;
            }
            filesToProcess.add(
                FilePathInformation.create(file, relativeFile, baselineFile, destinationFile));

            return FileVisitResult.CONTINUE;
          }
        };

    SimpleFileVisitor<Path> destinationWorkdirFileVisitor =
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (attrs.isSymbolicLink()) {
              return FileVisitResult.CONTINUE;
            }
            Path relativeFile = destinationWorkdir.relativize(file);
            Path originFile = originWorkdir.resolve(relativeFile);
            Path baselineFile = baselineWorkdir.resolve(relativeFile);
            if (visitedSet.contains(relativeFile)) {
              return FileVisitResult.CONTINUE;
            }
            // destination only file - keep it
            if (!Files.exists(originFile) && !Files.exists(baselineFile)) {
              Files.createDirectories(originWorkdir.resolve(relativeFile).getParent());
              Files.copy(file, originWorkdir.resolve(relativeFile));
            }
            // file was deleted in origin, propagate to destination
            if (!Files.exists(originFile) && Files.exists(baselineFile)) {
              Files.delete(file);
            }
            return FileVisitResult.CONTINUE;
          }
        };

    Files.walkFileTree(originWorkdir, originWorkdirFileVisitor);
    logger.atInfo().log("Using %d thread(s) for merging files", threadsForMergeImport);
    List<OperationResults> results =
        new LocalParallelizer(threadsForMergeImport, THREADS_MIN_SIZE).run(
            ImmutableSet.copyOf(filesToProcess), new BatchCaller(diffToolWorkdir));
    for (OperationResults result : results) {
      visitedSet.addAll(result.visitedFiles());
      mergeErrorPaths.addAll(result.mergeErrorPaths());
      troublePaths.addAll(result.troublePaths());
    }
    Files.walkFileTree(destinationWorkdir, destinationWorkdirFileVisitor);
    if (!mergeErrorPaths.isEmpty()) {
      mergeErrorPaths.forEach(
          path -> console.warn(String.format("Merge error for path %s", path.toString())));
    }
    if (!troublePaths.isEmpty()) {
      troublePaths.forEach(
          path ->
              console.warn(String.format("diff3 exited with code 2 for path %s, skipping", path)));
    }
  }

  private class BatchCaller
      implements LocalParallelizer.TransformFunc<FilePathInformation, OperationResults> {

    private final Path diffToolWorkdir;

    BatchCaller(Path diffToolWorkdir) {
      this.diffToolWorkdir = diffToolWorkdir;
    }

    @Override
    public OperationResults run(Iterable<FilePathInformation> elements) throws IOException {
      HashSet<Path> visitedSet = new HashSet<>();
      HashSet<Path> mergeErrorPaths = new HashSet<>();
      HashSet<Path> troublePaths = new HashSet<>();

      for (FilePathInformation paths : elements) {
        Path file = paths.file();
        Path relativeFile = paths.relativeFile();
        Path baselineFile = paths.baselineFile();
        Path destinationFile = paths.destinationFile();
        CommandOutputWithStatus output;
        if (!Files.exists(destinationFile) || !Files.exists(baselineFile)) {
          continue;
        }
        try {
          output = commandLineDiffUtil.diff(file, destinationFile, baselineFile, diffToolWorkdir);
          visitedSet.add(relativeFile);
          if (output.getTerminationStatus().getExitCode() == 1) {
            mergeErrorPaths.add(file);
          }
          if (output.getTerminationStatus().getExitCode() == 2) {
            troublePaths.add(file);
          }
        } catch (CommandException e) {
          throw new IOException(
              String.format("Could not execute diff tool %s", commandLineDiffUtil.diffBin), e);
        }
        Files.write(file, output.getStdoutBytes());
      }

      return OperationResults.create(
          ImmutableSet.copyOf(visitedSet),
          ImmutableSet.copyOf(mergeErrorPaths),
          ImmutableSet.copyOf(troublePaths));
    }
  }

  /** A class that will contain Path information for a file from the origin workdir. */
  @AutoValue
  abstract static class FilePathInformation {
    static FilePathInformation create(
        Path file, Path relativeFile, Path baselineFile, Path destinationFile) {
      return new AutoValue_MergeImportTool_FilePathInformation(
          file, relativeFile, baselineFile, destinationFile);
    }

    abstract Path file();

    abstract Path relativeFile();

    abstract Path baselineFile();

    abstract Path destinationFile();
  }

  /**
   * Contains the results of a batch job performed by LocalParallelizer, which includes the Paths of
   * which files were visited, and which files had merge errors reported by the external diffing
   * tool.
   */
  @AutoValue
  abstract static class OperationResults {
    static OperationResults create(
        ImmutableSet<Path> visitedFiles,
        ImmutableSet<Path> mergeErrorPaths,
        ImmutableSet<Path> troublePaths) {
      return new AutoValue_MergeImportTool_OperationResults(
          visitedFiles, mergeErrorPaths, troublePaths);
    }

    abstract ImmutableSet<Path> visitedFiles();

    abstract ImmutableSet<Path> mergeErrorPaths();

    abstract ImmutableSet<Path> troublePaths();
  }
}
