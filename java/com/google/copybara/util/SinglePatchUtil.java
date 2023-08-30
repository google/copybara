/*
 * Copyright (C) 2023 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

/**
 * Contains utilities for working with SinglePatch objects
 */
public class SinglePatchUtil {

  /**
   * Create a SinglePatch object from two folders containing separate versions of the repository.
   *
   * <p>The location of the two folders matters. The SinglePatch includes a diff between the two
   * folders which the parent of the destination will be used as the working directory for when
   * created. The locations of the folders will affect the paths that appear in the diff output.
   *
   * @param destination is the version containing all the destination only changes.
   * @param baseline is the version to diff against.
   */
  public static SinglePatch generateSinglePatch(Path destination, Path baseline,
      HashFunction hashFunction, Map<String, String> environment)
      throws IOException, InsideGitDirException {
    ImmutableMap.Builder<String, String> hashesBuilder = ImmutableMap.builder();
    Files.walkFileTree(destination, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
          throws IOException {
        HashCode hashCode = MoreFiles.asByteSource(file).hash(hashFunction);
        hashesBuilder.put(destination.relativize(file).toString(),
            new String(hashCode.asBytes(), UTF_8));
        return FileVisitResult.CONTINUE;
      }

    });
    byte[] diff = DiffUtil.diff(destination, baseline, false, environment);
    return new SinglePatch(hashesBuilder.build(), diff);
  }

}
