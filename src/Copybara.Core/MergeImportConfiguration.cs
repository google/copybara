/*
 * Copyright (C) 2023 Google LLC.
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

using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara;

/// <summary>An object used to configure Merge Import.</summary>
[StarlarkBuiltin("core.merge_import_config", Documented = false)]
public sealed class MergeImportConfiguration : IStarlarkValue
{
    /// <summary>
    /// The strategy to use for merging files.
    /// <para>DIFF3 shells out to diff3 with the -m flag to perform a 3-way merge. PATCH_MERGE creates
    /// a patch file by diffing the baseline and destination files, and then applies the patch to the
    /// origin file.</para>
    /// </summary>
    public enum MergeStrategy
    {
        DIFF3,
        PATCH_MERGE,
        UNKNOWN,
    }

    private readonly string _packagePath;
    private readonly Glob _paths;
    private readonly bool _useConsistencyFile;
    private readonly MergeStrategy _mergeStrategy;

    private MergeImportConfiguration(
        string packagePath, Glob paths, bool useConsistencyFile, MergeStrategy mergeStrategy)
    {
        _packagePath = packagePath;
        _paths = paths;
        _useConsistencyFile = useConsistencyFile;
        _mergeStrategy = mergeStrategy;
    }

    public static MergeImportConfiguration Create(
        string packagePath, Glob paths, bool useConsistencyFile, MergeStrategy mergeStrategy) =>
        new(packagePath, paths, useConsistencyFile, mergeStrategy);

    public string PackagePath() => _packagePath;

    public Glob Paths() => _paths;

    public bool UseConsistencyFile() => _useConsistencyFile;

    public MergeStrategy Strategy() => _mergeStrategy;

    public override bool Equals(object? obj) =>
        obj is MergeImportConfiguration o
        && _packagePath == o._packagePath
        && Equals(_paths, o._paths)
        && _useConsistencyFile == o._useConsistencyFile
        && _mergeStrategy == o._mergeStrategy;

    public override int GetHashCode() =>
        HashCode.Combine(_packagePath, _paths, _useConsistencyFile, _mergeStrategy);
}
