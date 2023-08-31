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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.util.DiffUtil.DiffFile;
import com.google.copybara.util.DiffUtil.DiffFile.Operation;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import javax.annotation.Nullable;

/** A utility class to automatically generate patch files */
public final class AutoPatchUtil {

  private AutoPatchUtil() {}

  /**
   * Given two paths, generates patch files per-file
   *
   * <p>Does not generate any patch files where there is no diff. Patch files are generated using
   * git diff.
   *
   * @param originWorkdir workdir used on lhs of diffing statement, should be baseline or origin
   *     workdir
   * @param destinationWorkdir workdir used on rhs of diffing statement, should be destination
   *     workdir
   * @param directoryPrefix prefix to all filenames. patch files are written inside this directory.
   *     e.g. if this is third_party/foo and a file is third_party/foo/bar/bar.txt, and
   *     patchFilePrefix is PATCHES, we will write to third_party/foo/PATCHES/bar/bar.txt.patch
   * @param patchFileDirectory optional directory, relative to directory prefix, in which to place
   *     patch files. See directoryPrefix description.
   * @param verbose forwards verbose setting to diffing command
   * @param environment environment variables
   * @param patchFilePrefix optional text prefix applied to the contents of all patch files
   * @param patchFileNameSuffix suffix used for patch files e.g. .patch
   * @param rootDirectory directory in which to write all patch files (using above subdirectories)
   * @param stripFileNames when true, strip filenames and line numbers from patch file contents
   * @param fileMatcher used to prevent AutoPatchUtil from running on certain files
   */
  public static void generatePatchFiles(
      Path originWorkdir,
      Path destinationWorkdir,
      Path directoryPrefix,
      @Nullable String patchFileDirectory,
      boolean verbose,
      Map<String, String> environment,
      @Nullable String patchFilePrefix,
      String patchFileNameSuffix,
      Path rootDirectory,
      boolean stripFileNames,
      Glob fileMatcher)
      throws IOException, InsideGitDirException {
    if (patchFilePrefix == null) {
      patchFilePrefix = "";
    }
    if (patchFileDirectory == null) {
      patchFileDirectory = "";
    }
    ImmutableList<DiffFile> diffFiles =
        DiffUtil.diffFiles(originWorkdir, destinationWorkdir, verbose, environment);
    ImmutableSet<String> diffFileNames =
        diffFiles.stream().map(DiffFile::getName).collect(toImmutableSet());
    // TODO: make this configurable
    for (DiffFile diffFile : diffFiles) {
      if (!diffFile.getOperation().equals(Operation.MODIFIED)) {
        continue;
      }
      if (!fileMatcher.relativeTo(Paths.get("")).matches(Path.of("/".concat(diffFile.getName())))) {
        continue;
      }
      String fileName = diffFile.getName();
      Path onePath = originWorkdir.resolve(fileName);
      Path otherPath = destinationWorkdir.resolve(fileName);
      if (!Files.exists(otherPath)) {
        continue;
      }
      String diffString =
          new String(
              DiffUtil.diffFileWithIgnoreCrAtEol(
                  originWorkdir.getParent(), onePath, otherPath, verbose, environment),
              UTF_8);
      if (Strings.isNullOrEmpty(diffString)) {
        // diff was carriage return at end of line
        continue;
      }
      if (stripFileNames) {
        diffString = stripFileNamesAndLineNumbers(diffString);
      }
      Path patchFilePath =
          derivePatchFileName(
              directoryPrefix, patchFileDirectory, patchFileNameSuffix, rootDirectory, fileName);
      Files.createDirectories(patchFilePath.getParent());
      Files.writeString(patchFilePath, patchFilePrefix.concat(diffString));
    }
    final String finalPatchFileDirectory = patchFileDirectory;
    SimpleFileVisitor<Path> originFileVisitor =
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Path patchFileName =
                derivePatchFileName(
                    directoryPrefix,
                    finalPatchFileDirectory,
                    patchFileNameSuffix,
                    rootDirectory,
                    originWorkdir.relativize(file).toString());
            // There is no longer a diff, but a patch file exists
            if (!diffFileNames.contains(originWorkdir.relativize(file).toString())
                && Files.exists(destinationWorkdir.resolve(patchFileName))) {
              Files.delete(destinationWorkdir.resolve(patchFileName));
            }
            return FileVisitResult.CONTINUE;
          }
        };
    SimpleFileVisitor<Path> destinationFileVisitor =
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (!file.toString().endsWith(patchFileNameSuffix)) {
              return FileVisitResult.CONTINUE;
            }
            Path fileName =
                destinationWorkdir.relativize(
                    Path.of(file.toString().replace(patchFileNameSuffix, "")));
            fileName = Path.of(fileName.toString().replace(directoryPrefix.toString(), ""));
            if (fileName.isAbsolute()) {
              fileName = fileName.subpath(0, fileName.getNameCount());
            }
            if (!finalPatchFileDirectory.isBlank()) {
              fileName = Path.of(fileName.toString().replace(finalPatchFileDirectory, ""));
            }
            if (fileName.isAbsolute()) {
               fileName = fileName.subpath(0, fileName.getNameCount());
            }

            // patch file exists, but the origin file was deleted
            // if patch file exists both in destination and root directory (which may also be the
            // destination directory), delete in the root directory
            // By getting to this point, we know that destination patch file exists
            Path originFile = originWorkdir.resolve(directoryPrefix).resolve(fileName);
            Path rootDirectoryPatchFile =
                rootDirectory
                    .resolve(directoryPrefix)
                    .resolve(finalPatchFileDirectory)
                    .resolve(Path.of(fileName.toString().concat(patchFileNameSuffix)));
            if (!Files.exists(originFile) && Files.exists(rootDirectoryPatchFile)) {
              Files.delete(rootDirectoryPatchFile);
            }
            return FileVisitResult.CONTINUE;
          }
        };
    Files.walkFileTree(originWorkdir, originFileVisitor);
    Files.walkFileTree(destinationWorkdir, destinationFileVisitor);
  }

  public static void reversePatchFiles(
      Path diffRoot, Path patchDir, String fileSuffix, Map<String, String> environment)
      throws IOException {
    // obtain list of patch files to apply
    ImmutableList.Builder<Path> files = ImmutableList.builder();
    Files.walkFileTree(
        patchDir,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (file.toString().endsWith(fileSuffix)) {
              files.add(file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    DiffUtil.reverseApplyPatches(files.build(), diffRoot, environment);
  }

  public static Glob getAutopatchGlob(String directoryPrefix, @Nullable String directory) {
    Path autopatchDirectoryPath = Path.of(directoryPrefix);
    if (directory != null) {
      autopatchDirectoryPath = autopatchDirectoryPath.resolve(directory);
    }
    autopatchDirectoryPath = autopatchDirectoryPath.resolve("**");
    return Glob.createGlob(ImmutableList.of(autopatchDirectoryPath.toString()));
  }

  private static Path derivePatchFileName(
      Path directoryPrefix,
      String patchFileDirectory,
      String patchFileNameSuffix,
      Path rootDirectory,
      String fileName) {
    Path fileRelativeDirectoryPrefix =
        directoryPrefix.relativize(Path.of(fileName.concat(patchFileNameSuffix)));
    return rootDirectory
        .resolve(directoryPrefix)
        .resolve(Path.of(patchFileDirectory).resolve(fileRelativeDirectoryPrefix));
  }

  // Reimplementation of golang packaging code
  private static String stripFileNamesAndLineNumbers(String diffString) {
    String parsedDiffString = diffString.substring(diffString.indexOf("\n@@") + "\n".length());
    String diffChunk = "";
    int i = 0;
    while (parsedDiffString.length() > 0) {
      i = parsedDiffString.indexOf("\n@@") + "\n".length();
      if (i <= 0 || i >= parsedDiffString.length()) {
        diffChunk = diffChunk.concat(parsedDiffString);
        break;
      }
      diffChunk = diffChunk.concat(parsedDiffString.substring(0, i));
      parsedDiffString = parsedDiffString.substring(i);
    }
    // strip line numbers - of format @@ -1,1 +1,1 @@, sometimes of form @@ -1 +1 @@
    diffChunk = diffChunk.replaceAll("@@ -(\\d+)(,\\d+)? \\+(\\d+)(,\\d+)? @@", "@@");
    return diffChunk;
  }
}
