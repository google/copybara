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

package com.google.copybara;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.doc.annotations.DocSignaturePrefix;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.ResolvedSymlink;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

/**
 * Represents a file that is exposed to Skylark.
 *
 * <p>Files are always relative to the checkout dir and normalized.
 */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "Path",
    doc = "Represents a path in the checkout directory")
@DocSignaturePrefix("path")
public class CheckoutPath implements Comparable<CheckoutPath>, StarlarkValue {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Path path;
  private final Path checkoutDir;

  CheckoutPath(Path path, Path checkoutDir) {
    this.path = Preconditions.checkNotNull(path);
    this.checkoutDir = Preconditions.checkNotNull(checkoutDir);
  }

  private CheckoutPath create(Path path) throws EvalException {
    return createWithCheckoutDir(path, checkoutDir);
  }

  @VisibleForTesting
  public static CheckoutPath createWithCheckoutDir(Path relative, Path checkoutDir)
      throws EvalException {
    if (relative.isAbsolute()) {
      throw Starlark.errorf("Absolute paths are not allowed: %s", relative);
    }
    Path targetPath = checkoutDir.resolve(relative).normalize();
    if (!targetPath.startsWith(checkoutDir)) {
      throw Starlark.errorf("Escaping the checkout dir is not allowed: %s", relative);
    }

    return new CheckoutPath(relative.normalize(), checkoutDir);
  }

  @StarlarkMethod(
      name = "path",
      doc = "Full path relative to the checkout directory",
      structField = true)
  public String pathAsString() {
    return path.toString();
  }

  /**
   * The full path pointing to the real location of the checkout file. Use only for internal
   * implementations and do not make this available to Starlark.
   */
  public Path fullPath() {
    return checkoutDir.resolve(path);
  }

  @StarlarkMethod(name = "name",
      doc = "Filename of the path. For foo/bar/baz.txt it would be baz.txt",
      structField = true)
  public String name() {
    return path.getFileName().toString();
  }

  @StarlarkMethod(name = "parent", doc = "Get the parent path", structField = true)
  public Object parent() throws EvalException {
    Path parent = path.getParent();
    if (parent == null) {
      // nio equivalent of new_path("foo").parent returns null, but we want to be able to do
      // foo.parent.resolve("bar"). While sibbling could be use for this, sometimes we'll need
      // to return the parent folder and another function resolve a path based on that.
      return path.toString().equals("") ? Starlark.NONE : create(path.getFileSystem().getPath(""));
    }
    return create(parent);
  }

  @StarlarkMethod(
      name = "relativize",
      doc =
          "Constructs a relative path between this path and a given path. For example:<br>"
              + "    path('a/b').relativize('a/b/c/d')<br>"
              + "returns 'c/d'",
      parameters = {
        @Param(name = "other", doc = "The path to relativize against this path"),
      })
  public CheckoutPath relativize(CheckoutPath other) throws EvalException {
    return create(path.relativize(other.path));
  }

  @StarlarkMethod(
      name = "resolve",
      doc = "Resolve the given path against this path.",
      parameters = {
        @Param(
            name = "child",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = CheckoutPath.class),
            },
            doc =
                "Resolve the given path against this path. The parameter"
                    + " can be a string or a Path.")
      })
  public CheckoutPath resolve(Object child) throws EvalException {
    if (child instanceof String) {
      return create(path.resolve((String) child));
    } else if (child instanceof CheckoutPath) {
      return create(path.resolve(((CheckoutPath) child).path));
    }
    throw Starlark.errorf(
        "Cannot resolve children for type %s: %s", child.getClass().getSimpleName(), child);
  }

  @StarlarkMethod(
      name = "resolve_sibling",
      doc = "Resolve the given path against this path.",
      parameters = {
        @Param(
            name = "other",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = CheckoutPath.class),
            },
            doc =
                "Resolve the given path against this path. The parameter can be a string or"
                    + " a Path."),
      })
  public CheckoutPath resolveSibling(Object other) throws EvalException {
    if (other instanceof String) {
      return create(path.resolveSibling((String) other));
    } else if (other instanceof CheckoutPath) {
      return create(path.resolveSibling(((CheckoutPath) other).path));
    }
    throw Starlark.errorf(
        "Cannot resolve sibling for type %s: %s", other.getClass().getSimpleName(), other);
  }

  @StarlarkMethod(
      name = "attr",
      doc = "Get the file attributes, for example size.",
      structField = true)
  public CheckoutPathAttributes attr() throws EvalException {
    try {
      return new CheckoutPathAttributes(path,
          Files.readAttributes(checkoutDir.resolve(path), BasicFileAttributes.class,
              LinkOption.NOFOLLOW_LINKS));
    } catch (IOException e) {
      String msg = "Error getting attributes for " + path + ":" + e;
      logger.atSevere().withCause(e).log("%s", msg);
      throw Starlark.errorf("%s", msg); // or IOException?
    }
  }

  @StarlarkMethod(name = "read_symlink", doc = "Read the symlink")
  public CheckoutPath readSymbolicLink() throws EvalException {
    try {
      Path symlinkPath = checkoutDir.resolve(path);
      if (!Files.isSymbolicLink(symlinkPath)) {
        throw Starlark.errorf("%s is not a symlink", path);
      }

      ResolvedSymlink resolvedSymlink =
          FileUtil.resolveSymlink(Glob.ALL_FILES.relativeTo(checkoutDir), symlinkPath);
      if (!resolvedSymlink.isAllUnderRoot()) {
        throw Starlark.errorf(
            "Symlink %s points to a file outside the checkout dir: %s",
            symlinkPath, resolvedSymlink.getRegularFile());
      }

      return create(checkoutDir.relativize(resolvedSymlink.getRegularFile()));
    } catch (IOException e) {
      String msg = String.format("Cannot resolve symlink %s: %s", path, e);
      logger.atSevere().withCause(e).log("%s", msg);
      throw Starlark.errorf("%s", msg);
    }
  }

  @StarlarkMethod(name = "remove", doc = "Delete self")
  public void remove() throws ValidationException, EvalException {
    try {
      Files.delete(checkoutDir.resolve(path));
    } catch (IOException e) {
      if (e.getCause() instanceof NoSuchFileException) {
        throw Starlark.errorf("Could not find file %s, received error %s", path, e.toString());
      }
      throw new ValidationException("Could not delete file for unknown reason", e);
    }
  }

  @StarlarkMethod(
      name = "rmdir",
      doc =
          "Delete all files in a directory. If recursive is true, delete descendants of all files"
              + " in directory",
      parameters = {
        @Param(
            name = "recursive",
            named = true,
            doc = "When true, delete descendants of self and of siblings",
            defaultValue = "False")
      })
  public void rmDir(boolean recursive) throws ValidationException, EvalException {
    try {
      if (!Files.exists(checkoutDir.resolve(path))) {
        return;
      }
      if (recursive) {
        FileUtil.deleteRecursively(checkoutDir.resolve(path));
      } else {
        Files.delete(checkoutDir.resolve(path));
      }
    } catch (IOException e) {
      if (e.getCause() instanceof NoSuchFileException) {
        throw Starlark.errorf("Could not find file %s, received error %s", path, e.toString());
      }
      throw new ValidationException("Could not delete file for unknown reason", e);
    }
  }

  @StarlarkMethod(
          name = "exists",
          doc = "Check whether a file, directory or symlink exists at this path")
  public boolean fileExists() {
    return Files.exists(checkoutDir.resolve(path));
  }

  public Path getPath() {
    return path;
  }

  public Path getCheckoutDir() {
    return checkoutDir;
  }

  @Override
  public String toString() {
    return path.toString();
  }

  @Override
  public int compareTo(CheckoutPath o) {
    return this.path.compareTo(o.path);
  }

  @Override
  public void repr(Printer printer) {
    printer.append(path.toString());
  }

  @Override
  public int hashCode() {
    return  path.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CheckoutPath)) {
      return false;
    }
    CheckoutPath other = (CheckoutPath) o;
    return Objects.equals(path, other.path);
  }
}
