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

using Copybara.Common;
using Copybara.Util;

namespace Copybara.TreeState;

/// <summary>
/// An object that allows to do potentially cached filesystem lookups.
///
/// <para>In particular, if a transform does lookups (using find) and then notifies the affected
/// files, the next transform gets a cached version of the TreeState.</para>
/// </summary>
public class TreeState
{
    /// <summary>
    /// An object that contains a path found in the <see cref="TreeState"/>.
    ///
    /// <para>Wrapped so that we can include things like the hash of the file in the future.</para>
    /// </summary>
    public sealed class FileState : IEquatable<FileState>
    {
        private readonly string _path;

        public FileState(string path)
        {
            _path = Preconditions.CheckNotNull(path);
        }

        public string GetPath() => _path;

        public bool Equals(FileState? other) => other != null && _path == other._path;

        public override bool Equals(object? o) => o is FileState fs && Equals(fs);

        public override int GetHashCode() => _path.GetHashCode();

        public override string ToString() => _path;
    }

    private readonly string _checkoutDir;
    private bool _isCached;
    private bool _notified;
    private Dictionary<string, FileState> _files = new();

    // Small LRU-ish cache of matches keyed by matcher, mirroring the Guava LoadingCache (max 10).
    private readonly Dictionary<IPathMatcher, List<FileState>> _cachedMatches = new();

    public TreeState(string checkoutDir)
    {
        _checkoutDir = checkoutDir;
    }

    /// <summary>Find a set of files in the checkout dir, using a <see cref="IPathMatcher"/>.</summary>
    public IEnumerable<FileState> Find(IPathMatcher pathMatcher)
    {
        if (!_isCached)
        {
            _files = ReadFileSystem();
            _isCached = true;
        }
        if (!_cachedMatches.TryGetValue(pathMatcher, out var matches))
        {
            matches = Filter(pathMatcher, _files.Values);
            if (_cachedMatches.Count >= 10)
            {
                _cachedMatches.Clear();
            }
            _cachedMatches[pathMatcher] = matches;
        }
        return matches;
    }

    private static List<FileState> Filter(IPathMatcher pathMatcher, IEnumerable<FileState> files)
    {
        var result = new List<FileState>();
        foreach (var file in files)
        {
            if (pathMatcher.Matches(file.GetPath()))
            {
                result.Add(file);
            }
        }
        return result;
    }

    private Dictionary<string, FileState> ReadFileSystem()
    {
        var result = new Dictionary<string, FileState>();
        if (!Directory.Exists(_checkoutDir))
        {
            return result;
        }
        foreach (var file in Directory.EnumerateFiles(_checkoutDir, "*", SearchOption.AllDirectories))
        {
            result[file] = new FileState(file);
        }
        return result;
    }

    /// <summary>Notify the <see cref="TreeState"/> that <paramref name="paths"/> have been modified.</summary>
    public void NotifyModify(IEnumerable<FileState> paths)
    {
        _notified = true;
        foreach (var path in paths)
        {
            _files[path.GetPath()] = path;
        }
    }

    /// <summary>Not implemented for now.</summary>
    public void NotifyAdd(IEnumerable<FileState> path) =>
        throw new NotSupportedException("Not supported. Don't notify!");

    /// <summary>Not implemented for now.</summary>
    public void NotifyDelete(IEnumerable<FileState> path) =>
        throw new NotSupportedException("Not supported. Don't notify!");

    public void NotifyNoChange() => _notified = true;

    public bool IsCached() => _isCached;

    public void ClearCache()
    {
        _isCached = false;
        _files = new Dictionary<string, FileState>();
        _cachedMatches.Clear();
        _notified = false;
    }

    /// <summary>
    /// If any of the notify* methods were invoked, it will retain the cached version of the
    /// TreeState. Otherwise it clears the cache.
    ///
    /// <para>This method is called in between every pair of Transformations. Unless the previous
    /// Transformation calls one of the notify* methods to indicate which files it has touched, we
    /// must assume that the cache may be stale.</para>
    /// </summary>
    public void MaybeClearCache()
    {
        if (!_notified)
        {
            ClearCache();
        }
        _notified = false;
    }
}
