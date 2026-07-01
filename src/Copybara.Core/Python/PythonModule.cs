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

using System.Linq;
using Starlark.Annot;
using Starlark.Eval;
using Tuple = Starlark.Eval.Tuple;

namespace Copybara.Python;

/// <summary>Module for python ecosystem support.</summary>
[StarlarkBuiltin("python", Doc = "utilities for interacting with the pypi package manager")]
public class PythonModule : IStarlarkValue
{
    [StarlarkMethod("parse_metadata",
        Doc =
            "Extract the metadata from a python METADATA file into a dictionary. Returns a list of"
            + " key value tuples.")]
    public StarlarkList ExtractMetadata(
        [Param(
            Name = "path",
            Doc = "path relative to workdir root of the .whl file",
            AllowedTypes = new[] { typeof(CheckoutPath) })]
        CheckoutPath path)
    {
        IReadOnlyList<KeyValuePair<string, string>> metadata = PackageMetadata.GetMetadata(path);

        return StarlarkList.ImmutableCopyOf(
            metadata.Select(entry => (object?)Tuple.Of(entry.Key, entry.Value)));
    }
}
