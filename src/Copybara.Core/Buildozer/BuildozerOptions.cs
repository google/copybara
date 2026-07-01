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
using System.Text;
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Format;
using Copybara.Util;
using Copybara.Util.Console;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using Command = Copybara.Util.Command;
using CommandException = Copybara.Util.CommandException;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Buildozer;

/// <summary>Specifies how Buildozer is executed.</summary>
public sealed class BuildozerOptions : IOption
{
    private static readonly ILogger Logger = NullLogger.Instance;

    private static readonly Regex TargetNotFound = new(
        @".*error while executing commands \[.+\] on target (?<error>.* not found)",
        RegexOptions.Compiled);

    private readonly GeneralOptions _generalOptions;
    private readonly BuildifierOptions _buildifierOptions;
    private readonly WorkflowOptions _workflowOptions;

    public BuildozerOptions(
        GeneralOptions generalOptions,
        BuildifierOptions buildifierOptions,
        WorkflowOptions workflowOptions)
    {
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _buildifierOptions = Preconditions.CheckNotNull(buildifierOptions);
        _workflowOptions = workflowOptions;
    }

    [Flag(
        "--buildozer-bin",
        "Binary to use for buildozer (Default is /usr/bin/buildozer)",
        Hidden = true)]
    public string BuildozerBin { get; set; } = "/usr/bin/buildozer";

    private void LogError(Console console, CommandOutput output)
    {
        Consoles.ErrorLogLines(console, "buildozer stdout: ", output.GetStdout());
        Consoles.ErrorLogLines(console, "buildozer stderr: ", output.GetStderr());
    }

    public sealed class BuildozerCommand
    {
        private readonly IReadOnlyList<string> _targets;
        private readonly string _cmd;

        internal BuildozerCommand(IReadOnlyList<string> targets, string cmd)
        {
            _targets = Preconditions.CheckNotNull(targets);
            _cmd = Preconditions.CheckNotNull(cmd);
        }

        internal BuildozerCommand(string targets, string cmd)
        {
            _targets = ImmutableArray.Create(Preconditions.CheckNotNull(targets));
            _cmd = Preconditions.CheckNotNull(cmd);
        }

        public override string ToString() => _cmd + "|" + string.Join('|', _targets);
    }

    /// <exception cref="ValidationException"/>
    /// <exception cref="TargetNotFoundException"/>
    internal void Run(Console console, string checkoutDir, IEnumerable<BuildozerCommand> commands)
    {
        string unused = RunCaptureOutput(console, checkoutDir, commands);
    }

    /// <summary>Runs buildozer with the given commands.</summary>
    /// <exception cref="ValidationException"/>
    /// <exception cref="TargetNotFoundException"/>
    internal string RunCaptureOutput(
        Console console, string checkoutDir, IEnumerable<BuildozerCommand> commands)
    {
        var commandList = commands.ToList();
        var args = new List<string>
        {
            BuildozerBin,
            "-buildifier=" + _buildifierOptions.BuildifierBin,
        };

        // We only use -k in keep going mode because it shows less errors (http://b/69386431)
        if (_workflowOptions.IgnoreNoop)
        {
            args.Add("-k");
        }
        args.Add("-f");
        args.Add("-");
        try
        {
            var cmd = new Copybara.Util.Command(args.ToArray(), null, checkoutDir);
            CommandOutputWithStatus output = _generalOptions.NewCommandRunner(cmd)
                .WithVerbose(_generalOptions.IsVerbose())
                .WithInput(Encoding.UTF8.GetBytes(string.Join('\n', commandList)))
                .Execute();
            if (output.GetStdout().Length != 0)
            {
                Logger.LogInformation("buildozer stdout: {Stdout}", output.GetStdout());
            }
            if (output.GetStderr().Length != 0)
            {
                Logger.LogInformation("buildozer stderr: {Stderr}", output.GetStderr());
            }
            return output.GetStdout();
        }
        catch (BadExitStatusWithOutputException e)
        {
            // Don't print the output for common/known errors.
            if (_generalOptions.IsVerbose())
            {
                LogError(console, e.GetOutput());
            }
            if (e.GetResult().TerminationStatus.GetExitCode() == 3)
            {
                // Buildozer exits with code == 3 when the build file was not modified and no output
                // was generated. This happens with expressions that match multiple targets, like
                // :%java_library
                throw new TargetNotFoundException(
                    CommandsMessage("Buildozer could not find a target for", commandList));
            }
            if (e.GetResult().TerminationStatus.GetExitCode() == 2)
            {
                var errors = e.GetOutput().GetStderr()
                    .Split('\n')
                    .Where(s => !(s.Length == 0 || s.StartsWith("fixed ", StringComparison.Ordinal)))
                    .ToList();
                var notFoundMsg = new List<string>();
                bool allNotFound = true;
                foreach (string error in errors)
                {
                    Match matcher = TargetNotFound.Match(error);
                    if (matcher.Success)
                    {
                        notFoundMsg.Add(
                            $"Buildozer could not find a target for {matcher.Groups["error"].Value}");
                    }
                    else if (error.Contains("no such file or directory")
                             || error.Contains("not a directory"))
                    {
                        notFoundMsg.Add("Buildozer could not find build file: " + error);
                    }
                    else
                    {
                        allNotFound = false;
                    }
                }
                if (allNotFound)
                {
                    throw new TargetNotFoundException(string.Join('\n', notFoundMsg));
                }
            }
            // Otherwise we have already printed above.
            if (!_generalOptions.IsVerbose())
            {
                LogError(console, e.GetOutput());
            }
            throw new ValidationException(
                string.Format(
                    "{0}\nCommand stderr:{1}",
                    CommandsMessage("Failed to execute buildozer with args", commandList),
                    e.GetOutput().GetStderr()),
                e);
        }
        catch (CommandException e)
        {
            string message = string.Format(
                "Error '{0}' running buildozer command: {1}",
                e.Message,
                e.GetCommand());
            console.Error(message);
            throw new ValidationException(message, e);
        }
    }

    private static string CommandsMessage(string prefix, IEnumerable<BuildozerCommand> commands) =>
        prefix + ":\n  " + string.Join("\n  ", commands);
}
