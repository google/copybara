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

using System.Collections.Immutable;
using Copybara.Revision;
using Starlark.Eval;

namespace Copybara.Config;

/// <summary>
/// A migration is a process that moves files and/or metadata (comments, labels...) at a particular
/// revision from one/many systems to one/many destinations.
///
/// <para>For helping with the migration a working directory is provided to do any temporary file
/// operations.</para>
/// </summary>
public interface IMigration
{
    /// <summary>
    /// Run a migration for a list of source references. If empty, the default (if any) will be used.
    ///
    /// <para>Different implementations of Migration might process the list of source references
    /// differently (batching them, or running one by one).</para>
    /// </summary>
    /// <param name="workdir">a working directory for doing file operations if needed.</param>
    /// <param name="sourceRefs">the source references to be migrated. If not present the default
    /// (if any) for the migration will be used.</param>
    void Run(string workdir, IReadOnlyList<string> sourceRefs);

    Info<IRevision> GetInfo() => Info.Empty;

    /// <summary>The migration's name.</summary>
    string GetName();

    /// <summary>An optional description that users can set to describe what this workflow achieves.</summary>
    string? GetDescription();

    /// <summary>The migration's mode.</summary>
    string GetModeString();

    /// <summary>The migration's main config file.</summary>
    ConfigFile GetMainConfigFile();

    /// <summary>
    /// Returns a multimap containing enough data to fingerprint the origin for validation purposes.
    /// </summary>
    Common.ImmutableListMultimap<string, string> GetOriginDescription();

    /// <summary>
    /// Returns a multimap containing enough data to fingerprint the destination for validation
    /// purposes.
    /// </summary>
    Common.ImmutableListMultimap<string, string> GetDestinationDescription();

    /// <summary>Returns a multimap containing enough data to fingerprint credentials used.</summary>
    IReadOnlyList<Common.ImmutableListMultimap<string, string>> GetCredentialDescription();

    /// <summary>Returns the Starlark call stack captured when the migration was defined.</summary>
    ImmutableArray<StarlarkThread.CallStackEntry> GetDefinitionStack() =>
        ImmutableArray<StarlarkThread.CallStackEntry>.Empty;
}
