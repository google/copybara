/*
 * Copyright (C) 2024 Google LLC.
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.util.GlobAtom.AtomType;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkList;

/** A "glob" that matches lists of fully qualified file names. */
class SequenceGlob extends Glob {

  private SequenceGlob(Iterable<GlobAtom> include) {
    super(include, ImmutableList.of(), null);
  }

  @Override
  String toStringWithParentheses(boolean isRootGlob) {
    return toStringList(include);
  }

  @Override
  public PathMatcher relativeTo(Path path) {
    ImmutableSet<Path> matcher =
        include.stream().map(GlobAtom::pattern).map(path::resolve).collect(toImmutableSet());
    return matcher::contains;
  }

  public static SequenceGlob ofStarlarkList(StarlarkList<?> patterns) throws EvalException {
    ImmutableList.Builder<GlobAtom> atoms = ImmutableList.builder();
    for (Object pattern : patterns) {
      atoms.add(GlobAtom.of(pattern.toString(), AtomType.SINGLE_FILE));
      if (!(pattern instanceof String)) {
        throw new EvalException("Only strings are supported in file lists.");
      }
      if (GlobAtom.AtomType.isMeta(pattern.toString())) {
        throw new EvalException("Wildcards are not supported in file lists.");
      }
    }
    return new SequenceGlob(atoms.build());
  }
}
