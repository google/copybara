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

namespace Copybara.Onboard.Core;

/// <summary>
/// Non-generic view over <see cref="Input{T}"/>. Mirrors the Java use of the wildcard type
/// <c>Input&lt;?&gt;</c> in collections and provider maps.
/// </summary>
public interface IInput
{
    /// <summary>Name of the Input object.</summary>
    string Name { get; }

    /// <summary>Description of the Input object. Can be used to give context to users.</summary>
    string Description { get; }

    /// <summary>The runtime type of the values this Input produces.</summary>
    Type Type { get; }

    /// <summary>Whether this Input can only be inferred, never asked to the user.</summary>
    bool InferOnly { get; }

    /// <summary>
    /// Convert <paramref name="value"/> to the value type of this input.
    /// </summary>
    /// <exception cref="CannotConvertException">if the conversion is not possible.</exception>
    object ConvertObject(string value, IInputProviderResolver resolver);

    /// <summary>The default value, or null if none.</summary>
    object? DefaultValueObject { get; }
}
