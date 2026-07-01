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

using Copybara.Common;

namespace Copybara.Onboard.Core;

/// <summary>
/// An Input provider that uses a constant map as the source of values. Can be used for providing
/// values from CLI flags. Port of
/// <c>com.google.copybara.onboard.core.MapBasedInputProvider</c>.
/// </summary>
public class MapBasedInputProvider : IInputProvider
{
    private readonly IReadOnlyDictionary<string, string> _map;
    private readonly int _priority;

    public MapBasedInputProvider(IReadOnlyDictionary<string, string> map, int priority)
    {
        _map = Preconditions.CheckNotNull(map, nameof(map));
        _priority = priority;
    }

    public T? Resolve<T>(Input<T> input, IInputProviderResolver resolver)
        where T : class
    {
        foreach (string s in _map.Keys)
        {
            IInput ourInput = FindInput(s);
            if (ReferenceEquals(ourInput, input))
            {
                try
                {
                    return input.Convert(_map[s], resolver);
                }
                catch (Exception e)
                {
                    // This could be console.error instead and return null so the user can correct in
                    // the iterative mode.
                    throw new CannotProvideException(
                        $"Invalid value for {input.Description}({input.Name}): {e.Message}");
                }
            }
        }

        return null;
    }

    public IReadOnlyDictionary<IInput, int> Provides()
    {
        var result = ImmutableDictionary.CreateBuilder<IInput, int>();
        foreach (string s in _map.Keys)
        {
            result[FindInput(s)] = _priority;
        }

        return result.ToImmutable();
    }

    /// <exception cref="CannotProvideException"/>
    private static IInput FindInput(string s)
    {
        IReadOnlyDictionary<string, IInput> registered = InputRegistry.RegisteredInputs();
        if (!registered.TryGetValue(s, out IInput? input))
        {
            throw new CannotProvideException(
                $"Invalid input type '{s}'. Available inputs: [{string.Join(", ", registered.Values)}]");
        }

        return input;
    }
}
