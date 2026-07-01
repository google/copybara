// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

using System.Collections;
using Starlark.Annot;

namespace Starlark.Eval;

/// <summary>
/// A Dict is a Starlark dictionary (dict), a mapping from keys to values. Port of
/// <c>net.starlark.java.eval.Dict</c>.
///
/// <para>Iteration yields keys in insertion order. Order is unaffected by updating an existing
/// key's value, but a removed-then-reinserted key moves to the end (as in Starlark/Python).</para>
/// </summary>
[StarlarkBuiltin("dict", Category = "core", Doc = "The built-in dict type.")]
public sealed class Dict : IStarlarkPrintableValue, IFreezable, IStarlarkIndexable,
    IStarlarkIterable<object?>, IEnumerable
{
    // Insertion-ordered contents (a small hand-rolled ordered map to guarantee Starlark iteration order).
    private readonly OrderedMap contents;
    private int iteratorCount;
    private Mutability mutability;

    private static readonly Dict EmptyImmutable = new(Mutability.IMMUTABLE, new OrderedMap());

    private Dict(Mutability mutability, OrderedMap contents)
    {
        this.mutability = mutability;
        this.contents = contents;
    }

    /// <summary>
    /// Takes ownership of the map and returns a new Dict wrapping it. Null mutability means immutable.
    /// </summary>
    internal static Dict Wrap(Mutability? mu, OrderedMap contents)
    {
        mu ??= Mutability.IMMUTABLE;
        if (mu == Mutability.IMMUTABLE && contents.Count == 0)
        {
            return Empty();
        }
        return new Dict(mu, contents);
    }

    public bool Truth() => contents.Count != 0;

    public bool IsImmutable() => mutability.IsFrozen;

    public Mutability Mutability => mutability;

    public bool UpdateIteratorCount(int delta)
    {
        if (mutability.IsFrozen)
        {
            return false;
        }
        if (delta > 0)
        {
            iteratorCount++;
        }
        else if (delta < 0)
        {
            iteratorCount--;
        }
        return iteratorCount > 0;
    }

    public void UnsafeShallowFreeze()
    {
        Mutability.CheckUnsafeShallowFreezePrecondition(this);
        mutability = Mutability.IMMUTABLE;
    }

    public void CheckHashable() => throw Starlark.Errorf("unhashable type: 'dict'");

    public override int GetHashCode() => contents.GetHashCode();

    public override bool Equals(object? o) => o is Dict d && contents.Equals(d.contents);

    public IEnumerator<object?> GetEnumerator() => contents.Keys.GetEnumerator();

    IEnumerator IEnumerable.GetEnumerator() => contents.Keys.GetEnumerator();

    /// <summary>The number of entries.</summary>
    public int Count => contents.Count;

    /// <summary>Whether the dict is empty.</summary>
    public bool IsEmpty => contents.Count == 0;

    /// <summary>Returns the value for the given key, or null if absent.</summary>
    public object? Get(object key) => contents.TryGetValue(key, out object? v) ? v : null;

    public bool ContainsKeyJava(object key) => contents.ContainsKey(key);

    public IReadOnlyList<object?> Keys => contents.Keys;

    public IReadOnlyList<object?> Values => contents.Values;

    public IEnumerable<KeyValuePair<object?, object?>> Entries => contents.Entries;

    // -- factories --

    public static Dict Empty() => EmptyImmutable;

    /// <summary>Returns a new empty dict with the specified mutability (null means immutable).</summary>
    public static Dict Of(Mutability? mu)
    {
        mu ??= Mutability.IMMUTABLE;
        return mu == Mutability.IMMUTABLE ? Empty() : new Dict(mu, new OrderedMap());
    }

    /// <summary>Returns a new dict with the given mutability containing the entries of m.</summary>
    public static Dict CopyOf(Mutability? mu, IEnumerable<KeyValuePair<object?, object?>> m)
    {
        mu ??= Mutability.IMMUTABLE;
        var map = new OrderedMap();
        foreach (var e in m)
        {
            map.Put(Starlark.CheckValid(e.Key), Starlark.CheckValid(e.Value));
        }
        if (mu == Mutability.IMMUTABLE && map.Count == 0)
        {
            return Empty();
        }
        return new Dict(mu, map);
    }

    public static Dict ImmutableCopyOf(IEnumerable<KeyValuePair<object?, object?>> m) => CopyOf(null, m);

    public static Builder NewBuilder() => new();

    /// <summary>A reusable builder for Dicts.</summary>
    public sealed class Builder
    {
        private readonly List<object?> items = new(); // [k, v, ... k, v]

        public Builder Put(object? k, object? v)
        {
            items.Add(Starlark.CheckValid(k));
            items.Add(Starlark.CheckValid(v));
            return this;
        }

        public Builder PutAll(IEnumerable<KeyValuePair<object?, object?>> map)
        {
            foreach (var e in map)
            {
                Put(e.Key, e.Value);
            }
            return this;
        }

        public Dict BuildImmutable() => Build(null);

        public Dict Build(Mutability? mu)
        {
            mu ??= Mutability.IMMUTABLE;
            if (mu == Mutability.IMMUTABLE && items.Count == 0)
            {
                return Empty();
            }
            var map = new OrderedMap();
            for (int i = 0; i < items.Count; i += 2)
            {
                map.Put(items[i], items[i + 1]);
            }
            return new Dict(mu, map);
        }
    }

    // -- mutators (checked against freezing) --

    /// <summary>Puts an entry into the dict, after validating that mutation is allowed.</summary>
    public void PutEntry(object? key, object? value)
    {
        Starlark.CheckMutable(this);
        Starlark.CheckHashable(key);
        contents.Put(key, value);
    }

    /// <summary>Puts all the entries from a given map into the dict.</summary>
    public void PutEntries(IEnumerable<KeyValuePair<object?, object?>> map)
    {
        Starlark.CheckMutable(this);
        foreach (var e in map)
        {
            Starlark.CheckHashable(e.Key);
            contents.Put(e.Key, e.Value);
        }
    }

    /// <summary>Deletes the entry associated with the given key; returns its value or null.</summary>
    public object? RemoveEntry(object key)
    {
        Starlark.CheckMutable(this);
        return contents.Remove(key, out object? v) ? v : null;
    }

    [StarlarkMethod("clear", Doc = "Remove all items from the dictionary.")]
    public void ClearEntries()
    {
        Starlark.CheckMutable(this);
        contents.Clear();
    }

    [StarlarkMethod("get", Doc = "Returns the value for key if present, else default.",
        UseStarlarkThread = true)]
    public object? Get2(
        [Param(Name = "key")] object key,
        [Param(Name = "default", DefaultValue = "None", Named = true)] object? defaultValue,
        StarlarkThread thread)
    {
        object? v = Get(key);
        if (v != null)
        {
            return v;
        }
        ContainsKey(thread.GetSemantics(), key); // throws if unhashable
        return defaultValue;
    }

    [StarlarkMethod("pop", Doc = "Removes a key and returns the associated value.",
        UseStarlarkThread = true)]
    public object? Pop(
        [Param(Name = "key")] object key,
        [Param(Name = "default", DefaultValue = "unbound", Named = true)] object? defaultValue,
        StarlarkThread thread)
    {
        Starlark.CheckMutable(this);
        if (contents.Remove(key, out object? value))
        {
            return value;
        }
        Starlark.CheckHashable(key);
        if (!ReferenceEquals(defaultValue, Starlark.UNBOUND))
        {
            return defaultValue;
        }
        throw Starlark.Errorf("KeyError: {0}", Starlark.Repr(key, thread.GetSemantics()));
    }

    [StarlarkMethod("popitem", Doc = "Remove and return the first (key, value) pair.")]
    public Tuple Popitem()
    {
        if (IsEmpty)
        {
            throw Starlark.Errorf("popitem: empty dictionary");
        }
        Starlark.CheckMutable(this);
        var e = contents.First();
        contents.Remove(e.Key!, out _);
        return Tuple.Pair(e.Key, e.Value);
    }

    [StarlarkMethod("setdefault", Doc = "Returns the value for key, inserting default if absent.")]
    public object? Setdefault(
        [Param(Name = "key")] object? key,
        [Param(Name = "default", DefaultValue = "None", Named = true)] object? defaultValue)
    {
        Starlark.CheckMutable(this);
        Starlark.CheckHashable(key);
        if (contents.TryGetValue(key, out object? prev))
        {
            return prev;
        }
        contents.Put(key, defaultValue);
        return defaultValue;
    }

    [StarlarkMethod("update", Doc = "Updates the dictionary with pairs then keyword arguments.",
        UseStarlarkThread = true)]
    public void Update(
        [Param(Name = "pairs", DefaultValue = "[]")] object pairs,
        Dict kwargs,
        StarlarkThread thread)
    {
        Starlark.CheckMutable(this);
        UpdateCommon("update", this, pairs, kwargs.Entries);
    }

    // Common implementation of dict(pairs, **kwargs) and dict.update(pairs, **kwargs).
    internal static void UpdateCommon(
        string funcname, Dict dict, object pairs, IEnumerable<KeyValuePair<object?, object?>> kwargs)
    {
        if (pairs is Dict pd)
        {
            dict.PutEntries(pd.Entries);
        }
        else
        {
            IEnumerable<object?> iterable;
            try
            {
                iterable = Starlark.ToIterable(pairs);
            }
            catch (EvalException)
            {
                throw Starlark.Errorf("in {0}, got {1}, want iterable", funcname, Starlark.Type(pairs));
            }
            int pos = 0;
            foreach (object? item in iterable)
            {
                object?[] pair;
                try
                {
                    pair = Starlark.ToArray(item);
                }
                catch (EvalException)
                {
                    throw Starlark.Errorf(
                        "in {0}, dictionary update sequence element #{1} is not iterable ({2})",
                        funcname, pos, Starlark.Type(item));
                }
                if (pair.Length != 2)
                {
                    throw Starlark.Errorf(
                        "in {0}, item #{1} has length {2}, but exactly two elements are required",
                        funcname, pos, pair.Length);
                }
                dict.PutEntry(pair[0], pair[1]);
                pos++;
            }
        }
        dict.PutEntries(kwargs);
    }

    [StarlarkMethod("values", Doc = "Returns the list of values.", UseStarlarkThread = true)]
    public StarlarkList Values0(StarlarkThread thread) =>
        StarlarkList.CopyOf(thread.Mutability, contents.Values);

    [StarlarkMethod("items", Doc = "Returns the list of key-value tuples.", UseStarlarkThread = true)]
    public StarlarkList Items(StarlarkThread thread)
    {
        object?[] array = new object?[Count];
        int i = 0;
        foreach (var e in contents.Entries)
        {
            array[i++] = Tuple.Pair(e.Key, e.Value);
        }
        return StarlarkList.Wrap(thread.Mutability, array);
    }

    [StarlarkMethod("keys", Doc = "Returns the list of keys.", UseStarlarkThread = true)]
    public StarlarkList Keys0(StarlarkThread thread) =>
        StarlarkList.Wrap(thread.Mutability, contents.Keys.ToArray());

    public void Repr(Printer printer, StarlarkSemantics semantics)
    {
        printer.Append("{");
        string sep = "";
        foreach (var e in contents.Entries)
        {
            printer.Append(sep);
            sep = ", ";
            printer.Repr(e.Key, semantics).Append(": ").Repr(e.Value, semantics);
        }
        printer.Append("}");
    }

    public override string ToString() => Starlark.Repr(this, StarlarkSemantics.DEFAULT);

    // -- StarlarkIndexable --

    public object? GetIndex(StarlarkSemantics semantics, object key)
    {
        object? v = Get(key);
        if (v == null)
        {
            throw Starlark.Errorf("key {0} not found in dictionary", Starlark.Repr(key, semantics));
        }
        return v;
    }

    public bool ContainsKey(StarlarkSemantics semantics, object key)
    {
        Starlark.CheckHashable(key);
        return contents.ContainsKey(key);
    }

    /// <summary>
    /// A small insertion-ordered map used to back Dict, keyed by Starlark value equality. Deleting
    /// then reinserting a key moves it to the end, matching Starlark semantics.
    /// </summary>
    internal sealed class OrderedMap
    {
        private readonly Dictionary<object, int> index = new(StarlarkKeyComparer.Instance);
        private readonly List<KeyValuePair<object?, object?>> order = new();
        private int liveCount;

        public int Count => liveCount;

        private static readonly object NullSentinel = new();

        private static object Box(object? k) => k ?? NullSentinel;
        private static object? Unbox(object k) => ReferenceEquals(k, NullSentinel) ? null : k;

        public bool ContainsKey(object? key) => index.ContainsKey(Box(key));

        public bool TryGetValue(object? key, out object? value)
        {
            if (index.TryGetValue(Box(key), out int i))
            {
                value = order[i].Value;
                return true;
            }
            value = null;
            return false;
        }

        public void Put(object? key, object? value)
        {
            object bk = Box(key);
            if (index.TryGetValue(bk, out int i))
            {
                order[i] = new KeyValuePair<object?, object?>(key, value);
            }
            else
            {
                index[bk] = order.Count;
                order.Add(new KeyValuePair<object?, object?>(key, value));
                liveCount++;
            }
        }

        public bool Remove(object? key, out object? value)
        {
            object bk = Box(key);
            if (index.TryGetValue(bk, out int i))
            {
                value = order[i].Value;
                index.Remove(bk);
                order[i] = new KeyValuePair<object?, object?>(Tombstone, null);
                liveCount--;
                if (liveCount < order.Count / 2)
                {
                    Compact();
                }
                return true;
            }
            value = null;
            return false;
        }

        public void Clear()
        {
            index.Clear();
            order.Clear();
            liveCount = 0;
        }

        public KeyValuePair<object?, object?> First()
        {
            foreach (var e in order)
            {
                if (!ReferenceEquals(e.Key, Tombstone))
                {
                    return e;
                }
            }
            throw new InvalidOperationException("empty");
        }

        public IReadOnlyList<object?> Keys => Entries.Select(e => e.Key).ToList();

        public IReadOnlyList<object?> Values => Entries.Select(e => e.Value).ToList();

        public IEnumerable<KeyValuePair<object?, object?>> Entries
        {
            get
            {
                foreach (var e in order)
                {
                    if (!ReferenceEquals(e.Key, Tombstone))
                    {
                        yield return e;
                    }
                }
            }
        }

        private static readonly object Tombstone = new();

        private void Compact()
        {
            var live = new List<KeyValuePair<object?, object?>>(liveCount);
            index.Clear();
            foreach (var e in order)
            {
                if (!ReferenceEquals(e.Key, Tombstone))
                {
                    index[Box(e.Key)] = live.Count;
                    live.Add(e);
                }
            }
            order.Clear();
            order.AddRange(live);
        }

        public override int GetHashCode()
        {
            int h = 0;
            foreach (var e in Entries)
            {
                h += (e.Key?.GetHashCode() ?? 0) ^ (e.Value?.GetHashCode() ?? 0);
            }
            return h;
        }

        public override bool Equals(object? obj)
        {
            if (obj is not OrderedMap other || liveCount != other.liveCount)
            {
                return false;
            }
            foreach (var e in Entries)
            {
                if (!other.TryGetValue(e.Key, out object? v) || !object.Equals(e.Value, v))
                {
                    return false;
                }
            }
            return true;
        }
    }

    // Equality comparer that uses Starlark value equality/hashing for keys.
    private sealed class StarlarkKeyComparer : IEqualityComparer<object>
    {
        public static readonly StarlarkKeyComparer Instance = new();

        public new bool Equals(object? x, object? y) => object.Equals(x, y);

        public int GetHashCode(object obj) => obj.GetHashCode();
    }
}
