/*
 * Copyright (C) 2017 Google Inc.
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
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.TreeState;
using Copybara.Util;
using Starlark.Syntax;
using Console = Copybara.Util.Console.Console;
using FileState = Copybara.TreeState.TreeState.FileState;

namespace Copybara.Transform;

/// <summary>Map Google style TODOs.</summary>
public class TodoReplace : ITransformation
{
    private static readonly Regex SingleUserPattern =
        new(@"^([ \t]*)([^ \t]*)([ \t]*)$");

    private readonly Regex _pattern;
    private readonly Location _location;
    private readonly Glob _glob;
    private readonly ImmutableArray<string> _todoTags;
    private readonly LocalParallelizer _parallelizer;
    private readonly Mode _mode;
    private readonly ImmutableDictionary<string, string> _mapping;
    private readonly string? _defaultString;
    private readonly Regex? _regexIgnorelist;

    public TodoReplace(
        Location location,
        Glob glob,
        ImmutableArray<string> todoTags,
        Mode mode,
        IReadOnlyDictionary<string, string> mapping,
        string? defaultString,
        LocalParallelizer parallelizer,
        Regex? regexIgnorelist)
    {
        _location = Preconditions.CheckNotNull(location);
        _glob = Preconditions.CheckNotNull(glob);
        _todoTags = todoTags;
        _parallelizer = parallelizer;
        Preconditions.CheckArgument(!todoTags.IsEmpty);
        _mode = mode;
        _mapping = mapping.ToImmutableDictionary();
        _defaultString = defaultString;
        if (mode == Mode.USE_DEFAULT || mode == Mode.MAP_OR_DEFAULT)
        {
            Preconditions.CheckNotNull(defaultString);
        }
        _regexIgnorelist = regexIgnorelist;
        _pattern = CreatePattern(todoTags);
    }

    private static Regex CreatePattern(ImmutableArray<string> todoTags)
    {
        string joined = string.Join("|", todoTags.Select(Regex.Escape));
        return new Regex("((?:" + joined + ") ?)\\((.*?)\\)");
    }

    public TransformationStatus Transform(TransformWork work)
    {
        work.GetTreeState().NotifyModify(
            _parallelizer
                .Run(
                    work.GetTreeState().Find(_glob.RelativeTo(work.GetCheckoutDir())).ToList(),
                    new BatchRun(this, work.GetConsole()))
                .SelectMany(s => s));
        return TransformationStatus.Success();
    }

    private sealed class BatchRun : LocalParallelizer.TransformFunc<FileState, ISet<FileState>>
    {
        private readonly TodoReplace _owner;
        private readonly Console _console;

        public BatchRun(TodoReplace owner, Console console)
        {
            _owner = owner;
            _console = console;
        }

        public ISet<FileState> Run(IEnumerable<FileState> files) => _owner.Run(files, _console);
    }

    private ISet<FileState> Run(IEnumerable<FileState> files, Console console)
    {
        var modifiedFiles = new HashSet<FileState>();
        var batchPattern = new Regex(_pattern.ToString(), _pattern.Options);
        foreach (FileState file in files)
        {
            var fileInfo = new FileInfo(file.GetPath());
            if (fileInfo.LinkTarget != null)
            {
                continue;
            }
            string content = Encoding.UTF8.GetString(File.ReadAllBytes(file.GetPath()));
            var sb = new StringBuilder();
            bool modified = false;
            int lastAppend = 0;
            foreach (Match matcher in batchPattern.Matches(content))
            {
                sb.Append(content, lastAppend, matcher.Index - lastAppend);
                lastAppend = matcher.Index + matcher.Length;
                if (matcher.Groups[2].Value.Trim().Length == 0)
                {
                    sb.Append(matcher.Value);
                    continue;
                }
                var users = matcher.Groups[2].Value.Split(',').ToList();
                var mappedUsers = MapUsers(users, matcher.Value, file.GetPath(), console);
                modified |= !users.SequenceEqual(mappedUsers);
                string result = matcher.Groups[1].Value;
                if (mappedUsers.Count != 0)
                {
                    result += "(" + string.Join(",", mappedUsers) + ")";
                }
                sb.Append(result);
            }
            sb.Append(content, lastAppend, content.Length - lastAppend);

            if (modified)
            {
                modifiedFiles.Add(file);
                File.WriteAllBytes(file.GetPath(), Encoding.UTF8.GetBytes(sb.ToString()));
            }
        }
        return modifiedFiles;
    }

    private List<string> MapUsers(List<string> users, string rawText, string path, Console console)
    {
        var alreadyAdded = new HashSet<string>();
        var result = new List<string>();
        foreach (string rawUser in users)
        {
            Match matcher = SingleUserPattern.Match(rawUser);
            // Throw VE if the pattern doesn't match and mode is MapOrFail.
            if (!matcher.Success)
            {
                ValidationException.CheckCondition(
                    _mode != Mode.MAP_OR_FAIL,
                    "Unexpected '{0}' doesn't match expected format", rawUser);
                console.WarnFmt("Skipping '{0}' that doesn't match expected format", rawUser);
                continue;
            }
            string prefix = matcher.Groups[1].Value;
            string originUser = matcher.Groups[2].Value;
            string suffix = matcher.Groups[3].Value;
            if (_regexIgnorelist != null && IsFullMatch(_regexIgnorelist, originUser))
            {
                result.Add(prefix + originUser + suffix);
                continue;
            }
            switch (_mode)
            {
                case Mode.MAP_OR_FAIL:
                    ValidationException.CheckCondition(
                        _mapping.ContainsKey(originUser),
                        "Cannot find a mapping '{0}' in '{1}' ({2})", originUser, rawText, path);
                    goto case Mode.MAP_OR_IGNORE;
                case Mode.MAP_OR_IGNORE:
                {
                    string destUser = _mapping.GetValueOrDefault(originUser, originUser);
                    if (alreadyAdded.Add(destUser))
                    {
                        result.Add(prefix + destUser + suffix);
                    }
                    break;
                }
                case Mode.MAP_OR_DEFAULT:
                {
                    string destUser = _mapping.GetValueOrDefault(originUser, _defaultString!);
                    if (alreadyAdded.Add(destUser))
                    {
                        result.Add(prefix + destUser + suffix);
                    }
                    break;
                }
                case Mode.SCRUB_NAMES:
                    break;
                case Mode.USE_DEFAULT:
                    if (alreadyAdded.Add(_defaultString!))
                    {
                        result.Add(prefix + _defaultString + suffix);
                    }
                    break;
            }
        }
        return result;
    }

    private static bool IsFullMatch(Regex regex, string input)
    {
        Match m = regex.Match(input);
        return m.Success && m.Index == 0 && m.Length == input.Length;
    }

    public ITransformation Reverse()
    {
        if (_mode != Mode.MAP_OR_FAIL && _mode != Mode.MAP_OR_IGNORE)
        {
            throw new NonReversibleValidationException(_mode + " mode is not reversible");
        }

        var inverse = new Dictionary<string, string>();
        foreach (var e in _mapping)
        {
            if (inverse.ContainsKey(e.Value))
            {
                throw new NonReversibleValidationException(
                    "Non-reversible mapping: value already present: " + e.Value);
            }
            inverse[e.Value] = e.Key;
        }

        return new TodoReplace(
            _location, _glob, _todoTags, _mode, inverse, _defaultString, _parallelizer,
            _regexIgnorelist);
    }

    public string Describe() => "Replacing [" + string.Join(", ", _todoTags) + "]";

    public Location Location() => _location;

    /// <summary>How to transforms TODOs in code.</summary>
    public enum Mode
    {
        /// <summary>Try to use the mapping and if not found fail.</summary>
        MAP_OR_FAIL,

        /// <summary>Try to use the mapping but ignore if no mapping found.</summary>
        MAP_OR_IGNORE,

        /// <summary>Try to use the mapping and use the default if not found.</summary>
        MAP_OR_DEFAULT,

        /// <summary>Scrub all names from TODOs. Transforms 'TODO(foo)' to 'TODO'.</summary>
        SCRUB_NAMES,

        /// <summary>Replace any TODO(foo, bar) with TODO(default_string).</summary>
        USE_DEFAULT,
    }
}
