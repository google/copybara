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
/// Validates that the configuration is correct.
/// </summary>
public sealed class ValidateCmd : ICopybaraCmd
{
    private readonly ConfigValidator _configValidator;
    private readonly IConfigLoaderProvider _configLoaderProvider;

    public ValidateCmd(
        ConfigValidator configValidator,
        Action<IMigration> migrationRanConsumer,
        IConfigLoaderProvider configLoaderProvider)
    {
        _ = migrationRanConsumer;
        _configValidator = Preconditions.CheckNotNull(configValidator);
        _configLoaderProvider = Preconditions.CheckNotNull(configLoaderProvider);
    }

    public ExitCode Run(CommandEnv commandEnv)
    {
        ConfigFileArgs configFileArgs = commandEnv.GetConfigFileArgs()!;
        ConfigLoader configLoader =
            _configLoaderProvider.NewLoader(
                configFileArgs.GetConfigPath(), configFileArgs.GetSourceRef());
        ValidationResult result =
            Validate(commandEnv.GetOptions(), configLoader, configFileArgs.GetWorkflowName());

        Console console = commandEnv.GetOptions().Get<GeneralOptions>().GetConsole();
        foreach (var message in result.GetAllMessages())
        {
            switch (message.GetLevel())
            {
                case ValidationResult.Level.WARNING:
                    console.Warn(message.GetMessage());
                    break;
                case ValidationResult.Level.ERROR:
                    console.Error(message.GetMessage());
                    break;
            }
        }

        if (result.HasErrors())
        {
            console.ErrorFmt("Configuration '%s' is invalid.", configLoader.Location());
            return ExitCode.ConfigurationError;
        }

        console.InfoFmt("Configuration '%s' is valid.", configLoader.Location());
        return ExitCode.Success;
    }

    /// <summary>
    /// Validates that the configuration is correct and that there is a valid migration specified by
    /// <paramref name="migrationName"/>.
    ///
    /// <para>Note that, besides validating the specific migration, all the configuration will be
    /// validated syntactically.</para>
    /// </summary>
    private ValidationResult Validate(Options options, ConfigLoader configLoader, string migrationName)
    {
        Console console = options.Get<GeneralOptions>().GetConsole();
        var resultBuilder = new ValidationResult.Builder();
        try
        {
            Config.Config config = configLoader.Load(console);
            resultBuilder.Append(_configValidator.Validate(config, migrationName));
        }
        catch (ValidationException e)
        {
            // The validate subcommand should not throw Validation exceptions but log a result.
            var error = new System.Text.StringBuilder(e.Message).Append('\n');
            Exception? cause = e.InnerException;
            while (cause != null)
            {
                error.Append("  CAUSED BY: ").Append(cause.Message).Append('\n');
                cause = cause.InnerException;
            }
            resultBuilder.Error(error.ToString());
        }

        return resultBuilder.Build();
    }

    public string Name => "validate";
}
