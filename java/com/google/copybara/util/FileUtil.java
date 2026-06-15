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
import static com.google.common.base.Verify.verify;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.common.io.InsecureRecursiveDeleteException;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.common.net.PercentEscaper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Utility methods for files
 */
public final class FileUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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

  public static void copyFilesRecursively(Path from, Path to, CopySymlinkStrategy symlinkStrategy)
      throws IOException {
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
  public static void copyFilesRecursively(Path from, Path to,
      CopySymlinkStrategy symlinkStrategy, Glob glob) throws IOException {
    copyFilesRecursively(from, to, symlinkStrategy, glob, Optional.empty());
  }

  /**
   * Same as copyFilesRecursively, but with an optional callback that is called for each file
   * Copies files from {@code from} directory to {@code to} directory. If any file exist in the
   * destination it fails instead of overwriting.
   *
   */
  public static void copyFilesRecursively(Path from, Path to,
      CopySymlinkStrategy symlinkStrategy, Glob glob, Optional<CopyVisitorValidator> validator)
      throws IOException {
    checkArgument(Files.isDirectory(from), "%s (from) is not a directory", from);
    checkArgument(Files.isDirectory(to), "%s (to) is not a directory", to);

    // Optimization to skip folders that will be skipped. This works well for huge file trees
    // where we have a very specific Glob ( foo/bar/**).
    for (String root : glob.roots()) {
      Path rootElement = from.resolve(root);
      if (!Files.exists(rootElement)) {
        continue;
      }
      Files.walkFileTree(
          rootElement,
          new CopyVisitor(
              rootElement,
              to.resolve(root),
              symlinkStrategy,
              glob.relativeTo(from.normalize()),
              // The PathMatcher matches destination files so that it can work with
              // absolute symlink materialization (We create a new CopyVisitor with the
              // resolved symlink as origin.
              glob.relativeTo(to.normalize()),
              validator,
              ImmutableSet.of()));
    }
  }

  /**
   * Adds the given permissions to the matching files under the given path.
   */
  public static void addPermissionsRecursively(
      Path path, Set<PosixFilePermission> permissionsToAdd, PathMatcher pathMatcher)
      throws IOException {
    Files.walkFileTree(
        path,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (!attrs.isSymbolicLink() && pathMatcher.matches(file)) {
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
  public static int deleteFilesRecursively(Path path, PathMatcher pathMatcher)
      throws IOException {
    AtomicInteger counter = new AtomicInteger();
    // Normalize so that the patchMatcher works
    Files.walkFileTree(path.normalize(), new SimpleFileVisitor<Path>() {

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
   * Deletes the files that match the Glob.
   *
   * <p>Note that this method doesn't delete the directories, only the files inside those
   * directories. This is fine for our use case since the majority of SCMs don't care about empty
   * directories.
   *
   * @throws IOException If it fails traversing or deleting the tree.
   */
  public static int deleteFilesRecursively(Path path, Glob glob) throws IOException {
    AtomicInteger counter = new AtomicInteger();
    // Optimization to only visit folders that match the Glob. This avoids needless work for huge
    // file trees where only a specific subset will be matched by the Glob (e.g., foo/bar/**).
    for (String root : glob.roots()) {
      Path rootPath = path.resolve(root);
      if (Files.exists(rootPath)) {
        counter.addAndGet(deleteFilesRecursively(rootPath, glob.relativeTo(path)));
      }
    }
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
      logger.atWarning().log("Secure delete not supported. Deleting '%s' insecurely.", path);
      MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE);
    }
  }

  /**
   * A {@link PathMatcher} that returns true if any of the delegate {@code pathMatchers} returns
   * true.
   */
  static PathMatcher anyPathMatcher(ImmutableList<PathMatcher> pathMatchers) {
    return new AnyPathMatcher(pathMatchers);
  }

  /**
   * Returns {@link PathMatcher} that negates {@code athMatcher}
   */
  public static PathMatcher notPathMatcher(PathMatcher pathMatcher) {
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

  /** Modes for handling symlinks in Copybara. */
  public enum SymlinkMode {
    /**
     * Copy the symlink as-is. The destination will contain a symlink pointing to the same target as
     * the original symlink.
     */
    COPY_AS_IS,
    /**
     * Materialize the symlink. The destination will contain a copy of the target file or directory
     * that the symlink points to, instead of the symlink itself.
     */
    MATERIALIZE,
    /** Ignore the symlink. The symlink will not be copied to the destination. */
    IGNORE,
    /** Fail the operation. An exception will be thrown if a symlink is encountered. */
    FAIL
  }

  /** How to handle symlinks */
  public static final class CopySymlinkStrategy {
    public static final CopySymlinkStrategy FAIL_OUTSIDE_SYMLINKS =
        new CopySymlinkStrategy(
            /* inside= */ SymlinkMode.COPY_AS_IS,
            /* outside= */ SymlinkMode.FAIL,
            /* broken= */ SymlinkMode.FAIL);

    public static final CopySymlinkStrategy MATERIALIZE_OUTSIDE_SYMLINKS =
        new CopySymlinkStrategy(
            /* inside= */ SymlinkMode.COPY_AS_IS,
            /* outside= */ SymlinkMode.MATERIALIZE,
            /* broken= */ SymlinkMode.FAIL);

    public static final CopySymlinkStrategy IGNORE_INVALID_SYMLINKS =
        new CopySymlinkStrategy(
            /* inside= */ SymlinkMode.COPY_AS_IS,
            /* outside= */ SymlinkMode.COPY_AS_IS,
            /* broken= */ SymlinkMode.IGNORE);

    private final SymlinkMode inside;
    private final SymlinkMode outside;
    private final SymlinkMode broken;

    public CopySymlinkStrategy(SymlinkMode inside, SymlinkMode outside, SymlinkMode broken) {
      this.inside = checkNotNull(inside);
      this.outside = checkNotNull(outside);
      this.broken = checkNotNull(broken);
      checkArgument(
          broken != SymlinkMode.MATERIALIZE, "MATERIALIZE is not a valid mode for broken symlinks");
    }

    public SymlinkMode getSymlinkMode(ResolvedSymlink resolvedSymlink) {
      return switch (resolvedSymlink.getTargetLocation()) {
        case INSIDE -> inside;
        case OUTSIDE -> outside;
        case BROKEN -> broken;
      };
    }
  }

  /**
   * A visitor that copies files recursively. If symlinks are found, and are relative to 'from'
   * they symlink is maintained, unless forceCopySymlinks is set.
   */
  private static class CopyVisitor extends SimpleFileVisitor<Path> {

    private final Path to;
    private final Path from;
    private final CopySymlinkStrategy symlinkStrategy;
    private final PathMatcher originPathMatcher;
    private final PathMatcher destPathMatcher;
    private final Optional<CopyVisitorValidator> additionalValidator;
    private final ImmutableSet<Path> visitedSourceDirs;

    CopyVisitor(
        Path from,
        Path to,
        CopySymlinkStrategy symlinkStrategy,
        PathMatcher originPathMatcher,
        PathMatcher destPathMatcher,
        Optional<CopyVisitorValidator> additionalValidator,
        ImmutableSet<Path> visitedSourceDirs) {
      this.to = to;
      this.from = from;
      this.symlinkStrategy = symlinkStrategy;
      this.originPathMatcher = originPathMatcher;
      this.destPathMatcher = destPathMatcher;
      this.additionalValidator = additionalValidator;
      this.visitedSourceDirs = visitedSourceDirs;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      // using from...toString to allow crossing from one filesystem into another
      Path destFile = to.resolve(from.relativize(file).toString()).normalize();
      if (!destPathMatcher.matches(destFile)) {
        return FileVisitResult.CONTINUE;
      }
      if (additionalValidator.isPresent()) {
        additionalValidator.get().validate(file);
      }
      Files.createDirectories(destFile.getParent());

      boolean symlink = attrs.isSymbolicLink();
      if (symlink) {
        // Determine the symlink mode based on whether it is broken, escaped the root,
        // or is inside the root.
        ResolvedSymlink resolvedSymlink = resolveSymlink(originPathMatcher, file);
        SymlinkMode mode = symlinkStrategy.getSymlinkMode(resolvedSymlink);

        switch (mode) {
          case FAIL ->
              throw new SymlinkException(
                  String.format(
                      "Symlink '%s' is %s as it points to '%s'",
                      file, resolvedSymlink.getTargetLocation(), resolvedSymlink.getRegularFile()));
          case IGNORE -> {
            return FileVisitResult.CONTINUE;
          }
          case COPY_AS_IS -> {
            Files.createSymbolicLink(destFile, Files.readSymbolicLink(file));
            return FileVisitResult.CONTINUE;
          }
          case MATERIALIZE -> {
            verify(resolvedSymlink.getTargetLocation() != ResolvedSymlink.TargetLocation.BROKEN);
            if (Files.isDirectory(resolvedSymlink.getRegularFile())) {
              Path targetReal = resolvedSymlink.getRegularFile().toRealPath();
              if (visitedSourceDirs.contains(targetReal)) {
                throw new IOException(
                    "Symlink cycle detected: "
                        + file
                        + " -> "
                        + targetReal
                        + " which is already being visited in "
                        + visitedSourceDirs);
              }
              Files.createDirectory(destFile);
              Files.walkFileTree(
                  resolvedSymlink.getRegularFile(),
                  new CopyVisitor(
                      resolvedSymlink.getRegularFile(),
                      destFile,
                      symlinkStrategy,
                      originPathMatcher,
                      destPathMatcher,
                      additionalValidator,
                      ImmutableSet.<Path>builder()
                          .addAll(visitedSourceDirs)
                          .add(targetReal)
                          .build()));
              return FileVisitResult.CONTINUE;
            }
          }
        }
      }
      if (symlink || attrs.isRegularFile()) {
        Files.copy(file, destFile, StandardCopyOption.COPY_ATTRIBUTES);
      }
      // Make writable any symlink that we materialize. This is safe since we have already
      // done a copy of the file. And it is probable that we will want to modify it.
      if (symlink) {
        addPermissions(destFile, ImmutableSet.of(PosixFilePermission.OWNER_WRITE));
      }
      return FileVisitResult.CONTINUE;
    }
  }

  /**
   * Additional checks to run while copying files.
   */
  public interface CopyVisitorValidator {
    void validate(Path from) throws IOException;
  }

  /**
   * Represents the regular file/directory that a symlink points to. It also includes a boolean
   * that is true if during all the symlink steps resolution, all the paths found where relative
   * to the root directory.
   */
  public static final class ResolvedSymlink {

    public enum TargetLocation {
      INSIDE,
      OUTSIDE,
      BROKEN
    }

    private final Path regularFile;
    private final TargetLocation targetLocation;

    ResolvedSymlink(Path regularFile, TargetLocation targetLocation) {
      this.regularFile = checkNotNull(regularFile);
      this.targetLocation = checkNotNull(targetLocation);
    }

    public Path getRegularFile() {
      return regularFile;
    }

    public TargetLocation getTargetLocation() {
      return targetLocation;
    }
  }

  /**
   * Resolves {@code symlink} recursively until it finds a regular file or directory. It also
   * checks that all its intermediate paths jumps are under {@code matcher}.
   */
  public static ResolvedSymlink resolveSymlink(PathMatcher matcher, Path symlink)
      throws IOException {
    // Normalize since matcher glob:foo/bar/file* doesn't match foo/bar/../bar/file*
    Path path = symlink.normalize();
    checkArgument(matcher.matches(path), "%s doesn't match %s", path, matcher);

    Set<Path> visited = new LinkedHashSet<>();
    while (Files.isSymbolicLink(path)) {
      if (!visited.add(path)) {
        throw new IOException(
            "Symlink cycle detected:\n  "
                + Joiner.on("\n  ").join(Iterables.concat(visited, ImmutableList.of(symlink))));
      }
      // Avoid 'dot' -> . like traps by capping to a sane limit
      if (visited.size() > 50) {
        throw new IOException(
            "Symlink chain too long:\n  "
                + Joiner.on("\n  ").join(Iterables.concat(visited, ImmutableList.of(symlink))));
      }
      Path newPath = Files.readSymbolicLink(path);
      if (!newPath.isAbsolute()) {
        newPath = path.resolveSibling(newPath).toAbsolutePath().normalize();
      } else {
        newPath = newPath.normalize();
      }
      if (!matcher.matches(newPath)) {
        if (!Files.isDirectory(newPath)
            // Special support for symlinks in the form of ROOT/symlink -> '.'. Technically we
            // shouldn't allow this because of our glob implementation, but this is a regression
            // from the old code and the correct behavior is difficult to understand by our users.
            || !matcher.matches(newPath.resolve("copybara_random_path.txt"))) {
          Path realPath;
          boolean broken = false;
          try {
            realPath = newPath.toRealPath();
          } catch (NoSuchFileException e) {
            realPath = newPath;
            broken = true;
          }
          return new ResolvedSymlink(
              realPath,
              broken
                  ? ResolvedSymlink.TargetLocation.BROKEN
                  : ResolvedSymlink.TargetLocation.OUTSIDE);
        }
      }
      path = newPath;
    }
    boolean broken = !Files.exists(path);
    return new ResolvedSymlink(
        path,
        broken ? ResolvedSymlink.TargetLocation.BROKEN : ResolvedSymlink.TargetLocation.INSIDE);
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

  private static final int REPO_FOLDER_NAME_LIMIT = 100;
  private static final PercentEscaper PERCENT_ESCAPER = new PercentEscaper(
      "-_", /*plusForSpace=*/ true);

  public static Path resolveDirInCache(String url, Path repoStorage) {
    String escapedUrl = PERCENT_ESCAPER.escape(url);

    // This is to avoid "Filename too long" errors, mainly in tests. We cannot change the repo
    // storage path (we use JAVA_IO_TMPDIR), which is the right thing to do for tests to be
    // hermetic.
    if (escapedUrl.length() > REPO_FOLDER_NAME_LIMIT + 40) {
      escapedUrl =
          escapedUrl.substring(0, REPO_FOLDER_NAME_LIMIT - 1)
              + "_"
              + Hashing.sha1()
                  .hashString(
                      escapedUrl.substring(REPO_FOLDER_NAME_LIMIT - 1), StandardCharsets.UTF_8);
    }
    return repoStorage.resolve(escapedUrl);
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
