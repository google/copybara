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

using System.Collections;
using System.Collections.Immutable;

namespace Copybara.Common;

/// <summary>
/// An immutable multimap where the values for each key form a set (no duplicate values per key),
/// mirroring the subset of Guava's <c>ImmutableSetMultimap</c> that Copybara relies on. Key insertion
/// order is preserved; per key, values keep their first-seen insertion order and duplicates are
/// dropped.
/// </summary>
public sealed class ImmutableSetMultimap<TKey, TValue> : IEnumerable<KeyValuePair<TKey, TValue>>
    where TKey : notnull
{
    private readonly ImmutableArray<KeyValuePair<TKey, TValue>> _entries;
    private readonly ImmutableDictionary<TKey, ImmutableHashSet<TValue>> _index;

    private ImmutableSetMultimap(
        ImmutableArray<KeyValuePair<TKey, TValue>> entries,
        ImmutableDictionary<TKey, ImmutableHashSet<TValue>> index)
    {
        _entries = entries;
        _index = index;
    }

    private static readonly ImmutableSetMultimap<TKey, TValue> EmptyInstance =
        new(ImmutableArray<KeyValuePair<TKey, TValue>>.Empty,
            ImmutableDictionary<TKey, ImmutableHashSet<TValue>>.Empty);

    public static ImmutableSetMultimap<TKey, TValue> Empty => EmptyInstance;

    /// <summary>All values associated with <paramref name="key"/> (empty if none).</summary>
    public ImmutableHashSet<TValue> this[TKey key] =>
        _index.TryGetValue(key, out var values) ? values : ImmutableHashSet<TValue>.Empty;

    /// <summary>All values associated with <paramref name="key"/> (empty if none).</summary>
    public ImmutableHashSet<TValue> Get(TKey key) => this[key];

    public bool ContainsKey(TKey key) => _index.ContainsKey(key);

    public bool ContainsEntry(TKey key, TValue value) =>
        _index.TryGetValue(key, out var values) && values.Contains(value);

    public IEnumerable<TKey> Keys => _index.Keys;

    public int Count => _entries.Length;

    public bool IsEmpty => _entries.IsEmpty;

    /// <summary>Returns the entries as a map from key to its set of values.</summary>
    public ImmutableDictionary<TKey, ImmutableHashSet<TValue>> AsMap() => _index;

    public IEnumerator<KeyValuePair<TKey, TValue>> GetEnumerator() =>
        ((IEnumerable<KeyValuePair<TKey, TValue>>)_entries).GetEnumerator();

    IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();

    public static Builder CreateBuilder() => new();

    /// <summary>Builder that accumulates entries, dropping duplicate (key, value) pairs.</summary>
    public sealed class Builder
    {
        private readonly List<KeyValuePair<TKey, TValue>> _entries = new();
        private readonly HashSet<(TKey, TValue)> _seen = new();

        public Builder Put(TKey key, TValue value)
        {
            if (_seen.Add((key, value)))
            {
                _entries.Add(new KeyValuePair<TKey, TValue>(key, value));
            }
            return this;
        }

        public Builder PutAll(TKey key, IEnumerable<TValue> values)
        {
            foreach (var value in values)
            {
                Put(key, value);
            }
            return this;
        }

        public Builder PutAll(ImmutableSetMultimap<TKey, TValue> multimap)
        {
            foreach (var entry in multimap)
            {
                Put(entry.Key, entry.Value);
            }
            return this;
        }

        public ImmutableSetMultimap<TKey, TValue> Build()
        {
            var entries = _entries.ToImmutableArray();
            var perKey = new Dictionary<TKey, ImmutableHashSet<TValue>.Builder>();
            var order = new List<TKey>();
            foreach (var entry in entries)
            {
                if (!perKey.TryGetValue(entry.Key, out var setBuilder))
                {
                    setBuilder = ImmutableHashSet.CreateBuilder<TValue>();
                    perKey[entry.Key] = setBuilder;
                    order.Add(entry.Key);
                }
                setBuilder.Add(entry.Value);
            }

            var indexBuilder = ImmutableDictionary.CreateBuilder<TKey, ImmutableHashSet<TValue>>();
            foreach (var key in order)
            {
                indexBuilder[key] = perKey[key].ToImmutable();
            }

            return new ImmutableSetMultimap<TKey, TValue>(entries, indexBuilder.ToImmutable());
        }
    }
}
