/*
 * Copyright (C) 2019 Google Inc.
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
using Copybara.Exceptions;
using Copybara.Transform;

namespace Copybara;

/// <summary>A reversible string-to-string mapper backed by an immutable map.</summary>
public class MapMapper : IReversibleFunction<string, string>
{
    private readonly ImmutableDictionary<string, string> _map;

    internal MapMapper(ImmutableDictionary<string, string> map)
    {
        _map = Preconditions.CheckNotNull(map);
    }

    public IReversibleFunction<string, string> ReverseMapping()
    {
        var inverse = ImmutableDictionary.CreateBuilder<string, string>();
        foreach (var kv in _map)
        {
            if (inverse.ContainsKey(kv.Value))
            {
                throw new NonReversibleValidationException(
                    "Non-reversible map: " + _map + ": key '" + kv.Value + "' mapped more than once");
            }
            inverse[kv.Value] = kv.Key;
        }
        return new MapMapper(inverse.ToImmutable());
    }

    public string Apply(string s) => _map.TryGetValue(s, out var v) ? v : s;
}
