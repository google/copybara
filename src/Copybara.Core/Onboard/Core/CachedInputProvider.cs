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

using Copybara.Common;

namespace Copybara.Onboard.Core;

/// <summary>
/// A simple provider that caches the request to avoid calling populators or asking the user for the
/// same value several times. Port of
/// <c>com.google.copybara.onboard.core.CachedInputProvider</c>.
/// </summary>
public class CachedInputProvider : IInputProvider
{
    private readonly Dictionary<IInput, object> _values = new();
    private readonly IInputProvider _provider;

    public CachedInputProvider(IInputProvider provider)
    {
        _provider = Preconditions.CheckNotNull(provider, nameof(provider));
    }

    public T? Resolve<T>(Input<T> input, IInputProviderResolver db)
        where T : class
    {
        if (_values.TryGetValue(input, out object? cached))
        {
            return (T)cached;
        }

        T? t = _provider.Resolve(input, db);
        if (t != null)
        {
            _values[input] = t;
        }

        return t;
    }

    public IReadOnlyDictionary<IInput, int> Provides() => _provider.Provides();

    public override string ToString() => "Cached(" + _provider + ')';
}
