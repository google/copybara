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
using Copybara.Exceptions;
using Copybara.Util;

namespace Copybara.Transform;

/// <summary>
/// A visitor which recursively verifies there are no files or symlinks in a directory tree.
/// </summary>
internal sealed class VerifyDirIsEmptyVisitor
{
    private readonly string _root;
    private readonly IPathMatcher? _pathMatcher;
    private readonly List<string> _existingFiles = new();

    public VerifyDirIsEmptyVisitor(string root, IPathMatcher? pathMatcher)
    {
        _root = Preconditions.CheckNotNull(root);
        _pathMatcher = pathMatcher;
    }

    public void Walk()
    {
        if (Directory.Exists(_root))
        {
            foreach (var source in Directory.EnumerateFiles(_root, "*", SearchOption.AllDirectories))
            {
                string relative = PathOps.Relativize(_root, source);
                if (_pathMatcher == null || _pathMatcher.Matches(relative))
                {
                    _existingFiles.Add(relative);
                }
            }
        }
        if (_existingFiles.Count != 0)
        {
            _existingFiles.Sort(StringComparer.Ordinal);
            throw new ValidationException(
                $"Files already exist in {_root}: [{string.Join(", ", _existingFiles)}]");
        }
    }
}
