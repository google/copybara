/*
 * Copyright (C) 2026 Google LLC.
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

using Starlark.Annot;
using Starlark.Eval;

namespace Copybara;

/// <summary>An object used to configure Consistency File options.</summary>
[StarlarkBuiltin("core.consistency_file_config", Documented = true)]
public sealed class ConsistencyFileConfiguration : IStarlarkValue
{
    private readonly string _path;
    private readonly bool _excludeBuildFiles;

    private ConsistencyFileConfiguration(string path, bool excludeBuildFiles)
    {
        _path = path;
        _excludeBuildFiles = excludeBuildFiles;
    }

    public static ConsistencyFileConfiguration Create(string path, bool excludeBuildFiles) =>
        new(path, excludeBuildFiles);

    public string Path() => _path;

    public bool ExcludeBuildFiles() => _excludeBuildFiles;
}
