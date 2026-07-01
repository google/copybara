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
using Copybara.TemplateToken;
using Copybara.Util;

namespace Copybara.Transform;

/// <summary>A visitor which copy or moves files recursively from the path it is visiting.</summary>
internal sealed class CopyMoveRegexVisitor
{
    private readonly RegexTemplateTokens _before;
    private readonly RegexTemplateTokens _after;
    private readonly IPathMatcher _pathMatcher;
    private readonly string _workDir;
    private readonly bool _isCopy;
    private readonly bool _overwrite;

    private readonly List<IAction> _actionsToTake = new();

    private CopyMoveRegexVisitor(
        RegexTemplateTokens before,
        RegexTemplateTokens after,
        IPathMatcher pathMatcher,
        string workDir,
        bool overwrite,
        bool isCopy)
    {
        _before = Preconditions.CheckNotNull(before);
        _after = Preconditions.CheckNotNull(after);
        _pathMatcher = Preconditions.CheckNotNull(pathMatcher);
        _workDir = Preconditions.CheckNotNull(workDir);
        _isCopy = isCopy;
        _overwrite = overwrite;
    }

    private void VisitFile(string file)
    {
        if (_pathMatcher.Matches(file))
        {
            string relativeFile = PathOps.Relativize(_workDir, file);
            string relativeDest = _before
                .CreateReplacer(_after, firstOnly: true, multiline: false, ImmutableArray<System.Text.RegularExpressions.Regex>.Empty)
                .Replace(relativeFile);
            if (relativeFile.Equals(relativeDest))
            {
                // Either the regex didn't match, or it did match but returned the same file name.
                return;
            }
            string dest = PathOps.Resolve(_workDir, relativeDest);
            _actionsToTake.Add(new CopyOrMoveAction(this, file, dest));
        }
    }

    private void WalkDir(string dir)
    {
        foreach (var sub in Directory.EnumerateDirectories(dir))
        {
            WalkDir(sub);
        }
        foreach (var file in Directory.EnumerateFiles(dir))
        {
            VisitFile(file);
        }
        // postVisitDirectory: schedule dir deletion for moves.
        if (!_isCopy)
        {
            _actionsToTake.Add(new DeleteDirectoryAction(dir));
        }
    }

    public static bool Run(
        string root,
        RegexTemplateTokens before,
        RegexTemplateTokens after,
        IPathMatcher pathMatcher,
        string workDir,
        bool overwrite,
        bool isCopy)
    {
        var visitor = new CopyMoveRegexVisitor(before, after, pathMatcher, workDir, overwrite, isCopy);
        visitor.WalkDir(root);

        // Start to execute actions only after we finish walking the tree, to make sure we don't
        // copy/move the same file twice.
        bool someActionSucceeded = false;
        foreach (IAction action in visitor._actionsToTake)
        {
            someActionSucceeded |= action.Run();
        }
        return someActionSucceeded;
    }

    private interface IAction
    {
        bool Run();
    }

    private sealed class CopyOrMoveAction : IAction
    {
        private readonly CopyMoveRegexVisitor _owner;
        private readonly string _file;
        private readonly string _dest;

        public CopyOrMoveAction(CopyMoveRegexVisitor owner, string file, string dest)
        {
            _owner = owner;
            _file = file;
            _dest = dest;
        }

        public bool Run()
        {
            var destParent = PathOps.GetParent(_dest);
            if (destParent != null)
            {
                Directory.CreateDirectory(destParent);
            }
            if (File.Exists(_dest))
            {
                if (!_owner._overwrite)
                {
                    throw new IOException(
                        $"Cannot move file to '{_dest}' because it already exists");
                }
                File.Delete(_dest);
            }
            if (_owner._isCopy)
            {
                File.Copy(_file, _dest, overwrite: _owner._overwrite);
            }
            else
            {
                File.Move(_file, _dest);
            }
            return true;
        }
    }

    private sealed class DeleteDirectoryAction : IAction
    {
        private readonly string _dir;

        public DeleteDirectoryAction(string dir)
        {
            _dir = dir;
        }

        public bool Run()
        {
            try
            {
                if (Directory.Exists(_dir) &&
                    !Directory.EnumerateFileSystemEntries(_dir).Any())
                {
                    Directory.Delete(_dir);
                    return true;
                }
                return false;
            }
            catch (IOException)
            {
                return false;
            }
        }
    }
}
