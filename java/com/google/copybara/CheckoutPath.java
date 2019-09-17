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

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.doc.annotations.DocSignaturePrefix;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.ResolvedSymlink;
import com.google.copybara.util.Glob;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Runtime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Represents a file that is exposed to Skylark.
 *
 * <p>Files are always relative to the checkout dir and normalized.
 */
@SuppressWarnings("unused")
@SkylarkModule(name = "Path",
    category = SkylarkModuleCategory.BUILTIN,
    doc = "Represents a path in the checkout directory")
@DocSignaturePrefix("path")
public class CheckoutPath implements Comparable<CheckoutPath>, SkylarkValue{

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Path path;
  private final Path checkoutDir;

  CheckoutPath(Path path, Path checkoutDir) {
    this.path = Preconditions.checkNotNull(path);
    this.checkoutDir = Preconditions.checkNotNull(checkoutDir);
  }

  private CheckoutPath create(Path path, Location location) throws EvalException {
    return createWithCheckoutDir(path, checkoutDir, location);
  }

  static CheckoutPath createWithCheckoutDir(Path relative, Path checkoutDir,
      Location location) throws EvalException {
    if (relative.isAbsolute()) {
      throw new EvalException(location, "Absolute paths are not allowed: " + relative);
    }
    return new CheckoutPath(relative.normalize(), checkoutDir);
  }

  @SkylarkCallable(name = "path", doc = "Full path relative to the checkout directory",
      structField = true)
  public String fullPath() {
    return path.toString();
  }

  @SkylarkCallable(name = "name",
      doc = "Filename of the path. For foo/bar/baz.txt it would be baz.txt",
      structField = true)
  public String name() {
    return path.getFileName().toString();
  }

  @SkylarkCallable(
      name = "parent",
      doc = "Get the parent path",
      structField = true,
      allowReturnNones = true,
      useLocation = true)
  public Object parent(Location location) throws EvalException {
    Path parent = path.getParent();
    if (parent == null) {
      // nio equivalent of new_path("foo").parent returns null, but we want to be able to do
      // foo.parent.resolve("bar"). While sibbling could be use for this, sometimes we'll need
      // to return the parent folder and another function resolve a path based on that.
      return path.toString().equals("")
          ? Runtime.NONE
          : create(path.getFileSystem().getPath(""), location);
    }
    return create(parent, location);
  }

  @SkylarkCallable(
      name = "relativize",
      doc =
          "Constructs a relative path between this path and a given path. For example:<br>"
              + "    path('a/b').relativize('a/b/c/d')<br>"
              + "returns 'c/d'",
      parameters = {
        @Param(
            name = "other",
            type = CheckoutPath.class,
            doc = "The path to relativize against this path"),
      }, useLocation = true)
  public CheckoutPath relativize(CheckoutPath other, Location location) throws EvalException {
    return create(path.relativize(other.path), location);
  }

  @SkylarkCallable(
      name = "resolve",
      doc = "Resolve the given path against this path.",
      parameters = {
        @Param(
            name = "child",
            type = Object.class,
            doc =
                "Resolve the given path against this path. The parameter"
                    + " can be a string or a Path.")
      }, useLocation = true)
  public CheckoutPath resolve(Object child, Location location) throws EvalException {
    if (child instanceof String) {
      return create(path.resolve((String) child), location);
    } else if (child instanceof CheckoutPath) {
      return create(path.resolve(((CheckoutPath) child).path), location);
    }
    throw new EvalException(location,
        "Cannot resolve children for type " + child.getClass().getSimpleName() + ":" + child);
  }

  @SkylarkCallable(
      name = "resolve_sibling",
      doc = "Resolve the given path against this path.",
      parameters = {
        @Param(
            name = "other",
            type = Object.class,
            doc =
                "Resolve the given path against this path. The parameter can be a string or"
                    + " a Path."),
      }, useLocation = true)
  public CheckoutPath resolveSibling(Object other, Location location) throws EvalException {
    if (other instanceof String) {
      return create(path.resolveSibling((String) other), location);
    } else if (other instanceof CheckoutPath) {
      return create(path.resolveSibling(((CheckoutPath) other).path), location);
    }
    throw new EvalException(location,
        "Cannot resolve sibling for type " + other.getClass().getSimpleName() + ": " + other);
  }

  @SkylarkCallable(
      name = "attr",
      doc = "Get the file attributes, for example size.",
      structField = true,
      useLocation = true)
  public CheckoutPathAttributes attr(Location location) throws EvalException {
    try {
      return new CheckoutPathAttributes(path,
          Files.readAttributes(checkoutDir.resolve(path), BasicFileAttributes.class,
              LinkOption.NOFOLLOW_LINKS));
    } catch (IOException e) {
      String msg = "Error getting attributes for " + path + ":" + e;
      logger.atSevere().withCause(e).log(msg);
      throw new EvalException(location, msg); // or IOException?
    }
  }

  @SkylarkCallable(name = "read_symlink", doc = "Read the symlink", useLocation = true)
  public CheckoutPath readSymbolicLink(Location location) throws EvalException {
    try {
      Path symlinkPath = checkoutDir.resolve(path);
      if (!Files.isSymbolicLink(symlinkPath)) {
        throw new EvalException(location, String.format("%s is not a symlink", path));
      }

      ResolvedSymlink resolvedSymlink =
          FileUtil.resolveSymlink(Glob.ALL_FILES.relativeTo(checkoutDir), symlinkPath);
      if (!resolvedSymlink.isAllUnderRoot()) {
        throw new EvalException(
            location,
            String.format(
                "Symlink %s points to a file outside the checkout dir: %s",
                symlinkPath, resolvedSymlink.getRegularFile()));
      }

      return create(checkoutDir.relativize(resolvedSymlink.getRegularFile()), location);
    } catch (IOException e) {
      String msg = String.format("Cannot resolve symlink %s: %s", path, e);
      logger.atSevere().withCause(e).log(msg);
      throw new EvalException(null, msg, e);
    }
  }

  public Path getPath() {
    return path;
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
  public void repr(SkylarkPrinter printer) {
    printer.append(path.toString());
  }
}
