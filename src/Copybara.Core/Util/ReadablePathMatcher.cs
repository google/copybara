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

namespace Copybara.Util;

/// <summary>
/// A path matcher which delegates to another path matcher but has a specifiable
/// <see cref="ToString"/> value. Port of <c>com.google.copybara.util.ReadablePathMatcher</c>.
/// </summary>
public sealed class ReadablePathMatcher : IPathMatcher, IEquatable<ReadablePathMatcher>
{
    private readonly IPathMatcher _delegate;
    private readonly string _toString;

    public ReadablePathMatcher(IPathMatcher @delegate, string toString)
    {
        _delegate = @delegate;
        _toString = toString;
    }

    public bool Matches(string path) => _delegate.Matches(path);

    public override string ToString() => _toString;

    /// <summary>
    /// Creates a <see cref="IPathMatcher"/> based on a glob relative to <paramref name="path"/>. The
    /// string representation of the matcher is the actual glob.
    ///
    /// <para>For example a glob "dir/**.java" would match any java file inside the {path}/dir
    /// directory.</para>
    /// </summary>
    public static ReadablePathMatcher RelativeGlob(string path, string glob)
    {
        FileUtil.CheckNormalizedRelative(glob);

        string root = PathNormalizer.Normalize(path);
        if (root.Length > 0 && !root.EndsWith('/'))
        {
            root += "/";
        }

        return new ReadablePathMatcher(GlobPathMatcher.Compile(root + glob), glob);
    }

    public bool Equals(ReadablePathMatcher? other) =>
        // Don't use the delegate as toString is unique.
        other is not null && _toString == other._toString;

    public override bool Equals(object? obj) => Equals(obj as ReadablePathMatcher);

    public override int GetHashCode() => _toString.GetHashCode();
}
