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

using Copybara.Util;

namespace Copybara.Transform;

/// <summary>A visitor which copy or moves files recursively from the path it is visiting.</summary>
internal sealed class CopyMoveVisitor
{
    private readonly string _before;
    private readonly string _after;
    private readonly IPathMatcher? _pathMatcher;
    private readonly bool _isCopy;
    private readonly bool _overwrite;

    public CopyMoveVisitor(
        string before, string after, IPathMatcher? pathMatcher, bool overwrite, bool isCopy)
    {
        _before = PathOps.Normalize(before);
        _after = PathOps.Normalize(after);
        _pathMatcher = pathMatcher;
        _isCopy = isCopy;
        _overwrite = overwrite;
    }

    /// <summary>Walks the <c>before</c> tree, copying/moving matched files into <c>after</c>.</summary>
    public void Walk()
    {
        if (!Directory.Exists(_before))
        {
            // 'before' may be a single file.
            if (File.Exists(_before))
            {
                VisitFile(_before);
            }
            return;
        }
        WalkDir(_before);
    }

    private void WalkDir(string dir)
    {
        // Mirror preVisitDirectory: skip the 'after' subtree.
        if (PathOps.Normalize(dir) == _after)
        {
            return;
        }
        foreach (var sub in Directory.EnumerateDirectories(dir))
        {
            WalkDir(sub);
        }
        foreach (var file in Directory.EnumerateFiles(dir))
        {
            VisitFile(file);
        }
    }

    private void VisitFile(string source)
    {
        if (_pathMatcher == null || _pathMatcher.Matches(source))
        {
            string relative = PathOps.Relativize(_before, source);
            string dest = PathOps.Resolve(_after, relative);
            var destParent = PathOps.GetParent(dest);
            if (destParent != null)
            {
                Directory.CreateDirectory(destParent);
            }
            if (File.Exists(dest))
            {
                if (!_overwrite)
                {
                    throw new IOException($"Cannot move file to '{dest}' because it already exists");
                }
                File.Delete(dest);
            }
            if (_isCopy)
            {
                File.Copy(source, dest, overwrite: _overwrite);
            }
            else
            {
                File.Move(source, dest);
            }
        }
    }
}
