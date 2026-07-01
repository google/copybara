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

namespace Copybara.Config;

/// <summary>
/// A class that contains a loaded config and all the config files that were accessed during the
/// parsing.
///
/// <para>Upstream this is a nested class <c>SkylarkParser.ConfigWithDependencies</c>; it is promoted
/// to a top-level type in the .NET port.</para>
/// </summary>
public sealed class ConfigWithDependencies
{
    private readonly ImmutableDictionary<string, ConfigFile> _files;
    private readonly Config _config;

    internal ConfigWithDependencies(ImmutableDictionary<string, ConfigFile> files, Config config)
    {
        _config = config;
        _files = files;
    }

    public Config GetConfig() => _config;

    public ImmutableDictionary<string, ConfigFile> GetFiles() => _files;
}
