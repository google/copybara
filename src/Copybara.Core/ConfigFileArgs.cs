/*
 * Copyright (C) 2018 Google Inc.
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
using Copybara.Common;

namespace Copybara;

/// <summary>
/// Arguments for a command that expects the CLI arguments be like:
/// <c>config_file [workflow [source_ref]]</c>.
/// </summary>
public sealed class ConfigFileArgs
{
    private readonly string _configPath;
    private readonly string? _workflowName;
    private readonly ImmutableArray<string> _sourceRefs;

    public ConfigFileArgs(string configPath, string? workflowName)
        : this(configPath, workflowName, ImmutableArray<string>.Empty)
    {
    }

    public ConfigFileArgs(string configPath, string? workflowName, IEnumerable<string> sourceRefs)
    {
        _configPath = Preconditions.CheckNotNull(configPath);
        _workflowName = workflowName;
        _sourceRefs = sourceRefs.ToImmutableArray();
    }

    public string GetConfigPath() => _configPath;

    public string GetWorkflowName() => _workflowName ?? "default";

    public bool HasWorkflowName() => _workflowName != null;

    /// <summary>
    /// Returns the first sourceRef from the command arguments, or null if no source ref was
    /// provided.
    ///
    /// <para>This method is provided for convenience, for subcommands that only care about the first
    /// source_ref.</para>
    /// </summary>
    public string? GetSourceRef() => _sourceRefs.Length == 0 ? null : _sourceRefs[0];

    public IReadOnlyList<string> GetSourceRefs() => _sourceRefs;
}
