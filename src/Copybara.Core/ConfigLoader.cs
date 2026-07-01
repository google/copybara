/*
 * Copyright (C) 2017 Google Inc.
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

using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Util.Console;
using Console = Copybara.Util.Console.Console;

namespace Copybara;

/// <summary>Loads the configuration from a given config file.</summary>
public class ConfigLoader
{
    private readonly SkylarkParser _skylarkParser;
    protected readonly ConfigFile ConfigFile;
    private readonly ModuleSet _moduleSet;

    public ConfigLoader(ModuleSet moduleSet, ConfigFile configFile, StarlarkMode validateStarlark)
    {
        _moduleSet = moduleSet;
        _skylarkParser = new SkylarkParser(_moduleSet.GetStaticModules(), validateStarlark);
        ConfigFile = Preconditions.CheckNotNull(configFile);
    }

    /// <summary>Returns a string representation of the location of this configuration.</summary>
    public string Location() => ConfigFile.Path();

    /// <summary>Loads the configuration using this loader.</summary>
    /// <param name="console">the console to use for reporting progress/errors.</param>
    public Config.Config Load(Console console) => LoadForConfigFile(console, ConfigFile);

    /// <summary>Loads the configuration and its dependencies using this loader.</summary>
    /// <param name="console">the console to use for reporting progress/errors.</param>
    public ConfigWithDependencies LoadWithDependencies(Console console)
    {
        console.ProgressFmt("Loading config and dependencies {0}", ConfigFile.GetIdentifier());

        using (_moduleSet.GetOptions().Get<GeneralOptions>().Profiler()
            .Start("loading_config_with_deps"))
        {
            return _skylarkParser.GetConfigWithTransitiveImports(ConfigFile, _moduleSet, console);
        }
    }

    protected Config.Config LoadForConfigFile(Console console, ConfigFile configFile)
    {
        console.ProgressFmt("Loading config {0}", configFile.GetIdentifier());

        using (_moduleSet.GetOptions().Get<GeneralOptions>().Profiler().Start("loading_config"))
        {
            return _skylarkParser.LoadConfig(configFile, _moduleSet, console);
        }
    }

    protected virtual Config.Config DoLoadForRevision(Console console, IRevision revision) =>
        throw new NotSupportedException(
            "This origin/configuration doesn't allow loading configs from specific revisions");

    public Config.Config LoadForRevision(Console console, IRevision revision)
    {
        using (_moduleSet.GetOptions().Get<GeneralOptions>().Profiler()
            .Start("loading_config_for_revision"))
        {
            return DoLoadForRevision(console, revision);
        }
    }

    public virtual bool SupportsLoadForRevision() => false;
}
