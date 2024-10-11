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

package com.google.copybara;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/** A Starlark module for randomization-related functions. */
@StarlarkBuiltin(name = "random", doc = "A module for randomization-related functions.")
public class StarlarkRandomModule implements StarlarkValue {
  @StarlarkMethod(
      name = "sample",
      doc = "Returns a list of k unique elements randomly sampled from the list.",
      parameters = {
        @Param(
            name = "population",
            doc = "The list to sample from.",
            allowedTypes = {@ParamType(type = StarlarkList.class)},
            named = true),
        @Param(
            name = "k",
            doc = "The number of elements to sample from the population list.",
            allowedTypes = {@ParamType(type = StarlarkInt.class)},
            named = true)
      })
  public StarlarkList<?> sampleStarlarkList(StarlarkList<?> population, StarlarkInt k)
      throws EvalException {
    // A StarlarkList might be immutable. Make a mutable copy that we can
    // shuffle and return.
    List<?> mutableList = new ArrayList<>(population);
    Collections.shuffle(mutableList);
    int kInt = k.toInt("k");
    try {
      return StarlarkList.immutableCopyOf(mutableList.subList(0, kInt));
    } catch (IndexOutOfBoundsException e) {
      throw new EvalException(
          String.format(
              "k is out of bounds. Must be >= 0 and <= %s. Current" + " value: %s",
              population.size(), kInt), e);
    }
  }
}
