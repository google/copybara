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

using System.Collections.Immutable;
using Copybara.Common;

namespace Copybara.Config;

/// <summary>
/// A config file that records the children created from it. Useful for collecting dependencies in
/// dry runs.
/// </summary>
internal sealed class CapturingConfigFile : ConfigFile
{
    private readonly LinkedHashSet<CapturingConfigFile> _children = new();
    private readonly ConfigFile _wrapped;

    internal CapturingConfigFile(ConfigFile config) => _wrapped = Preconditions.CheckNotNull(config);

    public ConfigFile Resolve(string path)
    {
        var resolved = new CapturingConfigFile(_wrapped.Resolve(path));
        _children.Add(resolved);
        return resolved;
    }

    public ImmutableDictionary<string, ConfigFile> ResolveAll(IReadOnlySet<string> paths)
    {
        var result = ImmutableDictionary.CreateBuilder<string, ConfigFile>();
        foreach (var e in _wrapped.ResolveAll(paths))
        {
            var capturingConfigFile = new CapturingConfigFile(e.Value);
            result[e.Key] = capturingConfigFile;
            _children.Add(capturingConfigFile);
        }
        return result.ToImmutable();
    }

    public string Path() => _wrapped.Path();

    public byte[] ReadContentBytes() => _wrapped.ReadContentBytes();

    public string GetIdentifier() => _wrapped.GetIdentifier();

    /// <summary>
    /// Retrieve collected dependencies.
    /// </summary>
    /// <returns>A map from path to the wrapped ConfigFile for each ConfigFile created by this or one
    /// of its descendants. Includes this.</returns>
    internal ImmutableDictionary<string, ConfigFile> GetAllLoadedFiles()
    {
        var map = new Dictionary<string, ConfigFile>();
        GetAllLoadedFiles(map);
        return map.ToImmutableDictionary();
    }

    private void GetAllLoadedFiles(Dictionary<string, ConfigFile> map)
    {
        map[Path()] = _wrapped;
        foreach (var child in _children)
        {
            child.GetAllLoadedFiles(map);
        }
    }

    public override bool Equals(object? otherObject) =>
        otherObject is CapturingConfigFile other
        && other._wrapped.Equals(_wrapped)
        && _children.SetEquals(other._children);

    public override int GetHashCode() => Path().GetHashCode();

    public override string ToString() =>
        $"CapturingConfigFile{{children={_children.Count}, wrapped={_wrapped}}}";

    /// <summary>An insertion-ordered set, mirroring Java's LinkedHashSet semantics.</summary>
    private sealed class LinkedHashSet<T> : IEnumerable<T>
        where T : notnull
    {
        private readonly Dictionary<T, int> _index = new();
        private readonly List<T> _items = new();

        public void Add(T item)
        {
            if (_index.TryAdd(item, _items.Count))
            {
                _items.Add(item);
            }
        }

        public int Count => _items.Count;

        public bool SetEquals(LinkedHashSet<T> other) =>
            _index.Count == other._index.Count && _index.Keys.All(other._index.ContainsKey);

        public IEnumerator<T> GetEnumerator() => _items.GetEnumerator();

        System.Collections.IEnumerator System.Collections.IEnumerable.GetEnumerator() =>
            GetEnumerator();
    }
}
