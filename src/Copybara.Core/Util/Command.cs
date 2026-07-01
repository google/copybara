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
using Copybara.Common;

namespace Copybara.Util;

/// <summary>
/// A description of a subprocess to run: the argv, an optional environment, and an optional working
/// directory. This is the .NET-port equivalent of the Bazel shell library's <c>Command</c> value
/// that Copybara's <see cref="CommandRunner"/> consumes. Actual execution is performed by
/// <see cref="CommandRunner"/> using <c>System.Diagnostics.Process</c>.
/// </summary>
public sealed class Command
{
    private readonly ImmutableArray<string> _commandLineElements;
    private readonly ImmutableDictionary<string, string>? _environmentVariables;
    private readonly string? _workingDirectory;

    public Command(string[] commandLineElements)
        : this(commandLineElements, null, null)
    {
    }

    public Command(
        string[] commandLineElements,
        IReadOnlyDictionary<string, string>? environmentVariables,
        string? workingDirectory)
    {
        Preconditions.CheckNotNull(commandLineElements);
        Preconditions.CheckArgument(
            commandLineElements.Length > 0, "command line elements must not be empty");
        _commandLineElements = commandLineElements.ToImmutableArray();
        _environmentVariables = environmentVariables?.ToImmutableDictionary();
        _workingDirectory = workingDirectory;
    }

    /// <summary>The complete argument vector, including the program name at index 0.</summary>
    public IReadOnlyList<string> GetCommandLineElements() => _commandLineElements;

    /// <summary>The environment variables to run with, or null to inherit the current environment.</summary>
    public IReadOnlyDictionary<string, string>? GetEnvironmentVariables() => _environmentVariables;

    /// <summary>The working directory to run in, or null to inherit the current one.</summary>
    public string? GetWorkingDirectory() => _workingDirectory;

    public override string ToString() => ShellUtils.PrettyPrintArgv(_commandLineElements);
}
