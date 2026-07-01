/*
 * Copyright (C) 2019 Google Inc.
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

using System.Collections.Immutable;
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Util;

namespace Copybara.Transform.Debug;

/// <summary>Workflow debugging tools.</summary>
public class DebugOptions : IOption
{
    [Flag("--debug-metadata-break", "Stop when message and/or author changes")]
    public bool DebugMetadataBreak { get; set; } = false;

    [Flag("--debug-file-break", "Stop when file matching the glob changes")]
    public string? DebugFileBreak { get; set; } = null;

    [Flag("--debug-transform-break", "Stop when transform description matches")]
    public string? DebugTransformBreak { get; set; } = null;

    private readonly GeneralOptions _generalOptions;

    public DebugOptions(GeneralOptions generalOptions)
    {
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
    }

    internal Glob? GetDebugFileBreak() =>
        DebugFileBreak != null
            ? Glob.CreateGlob(ImmutableArray.Create(DebugFileBreak))
            : null;

    internal Regex? GetDebugTransformBreak() =>
        DebugTransformBreak != null ? new Regex(DebugTransformBreak) : null;

    public ITransformation TransformWrapper(ITransformation transformation)
    {
        if (!DebuggerEnabled())
        {
            return transformation;
        }
        return TransformDebug.WithDebugger(
            transformation, this, _generalOptions.GetEnvironment());
    }

    private bool DebuggerEnabled() =>
        DebugMetadataBreak
        || DebugFileBreak != null
        || DebugTransformBreak != null;

    internal string CreateDiffDirectory() =>
        _generalOptions.GetDirFactory().NewTempDir("debug");
}
