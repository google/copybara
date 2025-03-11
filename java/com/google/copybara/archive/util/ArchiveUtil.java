/*
 * Copyright (C) 2025 Google LLC
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

package com.google.copybara.archive.util;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.copybara.CheckoutPath;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.remotefile.extractutil.ExtractType;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.jar.JarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

/**
 * A utility class to generate a (compressed) archive at a target directory path. Accepts a Glob to
 * filter out which files need to be archived.
 */
public final class ArchiveUtil {

  private ArchiveUtil() {}

  /**
   * Internal utility to create an archive.
   *
   * @param os generic OutputStream configured to write to the target archive file
   * @param type category of archive to generate based on target file extension
   * @param archivePath copybara checkout path to the archive file
   * @param fileFilter glob to filter the set of files to be included in the archive
   */
  public static void createArchive(
      OutputStream os, ExtractType type, CheckoutPath archivePath, @Nullable Glob fileFilter)
      throws IOException, ValidationException {
    try (ArchiveOutputStream<? extends ArchiveEntry> aos = createArchiveOutputStream(os, type)) {
      writeFiles(aos, archivePath, fileFilter);
    }
  }

  private static <T extends ArchiveEntry> void writeFiles(
      ArchiveOutputStream<T> os, CheckoutPath archivePath, @Nullable Glob fileFilter)
      throws IOException {
    // Get the current working directory
    Path workdir = archivePath.getCheckoutDir();

    // Exclude the "archive file" itself from getting added to the archived bundle.
    if (fileFilter == null) {
      fileFilter = Glob.createGlob(ImmutableList.of("**"));
    }
    fileFilter =
        Glob.difference(
            fileFilter, Glob.createGlob(ImmutableList.of(archivePath.getPath().toString())));

    try (Stream<Path> stream = Files.walk(workdir)) {
      stream
          .filter(Files::isRegularFile)
          .filter(fileFilter.relativeTo(workdir)::matches)
          .forEach(
              filePath -> {
                try {
                  Path relativePath = workdir.relativize(filePath);
                  T entry = os.createArchiveEntry(filePath, relativePath.toString());
                  os.putArchiveEntry(entry);
                  os.write(Files.readAllBytes(filePath));
                  os.closeArchiveEntry();
                } catch (IOException e) {
                  throw new VerifyException(e);
                }
              });
    }
  }

  private static ArchiveOutputStream<? extends ArchiveEntry> createArchiveOutputStream(
      OutputStream outputStream, ExtractType type) throws IOException, ValidationException {
    switch (type) {
      case JAR:
        return new JarArchiveOutputStream(outputStream);
      case ZIP:
        return new ZipArchiveOutputStream(outputStream);
      case TAR:
        return new TarArchiveOutputStream(outputStream);
      case TAR_GZ:
        return new TarArchiveOutputStream(new GzipCompressorOutputStream(outputStream));
      case TAR_XZ:
        return new TarArchiveOutputStream(new XZCompressorOutputStream(outputStream));
    }
    throw new ValidationException(
        String.format("Failed to get archive output stream for file type: %s", type));
  }
}
