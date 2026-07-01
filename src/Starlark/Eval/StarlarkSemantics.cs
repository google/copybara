// Copyright 2019 The Bazel Authors. All rights reserved.
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

using System.Collections.Immutable;
using System.Text;

namespace Starlark.Eval;

/// <summary>
/// A StarlarkSemantics is an immutable set of optional name/value pairs that affect the dynamic
/// behavior of Starlark operators and built-in functions. Port of
/// <c>net.starlark.java.eval.StarlarkSemantics</c>.
///
/// <para>Boolean values are accessed using a string key prefixed with "+" (default true) or "-"
/// (default false). Other values are accessed using a <see cref="Key{T}"/>.</para>
/// </summary>
public class StarlarkSemantics
{
    /// <summary>The empty semantics, in which every option has its default value.</summary>
    public static readonly StarlarkSemantics DEFAULT =
        new(ImmutableSortedDictionary<string, object>.Empty);

    // A map entry is accessed by Key iff its name has no [+-] prefix.
    private readonly ImmutableSortedDictionary<string, object> map;
    private readonly int hashCode;

    private StarlarkSemantics(ImmutableSortedDictionary<string, object> map)
    {
        this.map = map;
        int h = 0;
        foreach (var e in map)
        {
            h += e.Key.GetHashCode() ^ e.Value.GetHashCode();
        }
        this.hashCode = h;
    }

    protected StarlarkSemantics(StarlarkSemantics other) : this(other.map) { }

    /// <summary>Returns the value of a boolean option, which must have a [+-] prefix.</summary>
    public bool GetBool(string name)
    {
        char prefix = name[0];
        if (prefix != '+' && prefix != '-')
        {
            throw new ArgumentException("boolean option name must start with + or -: " + name);
        }
        bool defaultValue = prefix == '+';
        return map.TryGetValue(name, out object? v) ? (bool)v : defaultValue;
    }

    /// <summary>Returns the value of the option denoted by <paramref name="key"/>.</summary>
    public T Get<T>(Key<T> key) where T : notnull =>
        map.TryGetValue(key.Name, out object? v) ? (T)v : key.DefaultValue;

    /// <summary>
    /// Returns the value of the option with the given name, or the default value if not set.
    /// </summary>
    public object GetGeneric(string name, object defaultValue)
    {
        if (map.TryGetValue(name, out object? v)
            || map.TryGetValue("+" + name, out v)
            || map.TryGetValue("-" + name, out v))
        {
            return v;
        }
        return defaultValue;
    }

    /// <summary>A Key identifies an option, providing its name, type, and default value.</summary>
    public sealed class Key<T> where T : notnull
    {
        public string Name { get; }
        public T DefaultValue { get; }

        public Key(string name, T defaultValue)
        {
            char prefix = name[0];
            if (prefix == '-' || prefix == '+')
            {
                throw new ArgumentException("Key name must not start with + or -: " + name);
            }
            Name = name;
            DefaultValue = defaultValue ?? throw new ArgumentNullException(nameof(defaultValue));
        }

        public override string ToString() => Name;
    }

    /// <summary>Returns a new builder initially holding the same key/value pairs as this.</summary>
    public Builder ToBuilder() => new(map.ToBuilder());

    /// <summary>Returns a new empty builder.</summary>
    public static Builder NewBuilder() =>
        new(ImmutableSortedDictionary.CreateBuilder<string, object>());

    /// <summary>A mutable container used to construct an immutable StarlarkSemantics.</summary>
    public sealed class Builder
    {
        private readonly ImmutableSortedDictionary<string, object>.Builder map;

        internal Builder(ImmutableSortedDictionary<string, object>.Builder map) => this.map = map;

        public Builder Set<T>(Key<T> key, T value) where T : notnull
        {
            if (!value.Equals(key.DefaultValue))
            {
                map[key.Name] = value;
            }
            else
            {
                map.Remove(key.Name);
            }
            return this;
        }

        public Builder SetBool(string name, bool value)
        {
            char prefix = name[0];
            if (prefix != '+' && prefix != '-')
            {
                throw new ArgumentException("boolean option name must start with + or -: " + name);
            }
            bool defaultValue = prefix == '+';
            if (value != defaultValue)
            {
                map[name] = value;
            }
            else
            {
                map.Remove(name);
            }
            return this;
        }

        public StarlarkSemantics Build() =>
            map.Count == 0 ? DEFAULT : new StarlarkSemantics(map.ToImmutable());
    }

    /// <summary>Returns true if a feature attached to the given toggling flags should be enabled.</summary>
    internal bool IsFeatureEnabledBasedOnTogglingFlags(string enablingFlag, string disablingFlag)
    {
        if (!string.IsNullOrEmpty(enablingFlag) && !string.IsNullOrEmpty(disablingFlag))
        {
            throw new ArgumentException("at least one of enablingFlag or disablingFlag must be empty");
        }
        if (!string.IsNullOrEmpty(enablingFlag))
        {
            return GetBool(enablingFlag);
        }
        if (!string.IsNullOrEmpty(disablingFlag))
        {
            return !GetBool(disablingFlag);
        }
        return true;
    }

    /// <summary>Returns a possibly different equivalent instance for caching purposes.</summary>
    public virtual StarlarkSemantics GetBuiltinManagerCacheKey() => this;

    public override int GetHashCode() => hashCode;

    public override bool Equals(object? that) =>
        ReferenceEquals(this, that)
        || (that is StarlarkSemantics s && DictionaryEquals(map, s.map));

    private static bool DictionaryEquals(
        ImmutableSortedDictionary<string, object> a, ImmutableSortedDictionary<string, object> b)
    {
        if (a.Count != b.Count)
        {
            return false;
        }
        foreach (var e in a)
        {
            if (!b.TryGetValue(e.Key, out object? v) || !Equals(e.Value, v))
            {
                return false;
            }
        }
        return true;
    }

    public override string ToString()
    {
        var buf = new StringBuilder("StarlarkSemantics{");
        string sep = "";
        foreach (var e in map)
        {
            buf.Append(sep);
            sep = ", ";
            string key = e.Key;
            buf.Append(key[0] == '+' || key[0] == '-' ? key[1..] : key);
            buf.Append('=').Append(e.Value);
        }
        return buf.Append('}').ToString();
    }

    // -- semantics options affecting the Starlark interpreter itself --

    public const string PRINT_TEST_MARKER = "-print_test_marker";
    public const string ALLOW_RECURSION = "-allow_recursion";
    public const string EXPERIMENTAL_ENABLE_STARLARK_SET = "+experimental_enable_starlark_set";
    public const string INTERNAL_BAZEL_ONLY_UTF_8_BYTE_STRINGS =
        "-internal_bazel_only_utf_8_byte_strings";
    public const string EXPERIMENTAL_STARLARK_STATIC_TYPE_CHECKING =
        "-experimental_starlark_static_type_checking";
    public const string EXPERIMENTAL_STARLARK_DYNAMIC_TYPE_CHECKING =
        "-experimental_starlark_dynamic_type_checking";
    public const string FORCE_STARLARK_STACK_TRACE = "-force_starlark_stack_trace";
}
