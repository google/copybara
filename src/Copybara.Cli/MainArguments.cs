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
using Copybara;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Util;

namespace Copybara.Cli;

/// <summary>
/// Arguments which are unnamed (i.e. positional) or must be evaluated inside <see cref="Main"/>.
/// </summary>
public sealed class MainArguments
{
    public const string CopybaraSkylarkConfigFilename = "copy.bara.sky";

    private readonly ImmutableArray<string> _rawArgs;

    public MainArguments(IEnumerable<string> rawArgs)
    {
        _rawArgs = rawArgs.ToImmutableArray();
    }

    /// <summary>The positional arguments parsed off the command line (config, migration, refs).</summary>
    public List<string> Unnamed { get; set; } = new();

    [Flag(
        "--work-dir",
        "Directory where all the transformations will be performed. By default a temporary"
            + " directory.")]
    public string? BaseWorkdir { get; set; }

    public IReadOnlyList<string> GetRawArgs() => _rawArgs;

    /// <summary>
    /// Returns the base working directory. This method should not be accessed directly by any other
    /// class but <see cref="Main"/>.
    /// </summary>
    public string GetBaseWorkdir(GeneralOptions generalOptions, string fileSystemRoot)
    {
        _ = fileSystemRoot;
        string workdirPath = BaseWorkdir == null
            ? generalOptions.GetDirFactory().NewTempDir("workdir")
            : Path.GetFullPath(BaseWorkdir);

        if (File.Exists(workdirPath) && !Directory.Exists(workdirPath))
        {
            throw new IOException($"'{workdirPath}' exists and is not a directory");
        }

        if (Directory.Exists(workdirPath) && !IsDirEmpty(workdirPath))
        {
            System.Console.Error.WriteLine($"WARNING: {workdirPath} is not empty");
        }

        return workdirPath;
    }

    private static bool IsDirEmpty(string directory) =>
        !Directory.EnumerateFileSystemEntries(directory).Any();

    /// <summary>
    /// Resolves the subcommand and its remaining args from the positional arguments, mirroring
    /// upstream's config-vs-command disambiguation logic.
    /// </summary>
    /// <exception cref="CommandLineException"/>
    public CommandWithArgs ParseCommand(
        IReadOnlyDictionary<string, ICopybaraCmd> commands, ICopybaraCmd defaultCmd)
    {
        if (Unnamed.Count == 0)
        {
            return new CommandWithArgs(defaultCmd, ImmutableArray<string>.Empty);
        }

        string firstArg = Unnamed[0];
        // Default command might take a config file as param.
        if (firstArg.EndsWith(CopybaraSkylarkConfigFilename, StringComparison.Ordinal))
        {
            return new CommandWithArgs(defaultCmd, Unnamed.ToImmutableArray());
        }

        if (firstArg.Contains(CopybaraSkylarkConfigFilename + ':', StringComparison.Ordinal))
        {
            var args = ImmutableArray.CreateBuilder<string>();
            args.AddRange(SplitConfigArg(firstArg));
            args.AddRange(Unnamed.Skip(1));
            return new CommandWithArgs(defaultCmd, args.ToImmutable());
        }

        string key = firstArg.ToLowerInvariant();
        if (!commands.ContainsKey(key))
        {
            var available = new SortedSet<string>(commands.Keys, StringComparer.Ordinal);
            throw new CommandLineException(
                $"Invalid subcommand '{firstArg}'. Available commands: [{string.Join(", ", available)}]");
        }

        if (Unnamed.Count == 1)
        {
            return new CommandWithArgs(commands[key], ImmutableArray<string>.Empty);
        }

        var rest = ImmutableArray.CreateBuilder<string>();
        rest.AddRange(SplitConfigArg(Unnamed[1]));
        rest.AddRange(Unnamed.Skip(2));
        return new CommandWithArgs(commands[key], rest.ToImmutable());
    }

    private static IReadOnlyList<string> SplitConfigArg(string arg)
    {
        int idx = arg.IndexOf("copy.bara.sky:", StringComparison.Ordinal);
        if (idx < 0)
        {
            return ImmutableArray.Create(arg);
        }
        string head = arg.Substring(0, idx) + "copy.bara.sky";
        string tail = arg.Substring(idx + "copy.bara.sky:".Length);
        return ImmutableArray.Create(head, tail);
    }

    /// <summary>A subcommand and the remaining (config/migration/ref) arguments for it.</summary>
    public sealed class CommandWithArgs
    {
        internal CommandWithArgs(ICopybaraCmd subcommand, ImmutableArray<string> args)
        {
            Subcommand = Preconditions.CheckNotNull(subcommand);
            Args = args;
        }

        public ICopybaraCmd Subcommand { get; }

        public IReadOnlyList<string> Args { get; }
    }
}
