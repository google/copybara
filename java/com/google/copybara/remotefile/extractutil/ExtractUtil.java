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
package com.google.copybara.remotefile.extractutil;
import java.nio.file.Path;
import com.google.common.io.MoreFiles;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import javax.annotation.Nullable;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

/**
 * A utility to extract a compressed archive to a target folder. Accepts a Glob to filter out which
 * files should be copied.
 */
public final class ExtractUtil {

  private ExtractUtil() {}

  /** Helper to read an archive from a stream */
  public static void extractArchive(
      InputStream contents, Path targetPath, ExtractType type, @Nullable Glob fileFilter)
      throws IOException, ValidationException {
    Path root = targetPath.toAbsolutePath().normalize();
    PathMatcher rootedFilter = fileFilter != null ? fileFilter.relativeTo(root) : null;

    ArchiveEntry archiveEntry;
    try (ArchiveInputStream<?> inputStream = createArchiveInputStream(contents, type)) {
      while (((archiveEntry = inputStream.getNextEntry()) != null)) {
        Path resolvedPath = root.resolve(archiveEntry.getName()).normalize();

        // Security check: Prevent Zip Slip vulnerability
        if (!resolvedPath.startsWith(root)) {
          throw new IOException("Zip entry is outside of the target dir: " + archiveEntry.getName());
        }

        if ((rootedFilter != null && !rootedFilter.matches(resolvedPath))
            || archiveEntry.isDirectory()) {
          continue;
        }

        Files.createDirectories(resolvedPath.getParent());
        MoreFiles.asByteSink(resolvedPath).writeFrom(inputStream);
      }
    }
  }

  private static ArchiveInputStream<?> createArchiveInputStream(
      InputStream inputStream, ExtractType fileType) throws ValidationException, IOException {
    switch (fileType) {
      case JAR:
      case ZIP:
        return new ZipArchiveInputStream(inputStream);
      case TAR:
        return new TarArchiveInputStream(inputStream);
      case TAR_GZ:
        return new TarArchiveInputStream(new GzipCompressorInputStream(inputStream));
      case TAR_XZ:
        return new TarArchiveInputStream(new XZCompressorInputStream(inputStream));
      case TAR_BZ2:
        return new TarArchiveInputStream(new BZip2CompressorInputStream(inputStream));
    }
    throw new ValidationException(
        String.format("Failed to get archive input stream for file type: %s", fileType));
  }
}
