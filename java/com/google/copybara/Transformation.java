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

import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import java.io.IOException;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;
import net.starlark.java.syntax.Location;

/** Interface implemented by all source code transformations. */
@StarlarkBuiltin(
    name = "transformation",
    doc =
        "A single operation which modifies the source checked out from the origin, prior to writing"
            + " it to the destination. Transformations can also be used to perform validations or"
            + " checks.<br/><br/>Many common transformations are provided by the built-in"
            + " libraries, such as <a href='#core'><code>core</code></a>.<br/><br/>Custom"
            + " transformations can be defined in Starlark code via <a"
            + " href='#core.dynamic_transform'><code>core.dynamic_transform</code></a>.")
public interface Transformation extends StarlarkValue {

  /**
   * Transforms the files inside the checkout dir specified by {@code work}.
   *
   * @throws IOException if an error occur during the access to the files
   * @throws ValidationException if an error attributable to the user happened
   */
  TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException, RepoException;

  /**
   * Returns a transformation which runs this transformation in reverse.
   *
   * @throws NonReversibleValidationException if the transform is not reversible
   */
  Transformation reverse() throws NonReversibleValidationException;

  /**
   * Return a high level description of what the transform is doing. Note that this should not be
   * {@link #toString()} method but something more user friendly.
   */
  String describe();

  /**
   * Starlark location of the transformation.
   */
  default Location location() {
    return Location.BUILTIN;
  }

  default boolean canJoin(Transformation transformation) {
    return false;
  }

  default Transformation join(Transformation next) {
    throw new IllegalStateException(String.format(
        "Unexpected join call for %s and %s", this, next));
  }

}
