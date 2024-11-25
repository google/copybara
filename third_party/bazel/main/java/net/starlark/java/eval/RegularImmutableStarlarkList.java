// Copyright 2023 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.starlark.java.eval;

import com.google.common.base.Preconditions;

/** An immutable implementation of a {@code StarlarkList}. */
final class RegularImmutableStarlarkList<E> extends ImmutableStarlarkList<E> {

  /**
   * A shared instance for the empty list with immutable mutability.
   *
   * <p>Other immutable empty list objects can exist, e.g. lists that were once mutable but whose
   * environments were then frozen. This instance is for empty lists that were always frozen from
   * the beginning.
   */
  static final StarlarkList<?> EMPTY = new RegularImmutableStarlarkList<>(EMPTY_ARRAY);

  private final Object[] elems;

  RegularImmutableStarlarkList(Object[] elems) {
    Preconditions.checkArgument(elems.getClass() == Object[].class);
    this.elems = elems;
  }

  @Override
  Object[] elems() {
    return elems;
  }
}
