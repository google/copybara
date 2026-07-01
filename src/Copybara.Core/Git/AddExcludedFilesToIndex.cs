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

using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// A walker which adds all files not matching a glob to the index of a Git repo using
/// <c>git add</c>. Port of <c>com.google.copybara.git.AddExcludedFilesToIndex</c>.
/// </summary>
internal sealed class AddExcludedFilesToIndex
{
    private static readonly Regex SubmoduleStatusPrefix = new("^-[0-9a-f]{40,64} ", RegexOptions.Compiled);

    private readonly GitRepository _repo;
    private readonly IPathMatcher _pathMatcher;
    private readonly string _workTree;
    private List<string>? _addBackSubmodules;

    // Relative paths (using '/' separators) to add back to the index. Sorted for stable batching.
    private readonly SortedSet<string> _toExclude = new(StringComparer.Ordinal);

    internal AddExcludedFilesToIndex(GitRepository repo, IPathMatcher pathMatcher)
    {
        _repo = Preconditions.CheckNotNull(repo);
        _workTree = Preconditions.CheckNotNull(repo.GetWorkTree());
        _pathMatcher = Preconditions.CheckNotNull(pathMatcher);
    }

    internal void Prepare(string workdir)
    {
        var included = new HashSet<string>(StringComparer.Ordinal);
        var prevExcluded = new List<string>();
        IReadOnlyList<GitRepository.TreeElement> head;
        try
        {
            head = _repo.LsTree(_repo.ResolveReference("HEAD"), null, true, true);
        }
        catch (CannotResolveRevisionException)
        {
            // Destination is empty. Nothing to revert.
            return;
        }
        foreach (var treeElement in head)
        {
            string relative = Normalize(treeElement.Path);
            if (_pathMatcher.Matches(Path.Combine(_workTree, treeElement.Path)))
            {
                AddPathAndParents(included, relative);
            }
            else
            {
                prevExcluded.Add(relative);
                if (IsHidden(relative))
                {
                    // File is not included but 'git add dir' doesn't work for 'dir/.file'.
                    AddPathAndParents(included, Parent(relative));
                }
            }
        }

        foreach (var file in EnumerateRelativeFiles(workdir))
        {
            AddPathAndParents(included, file);
        }

        foreach (var path in prevExcluded)
        {
            var segments = path.Split('/');
            string search = "";
            foreach (var segment in segments)
            {
                search = search.Length == 0 ? segment : search + "/" + segment;
                if (search == path)
                {
                    _toExclude.Add(search);
                    break;
                }
                if (!included.Contains(search))
                {
                    _toExclude.Add(search);
                    break;
                }
            }
        }
    }

    private static void AddPathAndParents(HashSet<string> included, string? path)
    {
        while (!string.IsNullOrEmpty(path) && !included.Contains(path))
        {
            Preconditions.CheckArgument(!Path.IsPathRooted(path));
            included.Add(path);
            path = Parent(path);
        }
    }

    /// <summary>
    /// Finds and records the path of all submodules. This should be called when they are not staged
    /// for deletion.
    /// </summary>
    internal void FindSubmodules(Console console)
    {
        _addBackSubmodules = new List<string>();

        string submoduleStatus = _repo.SimpleCommand("submodule", "status").GetStdout();
        foreach (var line in submoduleStatus.Split('\n'))
        {
            if (line.Length == 0)
            {
                continue;
            }
            string submoduleName = SubmoduleStatusPrefix.Replace(line, "", 1);
            if (submoduleName == line)
            {
                console.Warn("Cannot parse line from 'git submodule status': " + line);
                continue;
            }
            if (!_pathMatcher.Matches(Path.Combine(_workTree, submoduleName)))
            {
                _addBackSubmodules.Add(submoduleName);
            }
        }
    }

    /// <summary>Adds all the excluded files and submodules.</summary>
    internal void Add()
    {
        int size = 0;
        var current = new List<string>();
        foreach (var path in _toExclude)
        {
            current.Add(path);
            size += path.Length;
            // Split the executions in chunks of 6K. 8K triggers arg max in some systems.
            if (size > 6 * 1024)
            {
                _repo.Add().Force().Files(current).Run();
                current = new List<string>();
                size = 0;
            }
        }
        if (current.Count != 0)
        {
            _repo.Add().Force().Files(current).Run();
        }

        foreach (var addBackSubmodule in _addBackSubmodules ?? new List<string>())
        {
            _repo.SimpleCommand("reset", "--", "--quiet", addBackSubmodule);
            _repo.Add().Force().Files(new[] { addBackSubmodule }).Run();
        }
    }

    private IEnumerable<string> EnumerateRelativeFiles(string workdir)
    {
        if (!Directory.Exists(workdir))
        {
            yield break;
        }
        foreach (var file in Directory.EnumerateFiles(
                     workdir, "*", SearchOption.AllDirectories))
        {
            yield return Normalize(Path.GetRelativePath(workdir, file));
        }
    }

    private static string Normalize(string path) => path.Replace('\\', '/');

    private static string? Parent(string path)
    {
        int idx = path.LastIndexOf('/');
        return idx < 0 ? null : path.Substring(0, idx);
    }

    private static bool IsHidden(string relativePath)
    {
        int idx = relativePath.LastIndexOf('/');
        string name = idx < 0 ? relativePath : relativePath.Substring(idx + 1);
        return name.StartsWith('.');
    }
}
