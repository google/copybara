/*
 * Copyright (C) 2026 Google Inc.
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

package com.google.copybara.perforce;

import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;

import com.google.copybara.CheckoutPath;
import com.google.copybara.DestinationReader;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import net.starlark.java.eval.EvalException;

/**
 * Reads files from the head of a Perforce stream, for {@code destination_reader().read_file(...)},
 * consistency files and merge-import baselines.
 */
class PerforceDestinationReader extends DestinationReader {

  private final PerforceServer server;
  private final String stream;
  private final Path workdir;

  PerforceDestinationReader(PerforceServer server, String stream, Path workdir) {
    this.server = server;
    this.stream = stream;
    this.workdir = workdir;
  }

  @Override
  public String readFile(String path) throws RepoException {
    return server.readFileAtHead(stream, path);
  }

  @Override
  public boolean exists(String path) {
    try {
      server.readFileAtHead(stream, path);
      return true;
    } catch (RepoException e) {
      return false;
    }
  }

  @Override
  public void copyDestinationFiles(Object globObj, Object path)
      throws RepoException, ValidationException, EvalException {
    CheckoutPath checkoutPath = convertFromNoneable(path, null);
    Glob glob = Glob.wrapGlob(globObj, null);
    copyDestinationFilesToDirectory(
        glob,
        checkoutPath == null
            ? workdir
            : checkoutPath.getCheckoutDir().resolve(checkoutPath.getPath()));
  }

  @Override
  public void copyDestinationFilesToDirectory(Glob glob, Path directory) throws RepoException {
    Path temp;
    try {
      temp = Files.createTempDirectory("p4_dest_reader");
    } catch (IOException e) {
      throw new RepoException("Could not create temp directory for Perforce destination reader", e);
    }
    try {
      // Materialise the whole stream head, then copy just the glob-matched files into the target.
      server.syncStreamHeadTo(stream, temp);
      PathMatcher matcher = glob.relativeTo(temp);
      try (Stream<Path> walk = Files.walk(temp)) {
        for (Path file :
            (Iterable<Path>)
                walk.filter(p -> Files.isSymbolicLink(p) || Files.isRegularFile(p))
                    .filter(matcher::matches)
                    ::iterator) {
          Path dest = directory.resolve(temp.relativize(file));
          Files.createDirectories(dest.getParent());
          if (Files.isSymbolicLink(file)) {
            Files.deleteIfExists(dest);
            Files.createSymbolicLink(dest, Files.readSymbolicLink(file));
          } else {
            Files.copy(
                file,
                dest,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
          }
        }
      }
    } catch (IOException e) {
      throw new RepoException("Error copying Perforce destination files", e);
    } finally {
      try {
        FileUtil.deleteRecursively(temp);
      } catch (IOException e) {
        // Best-effort cleanup of a temp directory.
      }
    }
  }
}
