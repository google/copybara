/*
 * Copyright (C) 2022 Google Inc.
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

namespace Copybara.Onboard.Core.Template;

/// <summary>
/// Config generators can generate a config file (as a <c>string</c>) given an
/// <see cref="IInputProviderResolver"/> with context of the <see cref="Input{T}"/>s. Port of
/// <c>com.google.copybara.onboard.core.template.ConfigGenerator</c>.
/// </summary>
public interface IConfigGenerator
{
    /// <exception cref="CannotProvideException"/>
    /// <exception cref="System.Threading.ThreadInterruptedException"/>
    string Generate(IInputProviderResolver inputProviders);

    /// <summary>Name of the template.</summary>
    string Name { get; }

    /// <summary>List of <see cref="IInput"/>s that the generator consumes.</summary>
    IReadOnlySet<IInput> Consumes();

    /// <summary>
    /// Returns true if this generator is a valid generator given the inputs (e.g. output folder).
    /// </summary>
    /// <exception cref="System.Threading.ThreadInterruptedException"/>
    bool IsGenerator(IInputProviderResolver resolver);
}
