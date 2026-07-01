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
/// Given an <see cref="IInput"/> and a collection of <see cref="IInputProvider"/>s for that Input,
/// creates an InputProvider that calls the delegate InputProviders in priority order. Port of
/// <c>com.google.copybara.onboard.core.PrioritizedInputProvider</c>.
/// </summary>
public class PrioritizedInputProvider : IInputProvider
{
    private readonly IInput _input;

    // Kept sorted descending by priority (highest priority first).
    private readonly List<PrioritizedEntry> _providers = new();

    /// <exception cref="CannotProvideException"/>
    public PrioritizedInputProvider(IInput input, IEnumerable<IInputProvider> providers)
    {
        _input = input;
        foreach (IInputProvider provider in providers)
        {
            if (!provider.Provides().TryGetValue(input, out int priority))
            {
                throw new InvalidOperationException(
                    $"Provider {provider} doesn't provide {input}");
            }

            _providers.Add(new PrioritizedEntry(provider, priority));
        }

        // reversed so highest priority is the biggest number and comes first.
        _providers.Sort((a, b) => b.Priority.CompareTo(a.Priority));
    }

    public T? Resolve<T>(Input<T> input, IInputProviderResolver db)
        where T : class
    {
        foreach (PrioritizedEntry p in _providers)
        {
            T? result = p.Provider.Resolve(input, db);
            if (result != null)
            {
                return result;
            }
        }

        return null;
    }

    public IReadOnlyDictionary<IInput, int> Provides()
    {
        // Doesn't matter much but just in case we wrap this in another provider in the future.
        return ImmutableDictionary.CreateRange(
            new[] { new KeyValuePair<IInput, int>(_input, _providers[0].Priority) });
    }

    private sealed class PrioritizedEntry
    {
        public PrioritizedEntry(IInputProvider provider, int priority)
        {
            Provider = provider;
            Priority = priority;
        }

        public IInputProvider Provider { get; }

        public int Priority { get; }

        public override string ToString() => Provider.ToString() ?? string.Empty;
    }

    public override string ToString() =>
        "Prioritized([" + string.Join(", ", _providers) + "])";
}
