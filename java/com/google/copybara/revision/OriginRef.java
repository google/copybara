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
package com.google.copybara.revision;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.util.Objects;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/**
 * Reference to the change/review read from the origin.
 */
@StarlarkBuiltin(
    name = "origin_ref",
    doc = "Reference to the change/review in the origin.")
public class OriginRef implements StarlarkValue {

  private final String ref;

  @VisibleForTesting
  public OriginRef(String id) {
    this.ref = Preconditions.checkNotNull(id);
  }

  /**
   * Origin reference
   */
  @StarlarkMethod(name = "ref", doc = "Origin reference ref", structField = true)
  public String getRef() {
    return ref;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OriginRef originRef = (OriginRef) o;
    return Objects.equals(ref, originRef.ref);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(ref);
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("ref", ref)
        .toString();
  }
}
