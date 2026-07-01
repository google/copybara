/*
 * Copyright (C) 2019 Google Inc.
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
using System.Text;
using System.Text.RegularExpressions;
using Copybara.Exceptions;
using Copybara.TreeState;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Syntax;
using FileState = Copybara.TreeState.TreeState.FileState;

namespace Copybara.Transform;

// Module needed because both Transformation and ReversibleFunction are Starlark objects but neither
// of them extend each other.

/// <summary>A core.filter_replace transformation.</summary>
[StarlarkBuiltin("filter_replace", Doc = "A core.filter_replace transformation")]
public class FilterReplace : ITransformation, IReversibleFunction<string, string>
{
    private readonly WorkflowOptions _workflowOptions;
    private readonly Regex _before;
    private readonly Regex? _after;
    private readonly int _group;
    private readonly int _reverseGroup;
    private readonly IReversibleFunction<string, string> _mapping;
    private readonly Glob _glob;
    private readonly Location _location;

    public FilterReplace(
        WorkflowOptions workflowOptions,
        Regex before,
        Regex? after,
        int group,
        int reverseGroup,
        IReversibleFunction<string, string> mapping,
        Glob glob,
        Location location)
    {
        _workflowOptions = workflowOptions;
        _before = before;
        _after = after;
        _group = group;
        _reverseGroup = reverseGroup;
        _mapping = mapping;
        _glob = glob;
        _location = location;
    }

    public TransformationStatus Transform(TransformWork work)
    {
        string checkoutDir = work.GetCheckoutDir();

        var files = work.GetTreeState().Find(_glob.RelativeTo(checkoutDir)).ToList();
        var batchReplace = new BatchReplace(this);
        _workflowOptions.Parallelizer().Run(files, batchReplace);
        var changed = batchReplace.GetChanged();
        bool matchedFile = batchReplace.MatchedFile;

        work.GetTreeState().NotifyModify(changed);
        if (changed.Count == 0)
        {
            return TransformationStatus.Noop(
                "Transformation '" + ToString() + "' was a no-op because it didn't "
                    + (matchedFile ? "change any of the matching files" : "match any file"));
        }
        return TransformationStatus.Success();
    }

    public ITransformation Reverse() => InternalReverse();

    private FilterReplace InternalReverse()
    {
        if (_after == null)
        {
            throw new NonReversibleValidationException("No 'after' defined");
        }

        return new FilterReplace(
            _workflowOptions, _after, _before, _reverseGroup, _group, _mapping.ReverseMapping(),
            _glob, _location);
    }

    public string Describe() => "Nested replaceString";

    public Location Location() => _location;

    public string Apply(string s) => ReplaceString(s);

    public IReversibleFunction<string, string> ReverseMapping() => InternalReverse();

    private sealed class BatchReplace : LocalParallelizer.TransformFunc<FileState, bool>
    {
        private readonly FilterReplace _owner;
        private readonly List<FileState> _changed = new();
        private readonly object _lock = new();
        public bool MatchedFile { get; private set; }

        public BatchReplace(FilterReplace owner)
        {
            _owner = owner;
        }

        public List<FileState> GetChanged() => _changed;

        public bool Run(IEnumerable<FileState> elements)
        {
            var changed = new List<FileState>();
            bool matchedFile = false;
            foreach (FileState file in elements)
            {
                var fileInfo = new FileInfo(file.GetPath());
                if (fileInfo.LinkTarget != null)
                {
                    continue;
                }
                matchedFile = true;
                string originalContent = Encoding.UTF8.GetString(File.ReadAllBytes(file.GetPath()));
                string transformed = _owner.ReplaceString(originalContent);
                // ReplaceString returns the same instance if no replacement happens. This avoids
                // comparing the whole file content.
                if (ReferenceEquals(transformed, originalContent))
                {
                    continue;
                }
                changed.Add(file);
                File.WriteAllBytes(file.GetPath(), Encoding.UTF8.GetBytes(transformed));
            }

            lock (_lock)
            {
                MatchedFile |= matchedFile;
                _changed.AddRange(changed);
            }
            // We cannot return null here.
            return true;
        }
    }

    private string ReplaceString(string originalContent)
    {
        bool anyReplace = false;
        var result = new StringBuilder(originalContent.Length);
        int lastAppend = 0;
        foreach (Match matcher in _before.Matches(originalContent))
        {
            Group g = matcher.Groups[_group];
            // Append text between the previous match end and this match start.
            result.Append(originalContent, lastAppend, matcher.Index - lastAppend);
            if (!g.Success)
            {
                result.Append(matcher.Value);
                lastAppend = matcher.Index + matcher.Length;
                continue;
            }
            string val = g.Value;
            string res = _mapping.Apply(val);
            anyReplace |= !val.Equals(res);
            if (_group == 0)
            {
                result.Append(res);
            }
            else
            {
                string prefix = originalContent.Substring(matcher.Index, g.Index - matcher.Index);
                string suffix = originalContent.Substring(
                    g.Index + g.Length, matcher.Index + matcher.Length - (g.Index + g.Length));
                result.Append(prefix).Append(res).Append(suffix);
            }
            lastAppend = matcher.Index + matcher.Length;
        }

        if (!anyReplace)
        {
            return originalContent;
        }

        result.Append(originalContent, lastAppend, originalContent.Length - lastAppend);
        return result.ToString();
    }

    public override string ToString() => $"FilterReplace{{before={_before}}}";
}
