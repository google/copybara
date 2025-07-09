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

package com.google.copybara;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/**
 * Common Starlark methods that allow users to manipulate paths of the workdir/checkoutPath.
 */
@SuppressWarnings("unused")
public class CheckoutFileSystem implements StarlarkValue {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final Path checkoutDir;

  public CheckoutFileSystem(Path checkoutDir) {
    this.checkoutDir = Preconditions.checkNotNull(checkoutDir);
  }
  @StarlarkMethod(
      name = "new_path",
      doc = "Create a new path",
      parameters = {
          @Param(
              name = "path",
              doc = "The string representing the path, relative to the checkout root directory"),
      })
  public CheckoutPath newPath(String path) throws EvalException {
    return CheckoutPath.createWithCheckoutDir(
        checkoutDir.getFileSystem().getPath(path), checkoutDir);
  }

  @StarlarkMethod(
      name = "create_symlink",
      doc = "Create a symlink",
      parameters = {
          @Param(name = "link", doc = "The link path"),
          @Param(name = "target", doc = "The target path"),
      })
  public void createSymlink(CheckoutPath link, CheckoutPath target) throws EvalException {
    try {
      Path linkFullPath = asCheckoutPath(link);
      // Verify target is inside checkout dir
      var unused = asCheckoutPath(target);

      if (Files.exists(linkFullPath)) {
        throw Starlark.errorf(
            "'%s' already exist%s",
            link.getPath(),
            Files.isDirectory(linkFullPath)
                ? " and is a directory"
                : Files.isSymbolicLink(linkFullPath)
                    ? " and is a symlink"
                    : Files.isRegularFile(linkFullPath)
                        ? " and is a regular file"
                        // Shouldn't happen:
                        : " and we don't know what kind of file is");
      }

      Path relativized = link.getPath().getParent() == null
          ? target.getPath()
          : link.getPath().getParent().relativize(target.getPath());
      Files.createDirectories(linkFullPath.getParent());

      // Shouldn't happen.
      Verify.verify(
          linkFullPath.getParent().resolve(relativized).normalize().startsWith(checkoutDir),
          "%s path escapes the checkout dir", relativized);
      Files.createSymbolicLink(linkFullPath, relativized);
    } catch (IOException e) {
      String msg = "Cannot create symlink: " + e.getMessage();
      logger.atSevere().withCause(e).log("%s", msg);
      throw Starlark.errorf("%s", msg);
    }
  }

  @StarlarkMethod(
      name = "write_path",
      doc = "Write an arbitrary string to a path (UTF-8 will be used)",
      parameters = {
          @Param(name = "path", doc = "The Path to write to"),
          @Param(name = "content", doc = "The content of the file"),
      })
  public void writePath(CheckoutPath path, String content) throws IOException, EvalException {
    Path fullPath = asCheckoutPath(path);
    if (fullPath.getParent() != null) {
      Files.createDirectories(fullPath.getParent());
    }
    Files.write(fullPath, content.getBytes(UTF_8));
  }

  @StarlarkMethod(
      name = "read_path",
      doc = "Read the content of path as UTF-8",
      parameters = {
          @Param(name = "path", doc = "The Path to read from"),
      })
  public String readPath(CheckoutPath path) throws IOException, EvalException {
    return new String(Files.readAllBytes(asCheckoutPath(path)), UTF_8);
  }

  @StarlarkMethod(
      name = "set_executable",
      doc = "Set the executable permission of a file",
      parameters = {
          @Param(name = "path", doc = "The Path to set the executable permission of"),
          @Param(name = "value", doc = "Whether or not the file should be executable"),
      })
  public void setExecutable(CheckoutPath path, boolean value) throws EvalException {
    asCheckoutPath(path).toFile().setExecutable(value);
  }

  /**
   * The path containing the repository state to transform. Transformation should be done in-place.
   */
  public Path getCheckoutDir() {
    return checkoutDir;
  }

  @StarlarkMethod(
      name = "list",
      doc = "List files in the checkout/work directory that matches a glob",
      parameters = {
          @Param(name = "paths", doc = "A glob representing the paths to list"),
      })
  public StarlarkList<CheckoutPath> list(Glob glob) throws IOException {
    PathMatcher pathMatcher = glob.relativeTo(checkoutDir);

    try (Stream<Path> stream = Files.walk(checkoutDir)) {
      return StarlarkList.immutableCopyOf(
          stream
              .filter(Files::isRegularFile)
              .filter(pathMatcher::matches)
              .map(p -> new CheckoutPath(checkoutDir.relativize(p), checkoutDir))
              .collect(Collectors.toList()));
    }
  }

  private Path asCheckoutPath(CheckoutPath path) throws EvalException {
    Path normalized = checkoutDir.resolve(path.getPath()).normalize();
    try {
      if (!normalized.startsWith(checkoutDir)
          || (Files.exists(normalized)
              && Files.isSymbolicLink(normalized)
              && !normalized.toRealPath().startsWith(checkoutDir))) {
        String realPath = "";
        try {
          realPath = normalized.toRealPath().toString();
        } catch (IOException ignored) {
          logger.atWarning().withCause(ignored).log("Cannot resolve %s", normalized);
        }
        throw Starlark.errorf(
            "%s is not inside the checkout directory or links to a file outside"
                + " the path. Was %s.",
            path, realPath);
      }
    } catch (IOException ioe) {
      logger.atInfo().withCause(ioe).log("Cannot resolve %s", path);
      throw Starlark.errorf("%s cannot be resolved : %s", path, ioe.getMessage());
    }
    return normalized;
  }
}
