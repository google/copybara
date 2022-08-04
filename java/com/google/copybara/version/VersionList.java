/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.version;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/** List all or a subset of the versions of a repository */
public interface VersionList extends StarlarkValue {

  /** List the versions */
  ImmutableSet<String> list() throws ValidationException, RepoException;

  @Override
  default void repr(Printer printer) {
    printer.append(toString());
  }

  /** A version list that comes from a set of Strings */
  class SetVersionList implements VersionList {
     private final ImmutableSet<String> versions;

    public SetVersionList(ImmutableSet<String> versions) {
      this.versions = versions;
    }

    @Override
    public ImmutableSet<String> list() {
      return versions;
    }
  }
}
