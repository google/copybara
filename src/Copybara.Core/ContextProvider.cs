/*
 * Copyright (C) 2019 Google Inc.
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

using Copybara.Config;
using Console = Copybara.Util.Console.Console;

namespace Copybara;

/// <summary>A class providing additional context for CMD.</summary>
public interface IContextProvider
{
    /// <summary>Get context for CMD.</summary>
    IReadOnlyDictionary<string, string> GetContext(
        Config.Config config,
        ConfigFileArgs configFileArgs,
        IConfigLoaderProvider configLoaderProvider,
        Console console);

    /// <summary>Get context for CMD.</summary>
    IReadOnlyDictionary<string, string> GetContext(
        ConfigWithDependencies config,
        ConfigFileArgs configFileArgs,
        IConfigLoaderProvider configLoaderProvider,
        Options options,
        Console console) =>
        GetContext(config.GetConfig(), configFileArgs, configLoaderProvider, console);
}
