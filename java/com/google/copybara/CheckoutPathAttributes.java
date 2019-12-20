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
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.Starlark;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Represents file attributes exposed to Skylark. */
@SuppressWarnings("unused")
@SkylarkModule(
    name = "PathAttributes",
    category = SkylarkModuleCategory.BUILTIN,
    doc = "Represents a path attributes like size.")
public class CheckoutPathAttributes implements StarlarkValue {

  private final Path path;
  private final BasicFileAttributes attributes;

  CheckoutPathAttributes(Path path, BasicFileAttributes attributes) {
    this.path = Preconditions.checkNotNull(path);
    this.attributes = Preconditions.checkNotNull(attributes);
  }

  @SkylarkCallable(
      name = "size",
      doc = "The size of the file. Throws an error if file size > 2GB.",
      structField = true)
  public int size() throws Exception {
    long size = attributes.size();
    try {
      return Math.toIntExact(size);
    } catch (ArithmeticException e) {
      throw Starlark.errorf("File %s is too big to compute the size: %d bytes", path, size);
    }
  }

  @SkylarkCallable(name = "symlink",
      doc = "Returns true if it is a symlink", structField = true)
  public boolean isSymlink() {
    return attributes.isSymbolicLink();
  }
}
