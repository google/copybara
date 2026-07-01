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
/// A <see cref="IConverter{T}"/> is a function that allows converting a string to the corresponding
/// <c>T</c> type. It should be used only in the context of <c>Data</c> objects.
/// </summary>
public interface IConverter<out T>
{
    /// <summary>
    /// Convert <paramref name="value"/> to <c>T</c>.
    /// </summary>
    /// <exception cref="CannotConvertException">if the conversion is not possible (e.g. wrong
    /// input).</exception>
    T Convert(string value, IInputProviderResolver resolver);
}
