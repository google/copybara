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

using System.Collections.Immutable;

namespace Copybara.Onboard.Core;

/// <summary>
/// A data provider provides values of <see cref="Input{T}"/> to populators and config templates.
/// </summary>
public interface IInputProvider
{
    /// <summary>Default provider priority.</summary>
    public const int DefaultPriority = 100;

    /// <summary>Priority for values coming from the command line.</summary>
    public const int CommandLinePriority = 1000;

    /// <summary>
    /// Resolve the value for an <see cref="Input{T}"/> object. Returns <c>null</c> when no value can
    /// be provided (mirrors the Java <c>Optional.empty()</c>).
    /// </summary>
    /// <exception cref="System.Threading.ThreadInterruptedException">if user cancels the request.</exception>
    /// <exception cref="CannotProvideException">if there is a failure during the resolution.</exception>
    T? Resolve<T>(Input<T> input, IInputProviderResolver db)
        where T : class;

    /// <summary>
    /// Return the set of <see cref="Input{T}"/> objects that this provider can provide, with its
    /// associated priority. The higher, the more priority.
    /// </summary>
    IReadOnlyDictionary<IInput, int> Provides();

    /// <summary>
    /// Given a set of Input returns a map of the Input to default priority. This is a helper for
    /// <see cref="Provides"/> implementations that don't care about priorities.
    /// </summary>
    public IReadOnlyDictionary<IInput, int> DefaultPriorityMap(IEnumerable<IInput> data)
    {
        var builder = ImmutableDictionary.CreateBuilder<IInput, int>();
        foreach (var d in data)
        {
            builder[d] = DefaultPriority;
        }

        return builder.ToImmutable();
    }
}
