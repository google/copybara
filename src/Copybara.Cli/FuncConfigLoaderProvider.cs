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
using Copybara.Config;

namespace Copybara.Cli;

/// <summary>
/// A <see cref="ConfigLoaderProvider"/> backed by a delegate. Mirrors the lambda-based
/// implementation returned by upstream's <c>Main.newConfigLoaderProvider</c>.
///
/// <para>Note: this assumes the config agent defines <c>ConfigLoaderProvider</c> in the
/// <c>Copybara</c> namespace as an interface exposing
/// <c>ConfigLoader NewLoader(string configPath, string? sourceRef)</c> (faithful to Java's
/// <c>ConfigLoaderProvider.newLoader</c>). If it lands as a delegate instead, this adapter should
/// be replaced during consolidation.</para>
/// </summary>
public sealed class FuncConfigLoaderProvider : IConfigLoaderProvider
{
    private readonly Func<string, string?, ConfigLoader> _factory;

    public FuncConfigLoaderProvider(Func<string, string?, ConfigLoader> factory)
    {
        _factory = factory;
    }

    public ConfigLoader NewLoader(string configPath, string? sourceRef) =>
        _factory(configPath, sourceRef);
}
