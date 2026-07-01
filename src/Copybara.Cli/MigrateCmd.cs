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

using Copybara;
using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Cli;

/// <summary>
/// Executes the migration for the given config.
/// </summary>
public sealed class MigrateCmd : ICopybaraCmd
{
    private readonly ConfigValidator _configValidator;
    private readonly Action<IMigration> _migrationRanConsumer;
    private readonly IConfigLoaderProvider _configLoaderProvider;
    private readonly ModuleSet _moduleSet;

    public MigrateCmd(
        ConfigValidator configValidator,
        Action<IMigration> migrationRanConsumer,
        IConfigLoaderProvider configLoaderProvider,
        ModuleSet moduleSet)
    {
        _configValidator = Preconditions.CheckNotNull(configValidator);
        _migrationRanConsumer = Preconditions.CheckNotNull(migrationRanConsumer);
        _configLoaderProvider = Preconditions.CheckNotNull(configLoaderProvider);
        _moduleSet = moduleSet;
    }

    public ExitCode Run(CommandEnv commandEnv)
    {
        ConfigFileArgs configFileArgs = commandEnv.GetConfigFileArgs()!;
        IReadOnlyList<string> sourceRefs = configFileArgs.GetSourceRefs();
        string workflowName = configFileArgs.GetWorkflowName();
        UpdateEnvironment(workflowName);
        GeneralOptions generalOptions = commandEnv.GetOptions().Get<GeneralOptions>();
        Console console = generalOptions.GetConsole();
        console.VerboseFmt("Executing workflow '%s'", workflowName);
        Run(
            commandEnv.GetOptions(),
            _configLoaderProvider.NewLoader(
                configFileArgs.GetConfigPath(),
                sourceRefs.Count == 1 ? sourceRefs[0] : null),
            workflowName,
            commandEnv.GetWorkdir(),
            sourceRefs);
        return ExitCode.Success;
    }

    /// <summary>Runs the migration specified by <paramref name="migrationName"/>.</summary>
    private void Run(
        Options options,
        ConfigLoader configLoader,
        string migrationName,
        string workdir,
        IReadOnlyList<string> sourceRefs)
    {
        Config.Config config = LoadConfig(options, configLoader, migrationName);

        IMigration migration = config.GetMigration(migrationName);

        if (!options.Get<WorkflowOptions>().IsReadConfigFromChange())
        {
            _migrationRanConsumer(migration);
            migration.Run(workdir, sourceRefs);
            return;
        }

        ValidationException.CheckCondition(
            configLoader.SupportsLoadForRevision(),
            "{0} flag is not supported for the origin/config file path",
            "--read-config-from-change");

        // A safeguard, mirror workflows are not supported in the service anyway.
        ValidationException.CheckCondition(
            migration is Workflow<Copybara.Revision.IRevision, Copybara.Revision.IRevision>,
            "Flag --read-config-from-change is not supported for non-workflow migrations: {0}",
            migrationName);
        _migrationRanConsumer(migration);

        // TODO(port): ReadConfigFromChangeWorkflow is not ported yet. When it lands, replace the
        // fallthrough below with the equivalent of:
        //   new ReadConfigFromChangeWorkflow(workflow, options, configLoader, configValidator)
        //       .run(workdir, sourceRefs);
        throw new ValidationException(
            "--read-config-from-change is not yet supported in the .NET port.");
    }

    private Config.Config LoadConfig(Options options, ConfigLoader configLoader, string migrationName)
    {
        GeneralOptions generalOptions = options.Get<GeneralOptions>();
        Console console = generalOptions.GetConsole();
        Config.Config config = configLoader.Load(console);
        console.Progress("Validating configuration");
        ValidationResult result = _configValidator.Validate(config, migrationName);
        if (!result.HasErrors())
        {
            return config;
        }

        foreach (string error in result.GetErrors())
        {
            console.Error(error);
        }
        console.Error("Configuration is invalid.");
        throw new ValidationException(
            "Error validating configuration: Configuration is invalid.");
    }

    private void UpdateEnvironment(string migrationName)
    {
        foreach (object module in _moduleSet.GetModules().Values)
        {
            // We mutate the module per file loaded. Not ideal but it is the best we can do.
            if (module is ILabelsAwareModule m)
            {
                m.SetWorkflowName(migrationName);
            }
        }
    }

    public string Name => "migrate";
}
