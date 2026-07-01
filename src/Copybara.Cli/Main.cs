/*
 * Copyright (C) 2016 Google LLC
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
using Copybara.Exceptions;
using Copybara.Profiler;
using Copybara.Util;
using Copybara.Util.Console;
using Microsoft.Extensions.Logging;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Cli;

/// <summary>
/// Main class that invokes Copybara from the command line.
///
/// <para>This class should only know about how to validate and parse command-line arguments in order
/// to invoke Copybara.</para>
/// </summary>
public class Main
{
    public const string BuildLabel = "Build label";

    // These flags are read before the arg parser is initialized, because of the console lifecycle.
    // They mirror the (internal) constants on GeneralOptions.
    private const string ConsoleFilePathFlag = "--console-file-path";
    private const string ConsoleFileFlushIntervalFlag = "--console-file-flush-interval";
    private static readonly TimeSpan DefaultConsoleFileFlushInterval = TimeSpan.FromSeconds(30);

    /// <summary>
    /// Commands whose config-file arguments should be parsed, mapping to whether they consume a
    /// source ref.
    /// </summary>
    private static readonly ImmutableDictionary<string, bool>
        CommandNamesThatUseConfigFilesToUseSourceRef =
            new Dictionary<string, bool>
            {
                ["migrate"] = true,
                ["info"] = false,
                ["validate"] = false,
            }.ToImmutableDictionary();

    /// <summary>The environment, typically the process environment variables. Injected for tests.</summary>
    protected readonly IReadOnlyDictionary<string, string> Environment;

    protected Profiler.Profiler? Profiler;
    protected ArgParser? ArgParser;

    private Console? _console;

    public Main()
        : this(GetSystemEnvironment())
    {
    }

    public Main(IReadOnlyDictionary<string, string> environment)
    {
        Environment = Preconditions.CheckNotNull(environment);
    }

    public static int Main_(string[] args) => (int)new Main().Run(args);

    public ExitCode Run(string[] args)
    {
        // We need a console before parsing the args because it could fail with wrong arguments and
        // we need to show the error.
        _console = GetConsole(args);
        Console console = _console;

        console.StartupMessage(GetVersion());
        console.VerboseFmt("Running: %s", string.Join(' ', args));

        CommandResult result = RunInternal(args, console);
        try
        {
            Shutdown(result);
        }
        catch (Exception e)
        {
            HandleUnexpectedError(console, "Execution was interrupted.", args, e);
        }

        return result.ExitCode;
    }

    /// <summary>Finds out about verbose output before the arg parser has been initialized.</summary>
    protected static bool IsVerbose(string[] args) =>
        args.Any(s => s == "-v" || s == "--verbose");

    /// <summary>Finds out if logging is enabled before the arg parser has been initialized.</summary>
    protected static bool IsEnableLogging(string[] args) => !args.Contains("--nologging");

    /// <summary>
    /// Finds a flag value before the arg parser is initialized. Returns null if the flag is not
    /// present or has no value ('=' and ' ' accepted as separators). Does not support arity 0 flags.
    /// </summary>
    protected static string? FindFlagValue(string[] args, string flagName)
    {
        for (int index = 0; index < args.Length; index++)
        {
            if (args[index] == flagName)
            {
                if (index < args.Length - 1 && !args[index + 1].StartsWith('-'))
                {
                    return args[index + 1];
                }
                return null;
            }
            if (args[index].StartsWith(flagName + "=", StringComparison.Ordinal))
            {
                return args[index].Substring(flagName.Length + 1);
            }
        }
        return null;
    }

    /// <summary>The exit code and the command executed.</summary>
    protected sealed record CommandResult(
        ExitCode ExitCode, ICopybaraCmd? Command, CommandEnv? CommandEnv);

    /// <summary>
    /// Runs the command and returns the <see cref="ExitCode"/>. Also responsible for the exception
    /// handling/logging.
    /// </summary>
    private CommandResult RunInternal(string[] args, Console console)
    {
        CommandEnv? commandEnv = null;
        ICopybaraCmd? subcommand = null;

        try
        {
            ModuleSet moduleSet = NewModuleSet(Environment, console);

            var mainArgs = new MainArguments(args);
            Options options = moduleSet.GetOptions();

            ArgParser = new ArgParser();
            ArgParser.AddObjects(options.GetAll().Cast<object>());
            ArgParser.AddObject(mainArgs);
            mainArgs.Unnamed = ArgParser.Parse(args).ToList();

            string version = GetVersion();

            IConfigLoaderProvider configLoaderProvider = NewConfigLoaderProvider(moduleSet);

            ImmutableDictionary<string, ICopybaraCmd> commands =
                GetCommands(moduleSet, configLoaderProvider)
                    .ToImmutableDictionary(c => c.Name, c => c);

            MainArguments.CommandWithArgs cmdToRun =
                mainArgs.ParseCommand(commands, commands["migrate"]);
            subcommand = cmdToRun.Subcommand;

            WarnAboutPossibleFlags(cmdToRun, console);

            InitEnvironment(options, cmdToRun.Subcommand, args);

            GeneralOptions generalOptions = options.Get<GeneralOptions>();
            string baseWorkdir = mainArgs.GetBaseWorkdir(generalOptions, generalOptions.GetFileSystem());

            commandEnv = new CommandEnv(baseWorkdir, options, cmdToRun.Args, mainArgs);
            if (CommandNamesThatUseConfigFilesToUseSourceRef.TryGetValue(subcommand.Name, out bool useSourceRef))
            {
                commandEnv.ParseConfigFileArgs(subcommand, useSourceRef);
            }

            console.VerboseFmt("Current working directory: %s", generalOptions.GetCwd());
            generalOptions.GetConsole().ProgressFmt("Running %s", subcommand.Name);

            ExitCode exitCode = subcommand.Run(commandEnv);
            return new CommandResult(exitCode, subcommand, commandEnv);
        }
        catch (CommandLineException e)
        {
            Consoles.PrintCauseChain(LogLevel.Warning, console, args, e);
            console.Error("Try 'copybara help'.");
            return new CommandResult(ExitCode.CommandLineError, subcommand, commandEnv);
        }
        catch (EmptyChangeException e)
        {
            // This is not necessarily an error. Maybe the tool was run previously and there are no
            // new changes to import. (EmptyChangeException derives from ValidationException, so this
            // must be caught before ValidationException.)
            console.Warn(e.Message);
            return new CommandResult(ExitCode.NoOp, subcommand, commandEnv);
        }
        catch (ValidationException e)
        {
            Consoles.PrintCauseChain(LogLevel.Warning, console, args, e);
            return new CommandResult(ExitCode.ConfigurationError, subcommand, commandEnv);
        }
        catch (RepoException e)
        {
            Consoles.PrintCauseChain(LogLevel.Error, console, args, e);
            if (e.InnerException is OperationCanceledException)
            {
                return new CommandResult(ExitCode.Interrupted, subcommand, commandEnv);
            }
            return new CommandResult(ExitCode.RepositoryError, subcommand, commandEnv);
        }
        catch (IOException e)
        {
            HandleUnexpectedError(console, e.Message, args, e);
            return new CommandResult(ExitCode.EnvironmentError, subcommand, commandEnv);
        }
        catch (Exception e)
        {
            // This usually indicates a serious programming error that will require Copybara team
            // intervention. Print stack trace without concern for presentation.
            System.Console.Error.WriteLine(e);
            HandleUnexpectedError(console, "Unexpected error: " + e.Message, args, e);
            return new CommandResult(ExitCode.InternalError, subcommand, commandEnv);
        }
    }

    private void WarnAboutPossibleFlags(MainArguments.CommandWithArgs cmdToRun, Console console)
    {
        var possibleFlags = cmdToRun.Args
            .Where(arg => arg.StartsWith("--", StringComparison.Ordinal))
            .ToImmutableArray();
        if (possibleFlags.IsEmpty)
        {
            return;
        }

        IReadOnlyList<string> allNames = ArgParser?.AllFlagNames ?? Array.Empty<string>();
        foreach (string possibleFlag in possibleFlags)
        {
            var candidates = allNames
                .Where(s => FlagDistance(s, possibleFlag) <= 1)
                .OrderBy(s => s, StringComparer.Ordinal)
                .Distinct()
                .ToImmutableArray();
            if (candidates.IsEmpty)
            {
                console.WarnFmt(
                    "Argument '%s' looks like a flag, but was not parsed as one, is this"
                        + " intentional?",
                    possibleFlag);
            }
            else
            {
                console.WarnFmt(
                    "Argument '%s' looks like a flag, but was not parsed as one, did you mean one"
                        + " of %s?",
                    possibleFlag, "[" + string.Join(", ", candidates) + "]");
            }
        }
    }

    /// <summary>Naive algorithm to propose similar flags (dropped pre-/suffixes).</summary>
    private static int FlagDistance(string flag, string input)
    {
        var flagSet = flag.Split('_', '-').Select(s => s.ToLowerInvariant()).ToHashSet();
        var inputSet = input.Split('_', '-').Select(s => s.ToLowerInvariant()).ToHashSet();
        return inputSet.Count - inputSet.Intersect(flagSet).Count();
    }

    public IReadOnlyList<ICopybaraCmd> GetCommands(
        ModuleSet moduleSet, IConfigLoaderProvider configLoaderProvider)
    {
        ConfigValidator validator = GetConfigValidator(moduleSet.GetOptions());
        Action<IMigration> consumer = GetMigrationRanConsumer();
        // TODO(port): OnboardCmd, GeneratorCmd, RegenerateCmd are not ported yet and are omitted.
        return new ICopybaraCmd[]
        {
            new MigrateCmd(validator, consumer, configLoaderProvider, moduleSet),
            new InfoCmd(configLoaderProvider, NewInfoContextProvider()),
            new ValidateCmd(validator, consumer, configLoaderProvider),
            new HelpCmd(this),
            new VersionCmd(this),
        };
    }

    /// <summary>Returns a short string representing the version of the binary.</summary>
    protected virtual string GetVersion()
    {
        var buildInfo = GetBuildInfo();
        return buildInfo.TryGetValue(BuildLabel, out var label) ? label : "Unknown version";
    }

    private static ImmutableDictionary<string, string> GetBuildInfo()
    {
        // TODO(port): upstream loads /build-data.properties from resources. Not wired up yet.
        return ImmutableDictionary<string, string>.Empty;
    }

    /// <summary>Returns a string describing who and when the binary was built.</summary>
    protected virtual string GetBinaryInfo() =>
        string.Join("\n", GetBuildInfo().Select(kv => $"{kv.Key}: {kv.Value}"));

    protected virtual Action<IMigration> GetMigrationRanConsumer() => _ => { };

    protected virtual ConfigValidator GetConfigValidator(Options options) =>
        new DefaultConfigValidator();

    private sealed class DefaultConfigValidator : ConfigValidator
    {
    }

    /// <summary>Returns a new module set.</summary>
    protected virtual ModuleSet NewModuleSet(
        IReadOnlyDictionary<string, string> environment, Console console)
    {
        string fsRoot = Path.GetPathRoot(Directory.GetCurrentDirectory()) ?? "/";
        return new ModuleSupplier(environment, fsRoot, console).Create();
    }

    protected virtual IConfigLoaderProvider NewConfigLoaderProvider(ModuleSet moduleSet)
    {
        GeneralOptions generalOptions = moduleSet.GetOptions().Get<GeneralOptions>();
        return new FuncConfigLoaderProvider((configPath, sourceRef) =>
            new ConfigLoader(
                moduleSet,
                CreateConfigFileWithHeuristic(
                    ValidateLocalConfig(generalOptions, configPath),
                    generalOptions.GetConfigRoot()),
                generalOptions.GetStarlarkMode()));
    }

    protected virtual IContextProvider NewInfoContextProvider() => new InfoContextProvider();

    private sealed class InfoContextProvider : IContextProvider
    {
        public IReadOnlyDictionary<string, string> GetContext(
            Copybara.Config.Config config,
            ConfigFileArgs configFileArgs,
            IConfigLoaderProvider configLoaderProvider,
            Console console) =>
            new Dictionary<string, string> { ["copybara_config"] = config.GetLocation() };
    }

    /// <summary>
    /// Validates that the passed config file is correct (exists, right filename, etc.) and returns
    /// its absolute path.
    /// </summary>
    /// <exception cref="ValidationException"/>
    /// <exception cref="CommandLineException"/>
    protected virtual string ValidateLocalConfig(GeneralOptions generalOptions, string configLocation)
    {
        string configPath = Path.GetFullPath(configLocation);
        string? fileName = Path.GetFileName(configPath);
        ValidationException.CheckCondition(
            !string.IsNullOrEmpty(fileName),
            "The configuration path '{0}' is not a file.",
            configPath);
        ValidationException.CheckCondition(
            fileName == MainArguments.CopybaraSkylarkConfigFilename,
            "Copybara config file filename should be '{0}' but it is '{1}'.",
            MainArguments.CopybaraSkylarkConfigFilename,
            fileName!);

        if (!File.Exists(configPath))
        {
            throw new CommandLineException("Configuration file not found: " + configPath);
        }
        return configPath;
    }

    /// <summary>
    /// Finds the root path for resolving configuration file paths. Assumes that the .git-containing
    /// directory is the root path.
    /// </summary>
    protected virtual PathBasedConfigFile CreateConfigFileWithHeuristic(
        string configPath, string? commandLineRoot)
    {
        if (commandLineRoot != null)
        {
            return new PathBasedConfigFile(configPath, commandLineRoot, identifierPrefix: null);
        }
        string? parent = Path.GetDirectoryName(configPath);
        while (parent != null)
        {
            if (Directory.Exists(Path.Combine(parent, ".git")))
            {
                return new PathBasedConfigFile(configPath, parent, identifierPrefix: null);
            }
            parent = Path.GetDirectoryName(parent);
        }
        return new PathBasedConfigFile(configPath, rootPath: null, identifierPrefix: null);
    }

    protected virtual Console GetConsole(string[] args)
    {
        bool verbose = IsVerbose(args);
        Console console;
        if (System.Console.IsOutputRedirected || System.Console.IsInputRedirected)
        {
            console = LogConsole.WriteOnlyConsole(System.Console.Error, verbose);
        }
        else if (args.Contains(GeneralOptions.Noansi))
        {
            console = LogConsole.ReadWriteConsole(System.Console.In, System.Console.Error, verbose);
        }
        else
        {
            console = new AnsiConsole(System.Console.In, System.Console.Error, verbose);
        }

        string? noPrompt = FindFlagValue(args, GeneralOptions.Noprompt);
        if (noPrompt == "true")
        {
            console = new NoPromptConsole(console, true);
        }

        string? maybeConsoleFilePath = FindFlagValue(args, ConsoleFilePathFlag);
        if (maybeConsoleFilePath == null)
        {
            return console;
        }

        try
        {
            string? dir = Path.GetDirectoryName(maybeConsoleFilePath);
            if (!string.IsNullOrEmpty(dir))
            {
                Directory.CreateDirectory(dir);
            }
        }
        catch (IOException)
        {
            // Could not create parent directories; disable redirecting.
            return console;
        }
        return new FileConsole(console, maybeConsoleFilePath, GetConsoleFlushRate(args));
    }

    /// <summary>Returns the console flush rate from the flag, if valid, or the default otherwise.</summary>
    protected virtual TimeSpan GetConsoleFlushRate(string[] args)
    {
        string? value = FindFlagValue(args, ConsoleFileFlushIntervalFlag);
        return value != null ? DurationConverter.Convert(value)
            : DefaultConsoleFileFlushInterval;
    }

    /// <summary>
    /// Hook to allow setting variables that are not run/validation specific, based on options. Called
    /// after command-line options are parsed but before a file is read or a run started.
    /// </summary>
    protected virtual void InitEnvironment(Options options, ICopybaraCmd copybaraCmd, string[] rawArgs)
    {
        GeneralOptions generalOptions = options.Get<GeneralOptions>();
        Profiler = generalOptions.Profiler();
        var profilerListeners = new List<IListener>
        {
            new LogProfilerListener(),
            new ConsoleProfilerListener(generalOptions.GetConsole()),
        };
        Profiler.Init(profilerListeners);
        CleanupOutputDir(generalOptions);
    }

    protected virtual void CleanupOutputDir(GeneralOptions generalOptions)
    {
        generalOptions.IoRepoTask<object?>(
            "clean_outputdir",
            () =>
            {
                if (generalOptions.IsNoCleanup())
                {
                    return null;
                }
                generalOptions.GetConsole().Progress("Cleaning output directory");
                generalOptions.GetDirFactory().CleanupTempDirs();
                return null;
            });
    }

    /// <summary>Performs cleanup tasks after executing Copybara.</summary>
    protected virtual void Shutdown(CommandResult result)
    {
        if (_console != null)
        {
            _console.Dispose();
        }
        if (Profiler != null)
        {
            Profiler.Stop();
        }
    }

    protected virtual void HandleUnexpectedError(
        Console console, string msg, string[] args, Exception e)
    {
        console.Error(msg + " (" + e + ")");
    }

    internal string Usage()
    {
        var sb = new System.Text.StringBuilder();
        sb.Append("Copybara version: ").Append(GetVersion()).Append('\n');
        sb.Append("Usage: copybara [subcommand] ").Append(MainArguments.CopybaraSkylarkConfigFilename)
            .Append(" [migration_name [source_ref]]\n\n");
        sb.Append("Available subcommands: migrate, info, validate, version, help\n\n");
        if (ArgParser != null)
        {
            sb.Append("Flags:\n");
            foreach (var (names, description) in ArgParser.Descriptions.OrderBy(d => d.Names, StringComparer.Ordinal))
            {
                sb.Append("  ").Append(names).Append("\n      ").Append(description).Append('\n');
            }
        }
        sb.Append("\nExample:\n  copybara ").Append(MainArguments.CopybaraSkylarkConfigFilename)
            .Append(" origin/main\n");
        return sb.ToString();
    }

    private static IReadOnlyDictionary<string, string> GetSystemEnvironment()
    {
        var dict = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (System.Collections.DictionaryEntry e in System.Environment.GetEnvironmentVariables())
        {
            dict[(string)e.Key] = (string?)e.Value ?? "";
        }
        return dict;
    }

    /// <summary>Prints the Copybara version.</summary>
    private sealed class VersionCmd : ICopybaraCmd
    {
        private readonly Main _main;

        public VersionCmd(Main main) => _main = main;

        public ExitCode Run(CommandEnv commandEnv)
        {
            commandEnv.GetOptions().Get<GeneralOptions>().GetConsole().Info(_main.GetBinaryInfo());
            return ExitCode.Success;
        }

        public string Name => "version";
    }

    /// <summary>Prints the help message.</summary>
    private sealed class HelpCmd : ICopybaraCmd
    {
        private readonly Main _main;

        public HelpCmd(Main main) => _main = Preconditions.CheckNotNull(main);

        public ExitCode Run(CommandEnv commandEnv)
        {
            commandEnv.GetOptions().Get<GeneralOptions>().GetConsole().Info(_main.Usage());
            return ExitCode.Success;
        }

        public string Name => "help";
    }
}
