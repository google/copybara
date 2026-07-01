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

using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara;

/// <summary>Parameters for customizing auto patch file generation.</summary>
[StarlarkBuiltin(
    "core.autopatch_config",
    Doc = "The configuration that describes automatic patch file generation")]
public sealed class AutoPatchfileConfiguration : IStarlarkValue
{
    private readonly string? _header;
    private readonly string _suffix;
    private readonly string _directoryPrefix;
    private readonly string? _directory;
    private readonly bool _stripFilenames;
    private readonly bool _stripLineNumbers;
    private readonly Glob _glob;

    private AutoPatchfileConfiguration(
        string? header,
        string suffix,
        string directoryPrefix,
        string? directory,
        bool stripFilenames,
        bool stripLineNumbers,
        Glob glob)
    {
        _header = header;
        _suffix = suffix;
        _directoryPrefix = directoryPrefix;
        _directory = directory;
        _stripFilenames = stripFilenames;
        _stripLineNumbers = stripLineNumbers;
        _glob = glob;
    }

    public static AutoPatchfileConfiguration Create(
        string? header,
        string suffix,
        string directoryPrefix,
        string? directory,
        bool stripFilenames,
        bool stripLineNumbers,
        Glob glob) =>
        new(header, suffix, directoryPrefix, directory, stripFilenames, stripLineNumbers, glob);

    public string? Header() => _header;

    public string Suffix() => _suffix;

    public string DirectoryPrefix() => _directoryPrefix;

    public string? Directory() => _directory;

    public bool StripFilenames() => _stripFilenames;

    public bool StripLineNumbers() => _stripLineNumbers;

    public Glob GlobValue() => _glob;
}
