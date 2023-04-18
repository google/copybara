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

package com.google.copybara.python;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ArrayListMultimap;
import com.google.copybara.CheckoutPath;
import com.google.copybara.python.PackageMetadata.EmptyMetadataException;
import java.io.IOException;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;
import net.starlark.java.eval.Tuple;

/** Module for python ecosystem support */
@StarlarkBuiltin(name = "python", doc = "utilities for interacting with the pypi package manager")
public class PythonModule implements StarlarkValue {
  @StarlarkMethod(
      name = "parse_metadata",
      doc =
          "Extract the metadata from a python METADATA file into a dictionary. Returns a list of"
              + " key value tuples.",
      parameters = {
        @Param(
            name = "path",
            doc = "path relative to workdir root of the .whl file",
            allowedTypes = {@ParamType(type = CheckoutPath.class)})
      })
  public Sequence<Tuple> extractMetadata(CheckoutPath path)
      throws IOException, EmptyMetadataException {
    ArrayListMultimap<String, String> metadata = PackageMetadata.getMetadata(path);

    return StarlarkList.immutableCopyOf(
        metadata.entries().stream()
            .map(entry -> Tuple.of(entry.getKey(), entry.getValue()))
            .collect(toImmutableList()));
  }
}
