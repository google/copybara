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
using Copybara.Common;

namespace Copybara;

/// <summary>
/// A set of modules and options for evaluating a Skylark config file.
/// </summary>
public class ModuleSet
{
    private readonly Options _options;

    // TODO(malcon): Remove this once all modules are @StarlarkMethod
    private readonly IReadOnlySet<Type> _staticModules;
    private readonly IReadOnlyDictionary<string, object> _modules;

    internal ModuleSet(
        Options options,
        IReadOnlySet<Type> staticModules,
        IReadOnlyDictionary<string, object> modules)
    {
        _options = Preconditions.CheckNotNull(options);
        _staticModules = Preconditions.CheckNotNull(staticModules);
        _modules = Preconditions.CheckNotNull(modules);
    }

    /// <summary>Copybara options.</summary>
    public Options GetOptions() => _options;

    /// <summary>
    /// Static modules. Will be deleted.
    /// TODO(malcon): Delete
    /// </summary>
    public IReadOnlySet<Type> GetStaticModules() => _staticModules;

    /// <summary>Non-static Copybara modules.</summary>
    public IReadOnlyDictionary<string, object> GetModules() => _modules;
}
