/*
 * Copyright (C) 2016 Google Inc.
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
/// An immutable multimap preserving insertion order of keys and values, mirroring the
/// subset of Guava's <c>ImmutableListMultimap</c> that Copybara relies on. A key may map to
/// multiple values; iteration order matches insertion order.
/// </summary>
public sealed class ImmutableListMultimap<TKey, TValue> : IEnumerable<KeyValuePair<TKey, TValue>>
    where TKey : notnull
{
    private readonly ImmutableArray<KeyValuePair<TKey, TValue>> _entries;
    private readonly ImmutableDictionary<TKey, ImmutableArray<TValue>> _index;

    private ImmutableListMultimap(
        ImmutableArray<KeyValuePair<TKey, TValue>> entries,
        ImmutableDictionary<TKey, ImmutableArray<TValue>> index)
    {
        _entries = entries;
        _index = index;
    }

    private static readonly ImmutableListMultimap<TKey, TValue> EmptyInstance =
        new(ImmutableArray<KeyValuePair<TKey, TValue>>.Empty,
            ImmutableDictionary<TKey, ImmutableArray<TValue>>.Empty);

    public static ImmutableListMultimap<TKey, TValue> Empty => EmptyInstance;

    /// <summary>All values associated with <paramref name="key"/> in insertion order (empty if none).</summary>
    public ImmutableArray<TValue> this[TKey key] =>
        _index.TryGetValue(key, out var values) ? values : ImmutableArray<TValue>.Empty;

    /// <summary>All values associated with <paramref name="key"/> in insertion order (empty if none).</summary>
    public ImmutableArray<TValue> Get(TKey key) => this[key];

    public bool ContainsKey(TKey key) => _index.ContainsKey(key);

    public bool ContainsEntry(TKey key, TValue value) =>
        _index.TryGetValue(key, out var values) && values.Contains(value);

    public IEnumerable<TKey> Keys => _index.Keys;

    public int Count => _entries.Length;

    public bool IsEmpty => _entries.IsEmpty;

    /// <summary>Returns the entries as a map from key to its list of values.</summary>
    public ImmutableDictionary<TKey, ImmutableArray<TValue>> AsMap() => _index;

    public IEnumerator<KeyValuePair<TKey, TValue>> GetEnumerator() =>
        ((IEnumerable<KeyValuePair<TKey, TValue>>)_entries).GetEnumerator();

    IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();

    public static Builder CreateBuilder() => new();

    /// <summary>Builder that accumulates entries preserving insertion order.</summary>
    public sealed class Builder
    {
        private readonly ImmutableArray<KeyValuePair<TKey, TValue>>.Builder _entries =
            ImmutableArray.CreateBuilder<KeyValuePair<TKey, TValue>>();

        public Builder Put(TKey key, TValue value)
        {
            _entries.Add(new KeyValuePair<TKey, TValue>(key, value));
            return this;
        }

        public Builder PutAll(TKey key, IEnumerable<TValue> values)
        {
            foreach (var value in values)
            {
                _entries.Add(new KeyValuePair<TKey, TValue>(key, value));
            }
            return this;
        }

        public Builder PutAll(ImmutableListMultimap<TKey, TValue> multimap)
        {
            foreach (var entry in multimap)
            {
                _entries.Add(entry);
            }
            return this;
        }

        public ImmutableListMultimap<TKey, TValue> Build()
        {
            var entries = _entries.ToImmutable();
            var perKey = new Dictionary<TKey, ImmutableArray<TValue>.Builder>();
            foreach (var entry in entries)
            {
                if (!perKey.TryGetValue(entry.Key, out var listBuilder))
                {
                    listBuilder = ImmutableArray.CreateBuilder<TValue>();
                    perKey[entry.Key] = listBuilder;
                }
                listBuilder.Add(entry.Value);
            }

            var indexBuilder = ImmutableDictionary.CreateBuilder<TKey, ImmutableArray<TValue>>();
            foreach (var kvp in perKey)
            {
                indexBuilder[kvp.Key] = kvp.Value.ToImmutable();
            }

            return new ImmutableListMultimap<TKey, TValue>(entries, indexBuilder.ToImmutable());
        }
    }
}
