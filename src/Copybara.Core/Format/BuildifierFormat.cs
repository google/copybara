/*
 * Copyright (C) 2020 Google Inc.
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
using Copybara.Exceptions;
using Copybara.Util;
using Copybara.Util.Console;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using Command = Copybara.Util.Command;
using CommandException = Copybara.Util.CommandException;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Format;

/// <summary>Format using buildifier.</summary>
public class BuildifierFormat : ITransformation
{
    private static readonly ILogger Logger = NullLogger.Instance;

    private readonly BuildifierOptions _buildifierOptions;
    private readonly GeneralOptions _generalOptions;
    private readonly Glob _glob;
    private readonly LintMode _lintMode;
    private readonly ImmutableArray<string> _warnings;
    private readonly string? _type;

    internal BuildifierFormat(
        BuildifierOptions buildifierOptions,
        GeneralOptions generalOptions,
        Glob glob,
        LintMode lintMode,
        ImmutableArray<string> warnings,
        string? type)
    {
        _buildifierOptions = Preconditions.CheckNotNull(buildifierOptions);
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _glob = Preconditions.CheckNotNull(glob);
        _lintMode = lintMode;
        _warnings = warnings;
        _type = type;
    }

    public TransformationStatus Transform(TransformWork work)
    {
        string checkoutDir = work.GetCheckoutDir();
        IPathMatcher pathMatcher = _glob.RelativeTo(checkoutDir);
        var paths = ImmutableArray.CreateBuilder<string>();
        foreach (string file in Directory.EnumerateFiles(
                     checkoutDir, "*", SearchOption.AllDirectories))
        {
            if (pathMatcher.Matches(file))
            {
                paths.Add(Path.GetFullPath(file));
            }
        }

        ImmutableArray<string> builtPaths = paths.ToImmutable();
        if (builtPaths.Length == 0)
        {
            return TransformationStatus.Noop(_glob + " didn't match any build file to format");
        }

        for (int i = 0; i < builtPaths.Length; i += _buildifierOptions.BatchSize)
        {
            var sublist = builtPaths.Skip(i).Take(_buildifierOptions.BatchSize).ToList();
            Run(work.GetConsole(), checkoutDir, sublist);
        }

        return TransformationStatus.Success();
    }

    /// <summary>Runs buildifier with the given arguments.</summary>
    private void Run(Console console, string checkoutDir, IReadOnlyList<string> args)
    {
        var argBuilder = new List<string> { _buildifierOptions.BuildifierBin };
        if (_type != null)
        {
            argBuilder.Add("-type=" + _type);
        }
        if (_lintMode != LintMode.Off)
        {
            argBuilder.Add("-lint=" + _lintMode.ToString().ToLowerInvariant());
            if (!_warnings.IsDefaultOrEmpty)
            {
                argBuilder.Add("-warnings=" + string.Join(",", _warnings));
            }
        }
        argBuilder.AddRange(args);

        try
        {
            var cmd = new Command(argBuilder.ToArray(), null, checkoutDir);
            CommandOutputWithStatus output = _generalOptions.NewCommandRunner(cmd)
                .WithVerbose(_generalOptions.IsVerbose())
                .Execute();
            if (output.GetStdout().Length != 0)
            {
                Logger.LogInformation("buildifier stdout: {Stdout}", output.GetStdout());
            }
            if (output.GetStderr().Length != 0)
            {
                Logger.LogInformation("buildifier stderr: {Stderr}", output.GetStderr());
            }
        }
        catch (BadExitStatusWithOutputException e)
        {
            Log(console, e.GetOutput());
            ValidationException.CheckCondition(
                e.GetResult().TerminationStatus.GetExitCode() != 1,
                "Build file(s) couldn't be formatted because there was a syntax error");
            throw new IOException(
                "Failed to execute buildifier with args: " + string.Join(" ", args), e);
        }
        catch (CommandException e)
        {
            throw new IOException(
                "Failed to execute buildifier with args: " + string.Join(" ", args), e);
        }
    }

    private static void Log(Console console, CommandOutput output)
    {
        Consoles.LogLines(console, "buildifier stdout: ", output.GetStdout());
        Consoles.LogLines(console, "buildifier stderr: ", output.GetStderr());
    }

    public ITransformation Reverse() => this;

    public string Describe() => "Buildifier";

    /// <summary>Valid modes that we support for buildifier -lint flag.</summary>
    public enum LintMode
    {
        Off,

        // Warn, // Not exposed for now since we don't show the stderr/out warnings in the console
        Fix,
    }
}
