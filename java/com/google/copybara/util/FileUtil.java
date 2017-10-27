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

package com.google.copybara.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.InsecureRecursiveDeleteException;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Utility methods for files
 */
public final class FileUtil {

  private static final Logger logger = Logger.getLogger(FileUtil.class.getName());

  private static final PathMatcher ALL_FILES = new PathMatcher() {
    @Override
    public boolean matches(Path path) {
      return true;
    }

    @Override
    public String toString() {
      return "**";
    }
  };

  private FileUtil() {}

  private static final Pattern RELATIVISM = Pattern.compile("(.*/)?[.][.]?(/.*)?");

  /**
   * Checks that the given path is relative and does not contain any {@code .} or {@code ..}
   * components.
   *
   * @return the {@code path} passed
   */
  public static String checkNormalizedRelative(String path) {
    checkArgument(!RELATIVISM.matcher(path).matches(),
        "path has unexpected . or .. components: %s", path);
    checkArgument(!path.startsWith("/"),
        "path must be relative, but it starts with /: %s", path);
    return path;
  }

  /**
   * Checks that the given path is relative and does not contain any {@code .} or {@code ..}
   * components.
   *
   * @return the {@code path} passed
   */
  public static Path checkNormalizedRelative(Path path) {
    checkNormalizedRelative(path.toString());
    return path;
  }

  public static void copyFilesRecursively(final Path from, final Path to,
      CopySymlinkStrategy symlinkStrategy) throws IOException {
    copyFilesRecursively(from, to, symlinkStrategy, Glob.ALL_FILES);
  }

  /**
   * Copies files from {@code from} directory to {@code to} directory. If any file exist in the
   * destination it fails instead of overwriting.
   *
   * <p>File attributes are also copied.
   *
   * <p>Symlinks that target files inside {@code from} directory are replicated in {@code to} as
   * the equivalent symlink. In other words, a {@code from/foo/bar -> ../baz} will be translated
   * into the following symlink: ${@code to/foo/bar -> ../baz}.
   *
   * <p>Symlinks that escape the {@code from} directory or that target absolute paths are treated
   * according to {@code symlinkStrategy}.
   */
  public static void copyFilesRecursively(final Path from, final Path to,
      CopySymlinkStrategy symlinkStrategy, Glob glob) throws IOException {
    checkArgument(Files.isDirectory(from), "%s (from) is not a directory");
    checkArgument(Files.isDirectory(to), "%s (to) is not a directory");

    // Optimization to skip folders that will be skipped. This works well for huge file trees
    // where we have a very specific Glob ( foo/bar/**).
    for (String root : glob.roots()) {
      Path rootElement = from.resolve(root);
      if (!Files.exists(rootElement)) {
        continue;
      }
      Files.walkFileTree(
          rootElement,
          new CopyVisitor(rootElement, to.resolve(root), symlinkStrategy,
                          // The PathMatcher matches destination files so that it can work with
                          // absolute symlink materialization (We create a new CopyVisitor with the
                          // resolved symlink as origin.
                          glob.relativeTo(to)));
    }
  }

  /**
   * Adds the given permissions to the matching files under the given path.
   */
  public static void addPermissionsRecursively(
      Path path, Set<PosixFilePermission> permissionsToAdd, final PathMatcher pathMatcher)
      throws IOException {
    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (pathMatcher.matches(file)) {
          addPermissions(file, permissionsToAdd);
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Adds the given permissions to all the files under the given path.
   */
  public static void addPermissionsAllRecursively(
      Path path, Set<PosixFilePermission> permissionsToAdd) throws IOException {
    addPermissionsRecursively(path, permissionsToAdd, ALL_FILES);
  }

  /**
   * Deletes the files that match the PathMatcher.
   *
   * <p> Note that this method doesn't delete the directories, only the files inside those
   * directories. This is fine for our use case since the majority of SCMs don't care about empty
   * directories.
   *
   * @throws IOException If it fails traversing or deleting the tree.
   */
  public static int deleteFilesRecursively(Path path, final PathMatcher pathMatcher)
      throws IOException {
    final AtomicInteger counter = new AtomicInteger();
    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (pathMatcher.matches(file)) {
          Files.delete(file);
          counter.incrementAndGet();
        }
        return FileVisitResult.CONTINUE;
      }
    });
    return counter.get();
  }

  /**
   * Delete all the contents of a path recursively.
   *
   * <p>First we try to delete securely. In case the FileSystem doesn't support it,
   * delete it insecurely.
   */
  public static void deleteRecursively(Path path) throws IOException {
    try {
      MoreFiles.deleteRecursively(path);
    } catch (InsecureRecursiveDeleteException ignore) {
      logger.warning(String.format("Secure delete not supported. Deleting '%s' insecurely.",
          path));
      MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE);
    }
  }

  /**
   * A {@link PathMatcher} that returns true if any of the delegate {@code pathMatchers} returns
   * true.
   */
  static PathMatcher anyPathMatcher(final ImmutableList<PathMatcher> pathMatchers) {
    return new AnyPathMatcher(pathMatchers);
  }

  /**
   * Returns {@link PathMatcher} that negates {@code pathMatcher}
   */
  public static PathMatcher notPathMatcher(final PathMatcher pathMatcher) {
    return new PathMatcher() {
      @Override
      public boolean matches(Path path) {
        return !pathMatcher.matches(path);
      }

      @Override
      public String toString() {
        return "not(" + pathMatcher + ")";
      }
    };
  }

  public enum CopySymlinkStrategy {
    /**
     * Materialize any symlink found in a new file.
     */
    MATERIALIZE_ALL,
    /**
     * Any symlink outside of the folder copied will be materialized in a new file.
     */
    MATERIALIZE_OUTSIDE_SYMLINKS,
    /**
     * Fail if any symlink outside of the folder is found.
     */
    FAIL_OUTSIDE_SYMLINKS,
  }

  /**
   * A visitor that copies files recursively. If symlinks are found, and are relative to 'from'
   * they symlink is maintained, unless forceCopySymlinks is set.
   */
  private static class CopyVisitor extends SimpleFileVisitor<Path> {

    private final Path to;
    private final Path from;
    private final CopySymlinkStrategy symlinkStrategy;
    private final PathMatcher pathMatcher;

    CopyVisitor(Path from, Path to, CopySymlinkStrategy symlinkStrategy, PathMatcher pathMatcher) {
      this.to = to;
      this.from = from;
      this.symlinkStrategy = symlinkStrategy;
      this.pathMatcher = pathMatcher;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      Path destFile = to.resolve(from.relativize(file));
      if (!pathMatcher.matches(destFile)) {
        return FileVisitResult.CONTINUE;
      }
      Files.createDirectories(destFile.getParent());

      boolean symlink = Files.isSymbolicLink(file);
      if (symlink) {
        // If the symlink remains under 'from' we keep the symlink as relative.
        // Otherwise we copy it as a regular file.
        ResolvedSymlink resolvedSymlink = resolveSymlink(from, file);
        boolean escapedRoot = !resolvedSymlink.allUnderRoot;
        if (escapedRoot) {
          String msg = String.format(
              "Symlink '%s' is absolute or escaped the root: '%s'.",
              file, resolvedSymlink.regularFile);
          if (symlinkStrategy == CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS) {
            throw new AbsoluteSymlinksNotAllowed(msg, file, resolvedSymlink.regularFile);
          }
          logger.log(Level.INFO, msg + " Materializing the symlink.");
        }

        if (symlinkStrategy == CopySymlinkStrategy.MATERIALIZE_ALL || escapedRoot) {
          if (Files.isDirectory(file)) {
            // A symlink to a directory outside 'from'. Copy all the files recursively as regular
            // files
            Files.createDirectory(destFile);
            Files.walkFileTree(resolvedSymlink.regularFile,
                new CopyVisitor(resolvedSymlink.regularFile, destFile,
                    CopySymlinkStrategy.MATERIALIZE_ALL, pathMatcher));
            return FileVisitResult.CONTINUE;
          }
        } else {
          Files.createSymbolicLink(destFile, Files.readSymbolicLink(file));
          return FileVisitResult.CONTINUE;
        }
      }
      Files.copy(file, destFile, StandardCopyOption.COPY_ATTRIBUTES);
      // Make writable any symlink that we materialize. This is safe since we have already
      // done a copy of the file. And it is probable that we will want to modify it.
      if (symlink) {
        addPermissions(destFile, ImmutableSet.of(PosixFilePermission.OWNER_WRITE));
      }
      return FileVisitResult.CONTINUE;
    }

    /**
     * Resolves {@code symlink} recursively until it finds a regular file or directory. It also
     * checks that all its intermediate paths jumps are under {@code root}.
     */
    private ResolvedSymlink resolveSymlink(Path root, Path symlink) throws IOException {
      checkArgument(symlink.startsWith(root), "%s doesn't start with %s", symlink, root);
      checkArgument(root.isAbsolute(), "%s is not absolute", root);

      Path relativeLink = root.relativize(symlink).normalize();
      Path realLink = symlink;
      boolean insideRoot = true;
      Set<Path> visited = new LinkedHashSet<>();
      while (true) {
        if (visited.contains(relativeLink)) {
          throw new IOException("Symlink cycle detected:\n  "
              + Joiner.on("\n  ").join(
              Iterables.concat(visited, ImmutableList.of(relativeLink))));
        }
        visited.add(relativeLink);

        if (insideRoot && (relativeLink.isAbsolute()
            || relativeLink.getNameCount() == 0
            // Because it is normalized, '..' is the first segment if it goes outside root.
            || relativeLink.getName(0).toString().equals(".."))) {
          insideRoot = false;
        }

        if (Files.isSymbolicLink(realLink)) {
          // Read the symlink. Could be '/foo/bar', '../../baz' or 'some' for example.
          Path resolved = Files.readSymbolicLink(realLink);
          // Resolve to absolute path. We use sibling because 'some' should be resolved
          // to the directory containing realLink + the link.
          realLink = realLink.resolveSibling(resolved).normalize();
          if (insideRoot) {
            // Now we have a possibly absolute path. Resolve relative to root and normalize
            // so '..' is the first segment.
            relativeLink = root.relativize(realLink).normalize();
          } else {
            relativeLink = realLink;
          }
        } else {
          // We reach to the regular file/directory.
          break;
        }
      }
      return new ResolvedSymlink(realLink, insideRoot);
    }

    /**
     * Represents the regular file/directory that a symlink points to. It also includes a boolean
     * that is true if during all the symlink steps resolution, all the paths found where relative
     * to the root directory.
     */
    private static final class ResolvedSymlink {

      private final Path regularFile;
      private final boolean allUnderRoot;

      ResolvedSymlink(Path regularFile, boolean allUnderRoot) {
        this.regularFile = checkNotNull(regularFile);
        this.allUnderRoot = allUnderRoot;
      }
    }
  }

  /**
   * Tries to add the Posix permissions if the file belongs to a Posix filesystem. This is an
   * addition, which means that no permissions are removed.
   *
   * <p>For Windows type filesystems, it uses setReadable/setWritable/setExecutable, which is only
   * supported for the owner, and ignores the rest of permissions.
   */
  public static void addPermissions(Path path, Set<PosixFilePermission> permissionsToAdd)
      throws IOException {
    if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
      permissions.addAll(permissionsToAdd);
      Files.setPosixFilePermissions(path, permissions);
    } else {
      File file = path.toFile();
      if (permissionsToAdd.contains(PosixFilePermission.OWNER_READ)) {
        if (!file.setReadable(true)) {
          throw new IOException("Could not set 'readable' permission for file: " + path);
        }
      }
      if (permissionsToAdd.contains(PosixFilePermission.OWNER_WRITE)) {
        if (!file.setWritable(true)) {
          throw new IOException("Could not set 'writable' permission for file: " + path);
        }
      }
      if (permissionsToAdd.contains(PosixFilePermission.OWNER_EXECUTE)) {
        if (!file.setExecutable(true)) {
          throw new IOException("Could not set 'executable' permission for file: " + path);
        }
      }
    }
  }

  private static class AnyPathMatcher implements PathMatcher {

    private final ImmutableList<PathMatcher> pathMatchers;

    AnyPathMatcher(ImmutableList<PathMatcher> pathMatchers) {
      this.pathMatchers = pathMatchers;
    }

    @Override
    public boolean matches(Path path) {
      for (PathMatcher pathMatcher : pathMatchers) {
        if (pathMatcher.matches(path)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      AnyPathMatcher that = (AnyPathMatcher) o;
      return Objects.equals(pathMatchers, that.pathMatchers);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(pathMatchers);
    }

    @Override
    public String toString() {
      return "anyOf[" + Joiner.on(", ").join(pathMatchers) + "]";
    }
  }
}
