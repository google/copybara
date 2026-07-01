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
using Copybara;
using Copybara.Common;
using Copybara.Exceptions;

namespace Copybara.Cli;

/// <summary>
/// Environment information for command execution: arguments, workdir, etc.
/// </summary>
public sealed class CommandEnv
{
    private readonly string _workdir;
    private readonly Options _options;
    private readonly MainArguments? _mainArgs;
    private readonly ImmutableArray<string> _args;
    private ConfigFileArgs? _configFileArgs;

    public CommandEnv(
        string workdir, Options options, IEnumerable<string> args, MainArguments? mainArgs)
    {
        _workdir = Preconditions.CheckNotNull(workdir);
        _options = Preconditions.CheckNotNull(options);
        _args = args.ToImmutableArray();
        _mainArgs = mainArgs;
    }

    /// <summary>
    /// Instantiate a new CommandEnv. Meant for use with construction of new ICopybaraCmd objects.
    /// </summary>
    public CommandEnv(string workdir, Options options, IEnumerable<string> args)
        : this(workdir, options, args, null)
    {
    }

    /// <summary>
    /// Get the arguments parsed as config [migration [source_ref]...] if the command uses that
    /// format.
    /// </summary>
    public ConfigFileArgs? GetConfigFileArgs() => _configFileArgs;

    public MainArguments? GetMainArgs() => _mainArgs;

    /// <summary>Parse the CLI arguments as config [workflow [source_ref]...].</summary>
    /// <exception cref="CommandLineException"/>
    public ConfigFileArgs ParseConfigFileArgs(ICopybaraCmd cmd, bool usesSourceRef)
    {
        Preconditions.CheckState(
            _configFileArgs == null,
            "ParseConfigFileArgs was already called. Only one invocation allowed.");
        if (_args.IsDefaultOrEmpty)
        {
            throw new CommandLineException(
                $"Configuration file missing for '{cmd.Name}' subcommand.");
        }

        string configPath = _args[0];

        if (_args.Length < 2)
        {
            _configFileArgs = new ConfigFileArgs(configPath, workflowName: null);
            return _configFileArgs;
        }

        string workflowName = _args[1];
        if (_args.Length < 3)
        {
            _configFileArgs = new ConfigFileArgs(configPath, workflowName);
            return _configFileArgs;
        }

        if (!usesSourceRef)
        {
            throw new CommandLineException(
                $"Too many arguments for subcommand '{cmd.Name}'");
        }

        _configFileArgs = new ConfigFileArgs(
            configPath, workflowName, _args.Skip(2));
        return _configFileArgs;
    }

    public string GetWorkdir() => _workdir;

    public Options GetOptions() => _options;

    public IReadOnlyList<string> GetArgs() => _args;
}
