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
using Copybara.Exceptions;
using Copybara.Util;
using Copybara.Util.Console;

// Domain 'Console' collides with System.Console; qualify both.
using Console = Copybara.Util.Console.Console;

namespace Copybara;

/// <summary>General options available for all the program classes.</summary>
public sealed class GeneralOptions : IOption
{
    public const string CliFlagPrefix = "FLAG_";

    public const string Noansi = "--noansi";
    public const string Noprompt = "--noprompt";
    public const string Force = "--force";
    public const string ConfigRootFlag = "--config-root";
    public const string OutputRootFlag = "--output-root";
    public const string OutputLimitFlag = "--output-limit";
    public const string DryRunFlag = "--dry-run";
    public const string SquashFlag = "--squash";
    public const string PatchBinFlag = "--patch-bin";
    internal const string ConsoleFilePath = "--console-file-path";
    internal const string ConsoleFileFlushInterval = "--console-file-flush-interval";

    internal static readonly TimeSpan DefaultConsoleFileFlushInterval = TimeSpan.FromSeconds(30);
    private const string DefaultMonitor = "default";

    private IReadOnlyDictionary<string, string> _environment;
    private string _fileSystemRoot;
    private Console _console;

    // Registry of available event monitors, keyed by name. Populated lazily on first access because
    // the default monitor needs the console. Mirrors Java's HashMap<String, EventMonitor>.
    private Dictionary<string, Copybara.Monitor.IEventMonitor>? _eventMonitors;

    private string? _configRootPath;
    private string? _outputRootPath;

    private Copybara.Profiler.Profiler _profiler = new(Copybara.Profiler.Ticker.SystemTicker);

    public GeneralOptions(
        IReadOnlyDictionary<string, string> environment,
        string fileSystemRoot,
        Console console)
    {
        _environment = environment;
        _fileSystemRoot = Preconditions.CheckNotNull(fileSystemRoot);
        _console = Preconditions.CheckNotNull(console);
    }

    public GeneralOptions(
        IReadOnlyDictionary<string, string> environment,
        string fileSystemRoot,
        bool verbose,
        Console console,
        string? configRoot,
        string? outputRoot,
        bool noCleanup,
        bool disableReversibleCheck,
        bool force,
        int outputLimit)
        : this(environment, fileSystemRoot, console)
    {
        Verbose = verbose;
        _configRootPath = configRoot;
        _outputRootPath = outputRoot;
        NoCleanup = noCleanup;
        DisableReversibleCheck = disableReversibleCheck;
        ForceFlag = force;
        OutputLimitValue = outputLimit;
    }

    public GeneralOptions WithForce(bool force)
    {
        return new GeneralOptions(
            _environment, _fileSystemRoot, Verbose, _console, GetConfigRoot(),
            GetOutputRoot(), NoCleanup, DisableReversibleCheck, force, OutputLimitValue);
    }

    public GeneralOptions WithConsole(Console console)
    {
        return new GeneralOptions(
            _environment, _fileSystemRoot, Verbose, console, GetConfigRoot(),
            GetOutputRoot(), NoCleanup, DisableReversibleCheck, ForceFlag, OutputLimitValue);
    }

    public IReadOnlyDictionary<string, string> GetEnvironment() => _environment;

    public bool IsVerbose() => Verbose;

    public Console GetConsole() => _console;

    /// <summary>The root of the filesystem used to resolve paths (analog of Java's FileSystem).</summary>
    public string GetFileSystem() => _fileSystemRoot;

    public bool IsNoCleanup() => NoCleanup;

    public bool IsDisableReversibleCheck() => DisableReversibleCheck;

    public bool IsForced() => ForceFlag;

    public bool IsVersionSelectorUseCliRef() => VersionSelectorUseCliRef;

    /// <summary>Returns current working directory.</summary>
    public string GetCwd()
    {
        return _environment.TryGetValue("PWD", out var pwd)
            ? pwd
            : Directory.GetCurrentDirectory();
    }

    /// <summary>Returns the root absolute path to use for config, or null if not set.</summary>
    public string? GetConfigRoot()
    {
        if (_configRootPath == null && ConfigRoot != null)
        {
            _configRootPath = Path.GetFullPath(ConfigRoot);
            ValidationException.CheckCondition(
                Directory.Exists(_configRootPath) || File.Exists(_configRootPath),
                "%s doesn't exist", ConfigRoot);
            ValidationException.CheckCondition(
                Directory.Exists(_configRootPath), "%s isn't a directory", ConfigRoot);
        }

        return _configRootPath;
    }

    /// <summary>
    /// Returns the output root directory, or null if not set.
    /// <para>This method is exposed mainly for tests; prefer <see cref="GetDirFactory"/>.</para>
    /// </summary>
    public string? GetOutputRoot()
    {
        if (_outputRootPath == null && OutputRoot != null)
        {
            _outputRootPath = OutputRoot;
        }

        return _outputRootPath;
    }

    /// <summary>
    /// Returns the output limit.
    /// <para>Each subcommand can use this value differently.</para>
    /// </summary>
    public int GetOutputLimit() => OutputLimitValue > 0 ? OutputLimitValue : int.MaxValue;

    public Copybara.Profiler.Profiler Profiler() => _profiler;

    private Dictionary<string, Copybara.Monitor.IEventMonitor> EventMonitorRegistry()
    {
        // Default registry contains the "default" monitor: a ConsoleEventMonitor wrapping the empty
        // monitor. Mirrors GeneralOptions' constructor in Java.
        return _eventMonitors ??= new Dictionary<string, Copybara.Monitor.IEventMonitor>
        {
            [DefaultMonitor] =
                new Copybara.Monitor.ConsoleEventMonitor(
                    _console, Copybara.Monitor.IEventMonitor.EmptyMonitor),
        };
    }

    /// <summary>
    /// Returns the configured (enabled) event monitors. Mirrors Java's <c>eventMonitors()</c>: it
    /// filters the available-monitor registry down to those named in <see cref="EnabledEventMonitors"/>.
    /// </summary>
    public Copybara.Monitor.IEventMonitor.EventMonitors EventMonitors()
    {
        var registry = EventMonitorRegistry();
        var result = new List<Copybara.Monitor.IEventMonitor>();
        foreach (var name in EnabledEventMonitors)
        {
            if (registry.TryGetValue(name, out var monitor))
            {
                result.Add(monitor);
            }
        }

        return new Copybara.Monitor.IEventMonitor.EventMonitors(result);
    }

    /// <summary>
    /// Adds an EventMonitor to the list of available monitors without activating it. Use this to make
    /// a monitor available for later enabling.
    /// </summary>
    public GeneralOptions AddEventMonitor(string name, Copybara.Monitor.IEventMonitor eventMonitor)
    {
        EventMonitorRegistry()[name] = eventMonitor;
        return this;
    }

    /// <summary>Enables an already-registered EventMonitor by name.</summary>
    public GeneralOptions EnableEventMonitor(string name)
    {
        ValidationException.CheckCondition(
            EventMonitorRegistry().ContainsKey(name), "%s is not a known EventMonitor.", name);
        EnabledEventMonitors.Add(name);
        return this;
    }

    /// <summary>Adds an EventMonitor to the list of available monitors and enables it.</summary>
    public GeneralOptions EnableEventMonitor(string name, Copybara.Monitor.IEventMonitor eventMonitor)
    {
        AddEventMonitor(name, eventMonitor);
        EnableEventMonitor(name);
        return this;
    }

    /// <summary>Clears the enabled event monitors.</summary>
    public GeneralOptions ClearEventMonitor()
    {
        EnabledEventMonitors.Clear();
        return this;
    }

    public IReadOnlyDictionary<string, string> CliLabels() => Labels;

    /// <summary>Run a repository task with profiling.</summary>
    public T RepoTask<T>(string description, Func<T> callable)
    {
        using (_profiler.Start(description))
        {
            return callable();
        }
    }

    /// <summary>Run a repository task that can throw IOException with profiling.</summary>
    public T IoRepoTask<T>(string description, Func<T> callable)
    {
        using (_profiler.Start(description))
        {
            return callable();
        }
    }

    /// <summary>
    /// Returns a <see cref="DirFactory"/> capable of creating directories in a self contained
    /// location in the filesystem.
    /// <para>By default, the directories are created under <c>$HOME/copybara</c>, but it can be
    /// overridden with the flag --output-root.</para>
    /// </summary>
    public DirFactory GetDirFactory()
    {
        var outputRoot = GetOutputRoot();
        if (outputRoot != null)
        {
            return new DirFactory(outputRoot);
        }

        var home = _environment.TryGetValue("HOME", out var h) ? h : null;
        Preconditions.CheckNotNull(home, "$HOME environment var is not set");
        return new DirFactory(Path.Combine(home!, "copybara"));
    }

    public void SetEnvironmentForTest(IReadOnlyDictionary<string, string> environment)
        => _environment = environment;

    public void SetTemporaryFeaturesForTest(IReadOnlyDictionary<string, string> temporaryFeatures)
        => TemporaryFeatures = temporaryFeatures.ToImmutableDictionary();

    public void SetOutputRootPathForTest(string outputRootPath) => _outputRootPath = outputRootPath;

    public void SetConsoleForTest(Console console) => _console = console;

    public void SetForceForTest(bool force) => ForceFlag = force;

    public void SetVersionSelectorUseCliRefForTest(bool versionSelectorUseCliRef)
        => VersionSelectorUseCliRef = versionSelectorUseCliRef;

    public void SetCliLabelsForTest(ImmutableDictionary<string, string> labels) => Labels = labels;

    public void SetFileSystemForTest(string fileSystemRoot) => _fileSystemRoot = fileSystemRoot;

    public void SetSquashForTest(bool squash) => Squash = squash;

    public GeneralOptions WithProfiler(Copybara.Profiler.Profiler profiler)
    {
        _profiler = Preconditions.CheckNotNull(profiler);
        return this;
    }

    // ---- Flags ----

    [Flag(new[] { "-v", "--verbose" }, "Verbose output.")]
    internal bool Verbose { get; set; }

    [Flag("--repo-timeout", "Repository operation timeout duration.")]
    public TimeSpan RepoTimeout { get; set; } = CommandRunner.DefaultTimeout;

    [Flag("--commands-timeout", "Commands timeout")]
    public TimeSpan CommandsTimeout { get; set; } = CommandRunner.DefaultTimeout;

    public CommandRunner NewCommandRunner(Command cmd) => new(cmd, CommandsTimeout);

    // We don't use JCommander for parsing these flags but we do it manually since
    // the parsing could fail and we need to report errors using one console.
    [Flag(Noansi, "Don't use ANSI output for messages")]
    internal bool Noansi_ { get; set; }

    [Flag(Noprompt, "Don't prompt, this will answer all prompts with 'yes'", Arity = 1)]
    internal bool NoPrompt { get; set; }

    [Flag(
        new[] { Force, "--force-update" },
        "Force the migration even if Copybara cannot find in the destination a change that is an"
            + " ancestor of the one(s) being migrated. This should be used with care, as it"
            + " could lose changes when migrating a previous/conflicting change.")]
    internal bool ForceFlag { get; set; }

    [Flag(
        "--version-selector-use-cli-ref",
        "If command line ref is to used with a version selector, pass this flag to tell copybara"
            + " to use it.",
        Arity = 1)]
    internal bool VersionSelectorUseCliRef { get; set; } = true;

    [Flag(
        ConfigRootFlag,
        "Configuration root path to be used for resolving absolute config labels"
            + " like '//foo/bar'")]
    internal string? ConfigRoot { get; set; }

    [Flag(
        "--disable-reversible-check",
        "If set, all workflows will be executed without reversible_check, overriding"
            + " the  workflow config and the normal behavior for CHANGE_REQUEST mode.")]
    internal bool DisableReversibleCheck { get; set; }

    [Flag(
        OutputRootFlag,
        "The root directory where to generate output files. If not set, ~/copybara/out is used "
            + "by default. Use with care, Copybara might remove files inside this root if "
            + "necessary.")]
    internal string? OutputRoot { get; set; }

    [Flag(
        OutputLimitFlag,
        "Limit the output in the console to a number of records. Each subcommand might use this "
            + "flag differently. Defaults to 0, which shows all the output.")]
    internal int OutputLimitValue { get; set; }

    [Flag(
        "--nocleanup",
        "Cleanup the output directories. This includes the workdir, scratch clones of Git"
            + " repos, etc. By default is set to false and directories will be cleaned prior to"
            + " the execution. If set to true, the previous run output will not be cleaned up."
            + " Keep in mind that running in this mode will lead to an ever increasing disk"
            + " usage.")]
    internal bool NoCleanup { get; set; }

    [Flag(
        "--nologging",
        "Disable logging of this binary. Note that commands executed by Copybara "
            + "might still log to their own file.",
        Hidden = true)]
    internal bool NoLogging { get; set; }

    [Flag(
        "--labels",
        "Additional flags. Can be accessed in feedback and mirror context objects via the"
            + " `cli_labels` field. In `core.workflow`, they are accessible as labels, but with"
            + " names uppercased and prefixed with "
            + CliFlagPrefix
            + " to avoid name clashes with existing labels. I.e. `--labels=label1:value1` will"
            + " define a label FLAG_LABEL1Format: --labels=flag1:value1,flag2:value2 Or: --labels"
            + " flag1:value1,flag2:value2 ")]
    internal ImmutableDictionary<string, string> Labels { get; set; } =
        ImmutableDictionary<string, string>.Empty;

    [Flag(
        "--temporary-features",
        "Change guarded features. If set it means that it will return true.",
        Hidden = true)]
    private ImmutableDictionary<string, string> TemporaryFeatures { get; set; } =
        ImmutableDictionary<string, string>.Empty;

    [Flag(
        "--diff-bin",
        "Command line diff tool bin used in merge import. Defaults to diff3, but users can pass"
            + " in their own diffing tools (along with requisite arg reordering)")]
    private string DiffBin { get; set; } = "diff3";

    public string GetDiffBin() => DiffBin;

    [Flag(PatchBinFlag, "Path for GNU Patch command")]
    public string PatchBin { get; set; } = "patch";

    /// <summary>
    /// Temporary features is meant to be used by Copybara team for guarding new codepaths. Should
    /// never be used for user facing flags or longer term experiments. Any caller of this function
    /// should have a todo saying when to remove the call.
    /// <para>If the flag doesn't have a value it will use defaultVal. If the flag is incorrect
    /// (different from true/false) it will use defaultVal (and log at severe).</para>
    /// </summary>
    public bool IsTemporaryFeature(string name, bool defaultVal)
    {
        Preconditions.CheckNotNull(name);
        if (!TemporaryFeatures.TryGetValue(name, out var v))
        {
            return defaultVal;
        }

        if (string.Equals(v, "true", StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        if (string.Equals(v, "false", StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        // logger.atSevere(): Invalid boolean value. Using default.
        return defaultVal;
    }

    // This flag is read before we parse the arguments, because of the console lifecycle.
    [Flag(
        ConsoleFilePath,
        "If set, write the console output also to the given file path.")]
    internal string? ConsoleFilePathValue { get; set; }

    // This flag is read before we parse the arguments, because of the console lifecycle.
    [Obsolete]
    [Flag(
        "--console-file-flush-rate",
        "How often in number of lines to flush the console to the output file. "
            + "If set to 0, console will be flushed only at the end.",
        Hidden = true)]
    internal int ConsoleFileFlushRateDeprecatedDontUse { get; set; } = -1;

    // This flag is read before we parse the arguments, because of the console lifecycle.
    [Flag(
        ConsoleFileFlushInterval,
        "How often Copybara should flush the console to the output file. (10s, 1m, etc.)"
            + "If set to 0s, console will be flushed only at the end.")]
    internal TimeSpan ConsoleFileFlushIntervalValue { get; set; } = DefaultConsoleFileFlushInterval;

    [Flag(
        DryRunFlag,
        "Run the migration in dry-run mode. Some destination implementations might"
            + " have some side effects (like creating a code review), but never submit to a main"
            + " branch.")]
    public bool DryRunMode { get; set; }

    [Flag(
        SquashFlag,
        "Override workflow's mode with 'SQUASH'. This is "
            + "useful mainly for workflows that use 'ITERATIVE' mode, when we want to run a single "
            + "export with 'SQUASH', maybe to fix an issue. Always use " + DryRunFlag + " before, to "
            + "test your changes locally.")]
    public bool Squash { get; set; }

    [Flag(
        "--validate-starlark",
        "Starlark should be validated prior to execution, but this might break legacy configs."
            + " Options are LOOSE, STRICT")]
    public string StarlarkModeFlag { get; set; } = StarlarkMode.Loose.ToString().ToUpperInvariant();

    public StarlarkMode GetStarlarkMode() =>
        Enum.Parse<StarlarkMode>(StarlarkModeFlag, ignoreCase: true);

    [Flag(
        "--info-list-only",
        "When set, the INFO command will print a list of workflows defined in the file.")]
    public bool InfoListOnly { get; set; }

    [Flag(
        "--info-include-definition",
        "When set, the INFO command will include the migrations' definition stack info in the"
            + " table or list output. In table, leaves out origin, destination and mode.")]
    public bool InfoIncludeDefinition { get; set; }

    [Flag(
        "--event-monitor",
        "Eventmonitors to enable. These must be in the list of available monitors.")]
    public List<string> EnabledEventMonitors { get; set; } = new() { DefaultMonitor };

    [Flag(
        "--allow-empty-diff",
        "If set to false, Copybara will not write to the destination if the exact same change is"
            + " already pending in the destination. Currently only supported for"
            + " `git.github_pr_destination` and `git.gerrit_destination`.",
        Arity = 1)]
    public bool? AllowEmptyDiff { get; set; }

    public bool AllowEmptyDiffValue(bool configAllowEmptyDiff)
        => AllowEmptyDiff ?? configAllowEmptyDiff;
}
