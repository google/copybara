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
using Copybara.Config;
using Copybara.Revision;
using Copybara.Util;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Cli;

/// <summary>
/// Reads the last migrated revision in the origin and destination.
/// </summary>
public sealed class InfoCmd : ICopybaraCmd
{
    private const int RevisionMaxLength = 15;
    private const int DescriptionMaxLength = 80;
    private const int AuthorMaxLength = 40;
    private const string DateFormat = "yyyy-MM-dd HH:mm:ss";

    private readonly IConfigLoaderProvider _configLoaderProvider;
    private readonly IContextProvider _contextProvider;

    public InfoCmd(IConfigLoaderProvider configLoaderProvider, IContextProvider contextProvider)
    {
        _configLoaderProvider = Preconditions.CheckNotNull(configLoaderProvider);
        _contextProvider = Preconditions.CheckNotNull(contextProvider);
    }

    public ExitCode Run(CommandEnv commandEnv)
    {
        ConfigFileArgs configFileArgs = commandEnv.GetConfigFileArgs()!;
        GeneralOptions generalOptions = commandEnv.GetOptions().Get<GeneralOptions>();
        Console console = generalOptions.GetConsole();
        bool includeDefinitions = generalOptions.InfoIncludeDefinition;
        ConfigWithDependencies config = _configLoaderProvider
            .NewLoader(configFileArgs.GetConfigPath(), configFileArgs.GetSourceRef())
            .LoadWithDependencies(console);

        if (generalOptions.InfoListOnly)
        {
            ListMigrations(commandEnv, config.GetConfig(), includeDefinitions);
            return ExitCode.Success;
        }

        if (configFileArgs.HasWorkflowName())
        {
            IReadOnlyDictionary<string, string> context = _contextProvider.GetContext(
                config, configFileArgs, _configLoaderProvider, commandEnv.GetOptions(), console);
            bool hasAvailableChanges =
                InfoWithFailureHandling(
                    commandEnv.GetOptions(),
                    config.GetConfig(),
                    configFileArgs.GetWorkflowName(),
                    context);
            return hasAvailableChanges ? ExitCode.Success : ExitCode.NoOp;
        }

        ShowAllMigrations(commandEnv, config.GetConfig(), includeDefinitions);
        return ExitCode.Success;
    }

    private static string GetShortFileName(string? path)
    {
        if (string.IsNullOrEmpty(path))
        {
            return "";
        }
        int idx = path.LastIndexOf('/');
        return idx >= 0 ? path.Substring(idx + 1) : path;
    }

    private static string FormatStackEntry(
        ImmutableArray<StarlarkThread.CallStackEntry> callStack, int stackIndex, bool extraSpacing)
    {
        Preconditions.CheckArgument(stackIndex > 0, "Index must be greater than 0");
        string definitionName = callStack[stackIndex].Name;
        int line = callStack[stackIndex - 1].Location.Line;
        if (line != 0)
        {
            string callerFile = GetShortFileName(callStack[stackIndex - 1].Location.File);
            string mainFile = GetShortFileName(callStack[0].Location.File);
            if (!callerFile.Equals(mainFile, StringComparison.Ordinal))
            {
                string spacing = extraSpacing ? " " : "";
                return $"{definitionName}@{line}{spacing}[{callerFile}]";
            }
            return $"{definitionName}@{line}";
        }
        return definitionName;
    }

    private static void ListMigrations(
        CommandEnv commandEnv, Config.Config config, bool includeDefinitions)
    {
        Console console = commandEnv.GetOptions().Get<GeneralOptions>().GetConsole();
        if (includeDefinitions)
        {
            var entries = new List<string>();
            foreach (string name in config.GetMigrations().Keys.OrderBy(n => n, StringComparer.Ordinal))
            {
                IMigration m = config.GetMigration(name);
                var callStack = m.GetDefinitionStack();
                if (!callStack.IsDefaultOrEmpty && callStack.Length > 1)
                {
                    var fullStack = new System.Text.StringBuilder(FormatStackEntry(callStack, 1, false));
                    for (int i = 2; i < callStack.Length; i++)
                    {
                        fullStack.Append("->").Append(FormatStackEntry(callStack, i, false));
                    }
                    entries.Add(name + ":" + fullStack);
                }
            }
            console.InfoFmt("MIGRATIONS+DEFINITIONSTACK: %s", string.Join(",", entries));
        }
        else
        {
            console.InfoFmt(
                "MIGRATIONS: %s",
                string.Join(
                    ",", config.GetMigrations().Keys.OrderBy(n => n, StringComparer.Ordinal)));
        }
    }

    private static void ShowAllMigrations(
        CommandEnv commandEnv, Config.Config config, bool includeDefinitions)
    {
        TablePrinter table;
        var sortedMigrations = config.GetMigrations().Values
            .OrderBy(m => m.GetName(), StringComparer.Ordinal)
            .ToImmutableArray();

        if (includeDefinitions)
        {
            table = new TablePrinter("Name", "Definition", "Description");
            foreach (IMigration m in sortedMigrations)
            {
                var callStack = m.GetDefinitionStack();
                if (!callStack.IsDefaultOrEmpty && callStack.Length > 1)
                {
                    table.AddRow(
                        m.GetName(),
                        FormatStackEntry(callStack, 1, true),
                        m.GetDescription() ?? "");
                    for (int i = 2; i < callStack.Length; i++)
                    {
                        table.AddRow("", "↳ " + FormatStackEntry(callStack, i, true), "");
                    }
                }
            }
        }
        else
        {
            table = new TablePrinter("Name", "Origin", "Destination", "Mode", "Description");
            foreach (IMigration m in sortedMigrations)
            {
                table.AddRow(
                    m.GetName(),
                    PrettyOriginDestination(m.GetOriginDescription()),
                    PrettyOriginDestination(m.GetDestinationDescription()),
                    m.GetModeString(),
                    m.GetDescription() ?? "");
            }
        }

        Console console = commandEnv.GetOptions().Get<GeneralOptions>().GetConsole();
        foreach (string line in table.Build())
        {
            console.Info(line);
        }
        console.Info(
            "To get information about the state of any migration run:\n\n"
            + "    copybara info " + config.GetLocation() + " [workflow_name]"
            + "\n");
    }

    private static string PrettyOriginDestination(ImmutableListMultimap<string, string> desc)
    {
        string type = desc["type"].Single();
        var urls = desc["url"];
        return type + (urls.Length > 0 ? " (" + urls[0] + ")" : "");
    }

    /// <summary>Retrieves the info of the migration and prints it to the console.</summary>
    private static bool InfoWithFailureHandling(
        Options options, Config.Config config, string migrationName,
        IReadOnlyDictionary<string, string> context)
    {
        // TODO(port): dispatch InfoFailedEvent via eventMonitors() once the monitor package is
        // ported. Currently only the info body is executed.
        _ = context;
        return Info(options, config, migrationName);
    }

    private static bool Info(Options options, Config.Config config, string migrationName)
    {
        Info<IRevision> info = GetInfo(migrationName, config);
        Console console = options.Get<GeneralOptions>().GetConsole();
        int outputSize = 0;
        bool hasAvailableChanges = false;
        foreach (MigrationReference<IRevision> migrationRef in info.MigrationReferences)
        {
            console.Info(string.Format(
                "'{0}': last_migrated {1} - last_available {2}.",
                migrationRef.GetLabel(),
                migrationRef.LastMigrated != null ? migrationRef.LastMigrated.AsString() : "None",
                migrationRef.GetLastAvailableToMigrate() != null
                    ? migrationRef.GetLastAvailableToMigrate()!.AsString()
                    : "None"));

            var availableToMigrate = migrationRef.GetAvailableToMigrate();
            int outputLimit = options.Get<GeneralOptions>().GetOutputLimit();
            if (availableToMigrate.Count > 0)
            {
                hasAvailableChanges = true;
                console.InfoFmt(
                    "Available changes %s:",
                    availableToMigrate.Count <= outputLimit
                        ? $"({availableToMigrate.Count})"
                        : $"(showing only first {outputLimit} out of {availableToMigrate.Count})");
                var table = new TablePrinter("Date", "Revision", "Description", "Author");
                foreach (var change in availableToMigrate.Take(outputLimit))
                {
                    outputSize++;
                    table.AddRow(
                        change.GetDateTime().ToString(DateFormat),
                        Truncate(change.GetRevision().AsString(), RevisionMaxLength, ""),
                        Truncate(change.FirstLineMessage(), DescriptionMaxLength, "..."),
                        Truncate(change.GetAuthor().ToString(), AuthorMaxLength, "..."));
                }
                foreach (string line in table.Build())
                {
                    console.Info(line);
                }
            }
            if (outputSize > 100)
            {
                console.InfoFmt(
                    "Use %s to limit the output of the command.", GeneralOptions.OutputLimitFlag);
            }
        }

        // TODO(port): dispatch InfoFinishedEvent via eventMonitors() once the monitor package is
        // ported.
        return hasAvailableChanges;
    }

    private static Info<IRevision> GetInfo(string migrationName, Config.Config config) =>
        config.GetMigration(migrationName).GetInfo();

    /// <summary>Truncates <paramref name="value"/> to <paramref name="maxLength"/>, appending the truncation
    /// indicator (equivalent to Guava's Ascii.truncate).</summary>
    private static string Truncate(string value, int maxLength, string indicator)
    {
        if (value.Length <= maxLength)
        {
            return value;
        }
        int truncationLength = maxLength - indicator.Length;
        return value.Substring(0, Math.Max(0, truncationLength)) + indicator;
    }

    public string Name => "info";
}
