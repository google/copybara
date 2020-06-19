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

package com.google.copybara.testing;

import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.base.Joiner;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Truth subjects for file assertions.
 */
public class FileSubjects {

  private static final Subject.Factory<PathSubject, Path> PATH_SUBJECT_FACTORY =
      PathSubject::new;

  private FileSubjects() {}

  /**
   * Truth subject that provides fluent methods for assertions on {@link Path}s.
   *
   * <p>For example:
   *
   *     assertThatPath(workdir)
   *       .containsFiles("file1", "file2")
   *       .containsFile("file3", "foo bar")
   *       .containsNoMoreFiles();
   */
  public static PathSubject assertThatPath(Path path) {
    return assertAbout(PATH_SUBJECT_FACTORY).that(path);
  }

  public static class PathSubject extends Subject {

    private final Path actual;
    private final Set<Path> allowedPaths = new HashSet<>();

    PathSubject(FailureMetadata failureMetadata, Path target) {
      super(failureMetadata, target);
      this.actual = target;
    }

    /**
     * Checks that the specific filenames exist relative to the path.
     */
    public PathSubject containsFiles(String... filenames) {
      for (String filename : filenames) {
        checkFile(filename);
      }
      return this;
    }

    /**
     * Checks that the specific filenames don't exist relative to the path.
     *
     * <p>Instead of this method, consider using {@link PathSubject#containsFile} combined with
     * {@link PathSubject#containsNoMoreFiles}.
     */
    public void containsNoFiles(String... filenames) {
      for (String filename : filenames) {
        Path filePath = actual.resolve(filename);
        if (Files.exists(filePath)) {
          failWithActual("expected not to have file", filePath);
        }
      }
    }

    /**
     * Check that {@code dirs} directories exist
     */
    public PathSubject containsDirs(String... dirs) {
      for (String filename : dirs) {
        Path filePath = actual.resolve(filename);
        if (!Files.isDirectory(filePath)) {
          failWithActual("expected to have directory", filePath);
        }
      }
      return this;
    }

    /**
     * Check that {@code dirs} directories do not exist (but could be regular files)
     */
    public PathSubject containsNoDirs(String... dirs) {
      for (String filename : dirs) {
        Path filePath = actual.resolve(filename);
        if (Files.isDirectory(filePath)) {
          failWithActual("expected not to have directory", filePath);
        }
      }
      return this;
    }

    /**
     * Checks that a filename exists relative to the path, and that the contents match.
     */
    public PathSubject containsFile(String filename, String fileContents) throws IOException {
      Path filePath = checkFile(filename);
      String realContents = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
      check("contentsOf(%s)", filename).that(realContents).isEqualTo(fileContents);
      return this;
    }

    public PathSubject containsFileMatching(String filename, String contentMatcher)
        throws IOException {
      Path filePath = checkFile(filename);
      String realContents = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
      check("contentsOf(%s)", filename).that(realContents).matches(contentMatcher);
      return this;
    }

    public PathSubject containsFileWithSha256(String filename, HashCode hash)
        throws IOException {
      Path filePath = checkFile(filename);
      HashCode realHash = Hashing.sha256().hashBytes(Files.readAllBytes(filePath));
      check("hashOf(%s)", filename).that(realHash).isEqualTo(hash);
      return this;
    }

    /**
     * Checks that a filename exists relative to the path, that the contents match and that the
     * executable bit is set.
     */
    public PathSubject containsExecutableFile(String filename, String fileContents)
        throws IOException {
      containsFile(filename, fileContents);
      Path filePath = checkFile(filename);
      if (!Files.isExecutable(filePath)) {
        failWithActual(simpleFact("expected to be executable"));
      }
      return this;
    }

    /**
     * Checks that a filename exists relative to the path and that it is a symlink pointing to
     * target.
     */
    public PathSubject containsSymlink(String filename, String target) throws IOException {
      Path filePath = actual.resolve(filename);
      Path targetPath = checkFile(target);

      if (!Files.isSymbolicLink(filePath)) {
        failWithActual("expected to be a Symlink", filename);
      }
      Path realTarget = filePath.resolveSibling(Files.readSymbolicLink(filePath));
      if (!Files.isSameFile(realTarget, targetPath)) {
        failWithoutActual(
            fact("expected to point to", target),
            fact("but points to", realTarget),
            fact("given path was", filename));
      }
      return this;
    }

    /**
     * Checks that there are no more files in the path.
     */
    public PathSubject containsNoMoreFiles() throws IOException {
      Files.walkFileTree(
          actual,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              if (attrs.isRegularFile()) {
                Path relativeFile = actual.relativize(file);
                if (!allowedPaths.contains(relativeFile)) {
                  failWithActual("expected to contain no more files", relativeFile.toString());
                }
              }
              return FileVisitResult.CONTINUE;
            }
          });
      return this;
    }

    private Path checkFile(String filename) {
      Path filePath = actual.resolve(filename);
      if (!Files.exists(filePath)) {
        failWithActual("missing file", filename);
      }
      allowedPaths.add(actual.relativize(filePath));
      return filePath;
    }

    @Override
    protected String actualCustomStringRepresentation() {
      StringBuilder sb = new StringBuilder(actual + ":\n");
      try (Stream<Path> pathStream = Files.walk(actual))  {
        for (Path path : pathStream.collect(Collectors.toList())) {
          if (Files.isRegularFile(path)) {
            sb.append("  ");
            sb.append(actual.relativize(path));
            sb.append(": <");
            sb.append(Joiner.on("\\n").join(Files.readAllLines(path)));
            sb.append(">\n");
          } else if (Files.isSymbolicLink(path)) {
            sb.append("  ");
            sb.append(actual.relativize(path));
            sb.append(" -> ");
            sb.append(Files.readSymbolicLink(path));
          }
        }
        return sb.toString();
      } catch (IOException e) {
        return e.toString();
      }
    }
  }
}
