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
using System.Reflection;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Util.Console;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using Starlark.Annot;
using Starlark.Eval;
using Starlark.Syntax;
using Console = Copybara.Util.Console.Console;
using StarlarkRt = Starlark.Eval.Starlark;
using Module = Starlark.Eval.Module;
using Tuple = Starlark.Eval.Tuple;
using FileOptions = Starlark.Syntax.FileOptions;

namespace Copybara.Config;

/// <summary>Loads Copybara configs out of Skylark files.</summary>
public class SkylarkParser
{
    private static readonly ILogger Logger = NullLogger.Instance;
    private const string DefaultExtension = ".bara.sky";

    private static readonly ImmutableHashSet<string> AllowedLoadExtensions =
        ImmutableHashSet.Create(DefaultExtension, ".scl");

    private static readonly object VisibilityFunc = new VisibilityFunction();

    // For now all the modules are namespaces. We don't use variables except for 'core'.
    private readonly IReadOnlyList<Type> _modules;
    private readonly StarlarkMode _validation;

    public SkylarkParser(IReadOnlySet<Type> staticModules, StarlarkMode validation)
    {
        var builder = ImmutableArray.CreateBuilder<Type>();
        builder.Add(typeof(GlobalMigrations));
        builder.AddRange(staticModules);
        _modules = builder.ToImmutable();
        _validation = validation;
    }

    /// <exception cref="ValidationException"/>
    public Config LoadConfig(ConfigFile config, ModuleSet moduleSet, Console console) =>
        GetConfigWithTransitiveImports(config, moduleSet, console).GetConfig();

    private Config LoadConfigInternal(
        ConfigFile content,
        ModuleSet moduleSet,
        Func<ImmutableDictionary<string, ConfigFile>> configFilesSupplier,
        Console console)
    {
        Module module = new Evaluator(this, moduleSet, content, configFilesSupplier, console)
            .Eval(content);
        GlobalMigrations globalMigrations = GlobalMigrations.GetGlobalMigrations(module);
        return new Config(
            globalMigrations.GetMigrations(), content.Path(), module.GetPredeclaredBindings());
    }

    public Module ExecuteSkylark(ConfigFile content, ModuleSet moduleSet, Console console)
    {
        var capturingConfigFile = new CapturingConfigFile(content);
        var configFilesSupplier = new ConfigFilesSupplier();

        Module module = new Evaluator(this, moduleSet, content, configFilesSupplier.Get, console)
            .Eval(content);
        configFilesSupplier.SetConfigFiles(capturingConfigFile.GetAllLoadedFiles());
        return module;
    }

    /// <summary>
    /// Collect all ConfigFiles retrieved by the parser while loading <paramref name="config"/>.
    /// </summary>
    /// <param name="config">Root file of the configuration.</param>
    /// <param name="moduleSet">the module set providing the Starlark globals.</param>
    /// <param name="console">the console to use for printing error/information.</param>
    /// <returns>A map linking paths to the captured ConfigFiles and the parsed Config.</returns>
    /// <exception cref="ValidationException">If config is invalid, references an invalid file or
    /// contains dependency cycles.</exception>
    public ConfigWithDependencies GetConfigWithTransitiveImports(
        ConfigFile config, ModuleSet moduleSet, Console console)
    {
        var capturingConfigFile = new CapturingConfigFile(config);
        var configFilesSupplier = new ConfigFilesSupplier();

        Config parsedConfig =
            LoadConfigInternal(capturingConfigFile, moduleSet, configFilesSupplier.Get, console);

        ImmutableDictionary<string, ConfigFile> allLoadedFiles =
            capturingConfigFile.GetAllLoadedFiles();

        configFilesSupplier.SetConfigFiles(allLoadedFiles);

        return new ConfigWithDependencies(allLoadedFiles, parsedConfig);
    }

    private sealed class ConfigFilesSupplier
    {
        private ImmutableDictionary<string, ConfigFile>? _configFiles;

        internal void SetConfigFiles(ImmutableDictionary<string, ConfigFile> configFiles)
        {
            Preconditions.CheckState(_configFiles == null, "Already set");
            _configFiles = Preconditions.CheckNotNull(configFiles);
        }

        internal ImmutableDictionary<string, ConfigFile> Get()
        {
            // We need to load all the files before knowing the set of files in the config.
            return Preconditions.CheckNotNull(
                _configFiles, "Don't call the supplier before loading finishes.");
        }
    }

    /// <summary>A class that traverses and evaluates the config file dependency graph.</summary>
    private sealed class Evaluator
    {
        private readonly SkylarkParser _parser;
        private readonly HashSet<string> _pending = new();
        private readonly List<string> _pendingOrder = new();
        private readonly Dictionary<string, Module> _loaded = new();
        private readonly Console _console;
        private readonly ConfigFile _mainConfigFile;

        // Predeclared environment shared by all files (modules) loaded.
        private readonly ImmutableDictionary<string, object> _environment;
        private readonly ModuleSet _moduleSet;

        internal Evaluator(
            SkylarkParser parser,
            ModuleSet moduleSet,
            ConfigFile mainConfigFile,
            Func<ImmutableDictionary<string, ConfigFile>> configFilesSupplier,
            Console console)
        {
            _parser = parser;
            _console = Preconditions.CheckNotNull(console);
            _mainConfigFile = Preconditions.CheckNotNull(mainConfigFile);
            _moduleSet = Preconditions.CheckNotNull(moduleSet);
            _environment = _parser.CreateEnvironment(_moduleSet, configFilesSupplier);
        }

        internal Module Eval(ConfigFile content)
        {
            if (_pending.Contains(content.Path()))
            {
                throw ThrowCycleError(content.Path());
            }
            if (_loaded.TryGetValue(content.Path(), out Module? existing))
            {
                return existing;
            }
            _pending.Add(content.Path());
            _pendingOrder.Add(content.Path());

            // Make the modules available as predeclared bindings.
            StarlarkSemantics semantics = StarlarkSemantics.DEFAULT;
            Module module = Module.WithPredeclared(semantics, _environment);

            // parse & compile
            ParserInput input = ParserInput.FromUTF8(content.ReadContentBytes(), content.Path());
            FileOptions options =
                FileOptions.DEFAULT.ToBuilder()
                    // Ordinarily, load statements should create file-local variables.
                    // For now, we make them create first-class members of Module.globals.
                    .LoadBindsGlobally(true)
                    .AllowToplevelRebinding(true) // allow e.g. x=1; x=2 at top level
                    .RequireLoadStatementsFirst(_parser._validation == StarlarkMode.Strict)
                    .Build();

            Program prog;
            try
            {
                prog = Program.CompileFile(
                    StarlarkFile.Parse(input, options), StarlarkRt.ModuleAsResolverModule(module));
            }
            catch (SyntaxError.Exception ex)
            {
                foreach (SyntaxError error in ex.Errors)
                {
                    _console.Error(error.ToString());
                }
                throw new ValidationException("Error loading config file.");
            }

            // process loads
            var loadedModules = new Dictionary<string, Module>();
            var fileToLoad = new Dictionary<string, string>();
            foreach (string l in prog.GetLoads().Distinct())
            {
                string key = AllowedLoadExtensions.Any(l.EndsWith) ? l : l + DefaultExtension;
                fileToLoad[key] = l;
            }

            foreach (var entry in
                content
                    // Resolve all in one call so the implementor can do it in batch/parallel.
                    .ResolveAll(fileToLoad.Keys.ToImmutableHashSet()))
            {
                Module loadedModule = Eval(entry.Value);
                loadedModules[fileToLoad[entry.Key]] = loadedModule;
            }

            // execute
            _parser.UpdateEnvironmentForConfigFile(
                StarlarkPrint, content, _mainConfigFile, _environment, _moduleSet);
            using (Mutability mu = Mutability.Create("CopybaraModules"))
            {
                StarlarkThread thread = StarlarkThread.CreateTransient(mu, semantics);
                thread.SetLoader(m => loadedModules.TryGetValue(m, out Module? md) ? md : null);
                thread.SetPrintHandler(StarlarkPrint);
                try
                {
                    StarlarkRt.ExecFileProgram(prog, module, thread);
                }
                catch (EvalException ex)
                {
                    _console.Error(ex.Message);
                    throw new ValidationException("Error loading config file", ex);
                }
            }

            _pending.Remove(content.Path());
            _pendingOrder.Remove(content.Path());
            _loaded[content.Path()] = module;
            return module;
        }

        private void StarlarkPrint(StarlarkThread thread, string msg) =>
            _console.Verbose(thread.GetCallerLocation() + ": " + msg);

        private ValidationException ThrowCycleError(string cycleElement)
        {
            var sb = new System.Text.StringBuilder();
            foreach (string element in _pendingOrder)
            {
                sb.Append(element.Equals(cycleElement) ? "* " : "  ");
                sb.Append(element).Append('\n');
            }
            sb.Append("* ").Append(cycleElement).Append('\n');
            _console.Error("Cycle was detected in the configuration: \n" + sb);
            return new ValidationException("Cycle was detected");
        }
    }

    /// <summary>Updates the module globals with information about the current loaded config file.</summary>
    private void UpdateEnvironmentForConfigFile(
        StarlarkThread.PrintHandler printHandler,
        ConfigFile currentConfigFile,
        ConfigFile mainConfigFile,
        ImmutableDictionary<string, object> environment,
        ModuleSet moduleSet)
    {
        foreach (object module in moduleSet.GetModules().Values)
        {
            // We mutate the module per file loaded. Not ideal but it is the best we can do.
            if (module is ILabelsAwareModule m)
            {
                m.SetConfigFile(mainConfigFile, currentConfigFile);
                m.SetPrintHandler(printHandler);
            }
        }
        foreach (Type module in _modules)
        {
            Logger.LogInformation("Creating variable for {Module}", module.FullName);
            // We mutate the module per file loaded. Not ideal but it is the best we can do.
            if (typeof(ILabelsAwareModule).IsAssignableFrom(module))
            {
                var m = (ILabelsAwareModule)environment[GetModuleName(module)];
                m.SetConfigFile(mainConfigFile, currentConfigFile);
                m.SetPrintHandler(printHandler);
            }
        }
    }

    /// <summary>
    /// Create the environment for all evaluations (will be shared between all the dependent files
    /// loaded).
    /// </summary>
    private ImmutableDictionary<string, object> CreateEnvironment(
        ModuleSet moduleSet, Func<ImmutableDictionary<string, ConfigFile>> configFilesSupplier)
    {
        var env = new Dictionary<string, object>();
        foreach (var module in moduleSet.GetModules())
        {
            Logger.LogInformation("Creating variable for {Module}", module.Key);
            if (module.Value is ILabelsAwareModule lam)
            {
                lam.SetAllConfigResources(configFilesSupplier);
            }
            // Modules shouldn't use the same name
            env[module.Key] = module.Value;
        }

        foreach (Type module in _modules)
        {
            Logger.LogInformation("Creating variable for {Module}", module.FullName);
            // Create the module object and associate it with the functions
            var envBuilder = new Dictionary<string, object>();
            var annot = module.GetCustomAttribute<StarlarkBuiltinAttribute>(inherit: false);
            if (annot != null)
            {
                envBuilder[annot.Name] = Activator.CreateInstance(module)!;
            }
            else if (IsLibrary(module))
            {
                // Reference-forward: upstream registers @Library modules' methods directly. The
                // doc.annotations.Library attribute is not yet ported; handled via IsLibrary probe.
                StarlarkRt.AddMethods(envBuilder, Activator.CreateInstance(module)!);
            }

            foreach (var e in envBuilder)
            {
                env[e.Key] = e.Value;
            }

            // Add the options to the module that require them
            if (typeof(IOptionsAwareModule).IsAssignableFrom(module))
            {
                ((IOptionsAwareModule)env[GetModuleName(module)]).SetOptions(moduleSet.GetOptions());
            }
            if (typeof(ILabelsAwareModule).IsAssignableFrom(module))
            {
                ((ILabelsAwareModule)env[GetModuleName(module)])
                    .SetAllConfigResources(configFilesSupplier);
            }
        }
        env["visibility"] = VisibilityFunc;
        return env.ToImmutableDictionary();
    }

    private static string GetModuleName(Type cls) =>
        cls.GetCustomAttribute<StarlarkBuiltinAttribute>(inherit: false)!.Name;

    // Probes for a doc.annotations.Library-style attribute by name so the port keeps working once
    // that annotation lands, without a hard dependency on it here.
    private static bool IsLibrary(Type module) =>
        module.GetCustomAttributes(inherit: false)
            .Any(a => a.GetType().Name is "LibraryAttribute" or "Library");

    private sealed class VisibilityFunction : IStarlarkCallable
    {
        public object Call(StarlarkThread thread, Tuple args, Dict kwargs) => StarlarkRt.None;

        public object? Fastcall(StarlarkThread thread, object?[] positional, object?[] named) =>
            StarlarkRt.None;

        public string Name => "visibility";

        public void Repr(Printer printer, StarlarkSemantics semantics) =>
            printer.Append("<built-in function visibility>");

        public Location Location => Location.BUILTIN;
    }
}
