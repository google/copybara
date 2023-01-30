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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.copybara.util.DiffUtil.DiffFile;
import com.google.copybara.util.DiffUtil.DiffFile.Operation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
   * @param lhsWorkdir workdir used on lhs of diffing statement e.g. baseline workdir
   * @param rhsWorkdir workdir used on rhs of diffing statement e.g. destination workdir
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
   */
  public static void generatePatchFiles(
      Path lhsWorkdir,
      Path rhsWorkdir,
      Path directoryPrefix,
      @Nullable String patchFileDirectory,
      boolean verbose,
      Map<String, String> environment,
      @Nullable String patchFilePrefix,
      String patchFileNameSuffix,
      Path rootDirectory,
      boolean stripFileNames)
      throws IOException, InsideGitDirException {
    if (patchFilePrefix == null) {
      patchFilePrefix = "";
    }
    if (patchFileDirectory == null) {
      patchFileDirectory = "";
    }
    ImmutableList<DiffFile> diffFiles =
        DiffUtil.diffFiles(lhsWorkdir, rhsWorkdir, verbose, environment);
    // TODO: make this configurable
    for (DiffFile diffFile : diffFiles) {
      if (!diffFile.getOperation().equals(Operation.MODIFIED)) {
        continue;
      }
      String fileName = diffFile.getName();
      Path onePath = lhsWorkdir.resolve(fileName);
      Path otherPath = rhsWorkdir.resolve(fileName);
      if (!Files.exists(otherPath)) {
        continue;
      }
      String diffString =
          new String(
              DiffUtil.diffFileWithIgnoreCrAtEol(onePath, otherPath, verbose, environment), UTF_8);
      if (stripFileNames) {
        diffString = stripFileNamesAndLineNumbers(diffString);
      }
      Path fileRelativeDirectoryPrefix =
          directoryPrefix.relativize(Path.of(fileName.concat(patchFileNameSuffix)));
      Path patchFilePath =
          rootDirectory
              .resolve(directoryPrefix)
              .resolve(Path.of(patchFileDirectory).resolve(fileRelativeDirectoryPrefix));
      Files.createDirectories(patchFilePath.getParent());
      Files.writeString(patchFilePath, patchFilePrefix.concat(diffString));
    }
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
