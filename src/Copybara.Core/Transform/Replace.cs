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
using System.Text;
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.TemplateToken;
using Copybara.TreeState;
using Copybara.Util;
using Starlark.Eval;
using Starlark.Syntax;
using StarlarkRt = Starlark.Eval.Starlark;
using FileState = Copybara.TreeState.TreeState.FileState;
using Replacer = Copybara.TemplateToken.RegexTemplateTokens.Replacer;

namespace Copybara.Transform;

/// <summary>
/// A source code transformation which replaces a regular expression with some other string.
///
/// <para>The replacement is defined as two strings with interpolations and a mapping of
/// interpolation names to regular expressions.</para>
///
/// <para>This transformation is line-based and only replaces the first instance of the pattern on a
/// line.</para>
/// </summary>
public sealed class Replace : ITransformation
{
    private readonly RegexTemplateTokens _before;
    private readonly RegexTemplateTokens _after;
    private readonly ImmutableDictionary<string, Regex> _regexGroups;
    private readonly bool _firstOnly;
    private readonly bool _multiline;
    private readonly bool _repeatedGroups;
    private readonly Glob _paths;
    private readonly ImmutableArray<Regex> _patternsToIgnore;
    private readonly WorkflowOptions _workflowOptions;
    private readonly Location _location;

    private Replace(
        RegexTemplateTokens before,
        RegexTemplateTokens after,
        IReadOnlyDictionary<string, Regex> regexGroups,
        bool firstOnly,
        bool multiline,
        bool repeatedGroups,
        Glob paths,
        IReadOnlyList<Regex> patternsToIgnore,
        WorkflowOptions workflowOptions,
        Location location)
    {
        _before = Preconditions.CheckNotNull(before);
        _after = Preconditions.CheckNotNull(after);
        _regexGroups = regexGroups.ToImmutableDictionary();
        _firstOnly = firstOnly;
        _multiline = multiline;
        _repeatedGroups = repeatedGroups;
        _paths = Preconditions.CheckNotNull(paths);
        _patternsToIgnore = patternsToIgnore.ToImmutableArray();
        _workflowOptions = Preconditions.CheckNotNull(workflowOptions);
        _location = Preconditions.CheckNotNull(location);
    }

    public override string ToString() =>
        $"Replace{{before={_before}, after={_after}, regexGroups=[{string.Join(", ", _regexGroups.Keys)}],"
        + $" firstOnly={_firstOnly}, multiline={_multiline}, path={_paths},"
        + $" patternsToIgnore=[{string.Join(", ", _patternsToIgnore.Select(p => p.ToString()))}],"
        + $" location={_location}}}";

    public TransformationStatus Transform(TransformWork work)
    {
        work.GetConsole().VerboseFmt("Running Replace {0}", this);
        if (Regex.IsMatch("", _before.GetBefore().ToString()) && !_firstOnly)
        {
            work.GetConsole().WarnFmt(
                "Replace {0} matches the empty String, this is likely to cause unintended behavior,"
                    + " unless it is a no-op.",
                this);
        }
        string checkoutDir = work.GetCheckoutDir();

        var files = work.GetTreeState().Find(_paths.RelativeTo(checkoutDir)).ToList();
        var batchReplace = new BatchReplace(CreateReplacer, _before.GetBefore().ToString());
        _workflowOptions.Parallelizer().Run(files, batchReplace);
        var changed = batchReplace.GetChanged();
        bool matchedFile = batchReplace.IsMatchedFile();

        work.GetTreeState().NotifyModify(changed);
        if (changed.Count == 0)
        {
            return TransformationStatus.Noop(
                "Transformation '" + ToString() + "' was a no-op because it didn't "
                    + (matchedFile ? "change any of the matching files" : "match any file"));
        }
        return TransformationStatus.Success();
    }

    public string Describe() =>
        // before should be almost always unique so it is good enough for identifying the transform.
        "Replace " + _before;

    public ITransformation Reverse()
    {
        try
        {
            _after.ValidateUnused();
        }
        catch (EvalException e)
        {
            throw new NonReversibleValidationException(
                "The transformation is not automatically reversible. Add an explicit reversal field"
                    + " with core.transform: " + e.Message,
                e.InnerException);
        }
        return new Replace(
            _after, _before, _regexGroups, _firstOnly, _multiline, _repeatedGroups,
            _paths, _patternsToIgnore, _workflowOptions, _location);
    }

    public static Replace Create(
        Location location,
        string before,
        string after,
        IReadOnlyDictionary<string, string> regexGroups,
        Glob paths,
        bool firstOnly,
        bool multiline,
        bool repeatedGroups,
        IReadOnlyList<string> patternsToIgnore,
        WorkflowOptions workflowOptions)
    {
        var parsedGroups = ParsePatterns(regexGroups);

        var beforeTokens = new RegexTemplateTokens(before, parsedGroups, repeatedGroups, location);
        var afterTokens = new RegexTemplateTokens(after, parsedGroups, repeatedGroups, location);

        beforeTokens.ValidateUnused();

        var parsedIgnorePatterns = new List<Regex>();
        foreach (string toIgnore in patternsToIgnore)
        {
            try
            {
                parsedIgnorePatterns.Add(new Regex(toIgnore));
            }
            catch (ArgumentException)
            {
                throw StarlarkRt.Errorf("'patterns_to_ignore' includes invalid regex: {0}", toIgnore);
            }
        }

        // Don't validate non-used interpolations in after since they are only relevant for
        // reversible transformations.
        return new Replace(
            beforeTokens, afterTokens, parsedGroups, firstOnly, multiline, repeatedGroups, paths,
            parsedIgnorePatterns, workflowOptions, location);
    }

    public static IReadOnlyDictionary<string, Regex> ParsePatterns(
        IReadOnlyDictionary<string, string> regexGroups)
    {
        var parsedGroups = new Dictionary<string, Regex>();
        foreach (var group in regexGroups)
        {
            try
            {
                parsedGroups[group.Key] = new Regex(group.Value);
            }
            catch (ArgumentException)
            {
                throw StarlarkRt.Errorf(
                    "'regex_groups' includes invalid regex for key {0}: {1}",
                    group.Key, group.Value);
            }
        }
        return parsedGroups;
    }

    private sealed class BatchReplace : LocalParallelizer.TransformFunc<FileState, bool>
    {
        private readonly Func<Replacer> _replacerSupplier;
        private readonly List<FileState> _changed = new();
        private bool _matchedFile;
        private readonly bool _emptyBefore;
        private readonly object _lock = new();

        public BatchReplace(Func<Replacer> replacerSupplier, string before)
        {
            _replacerSupplier = Preconditions.CheckNotNull(replacerSupplier);
            _emptyBefore = before.Length == 0;
        }

        public List<FileState> GetChanged() => _changed;

        public bool IsMatchedFile() => _matchedFile;

        public bool Run(IEnumerable<FileState> elements)
        {
            Replacer replacer = _replacerSupplier();
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
                byte[] bytes = File.ReadAllBytes(file.GetPath());
                if (bytes.Length > int.MaxValue >> 1)
                {
                    throw new ValidationException(
                        $"Cannot read file '{file.GetPath()}' because it is too big for"
                            + " core.replace(). You can exclude running for this file by adding"
                            + " core.replace(..., paths = glob(['**'], exclude ="
                            + " ['big/file/path'])). another option, if the file is not needed, is"
                            + " to exclude it in origin_files.");
                }
                string originalFileContent = Encoding.UTF8.GetString(bytes);

                if (!replacer.IsFirstOnly() && _emptyBefore && originalFileContent.Length > 10_000)
                {
                    throw new ValidationException(
                        "Error trying to replace empty string with text on a big file, this usually"
                            + " happens if you use the transform"
                            + " core.replace(before = '', after = 'some text') or, more commonly,"
                            + " when a you have a transform like core.replace(before = 'some text',"
                            + " after = '') and is reversed in another workflow. The effect of this"
                            + " transform is not what you want, as it will replace every single"
                            + " character with 'some text'. In the case of the reverse, the fix is"
                            + " to either wrap the core.replace in: core.transform([core.replace"
                            + "(...)], reversal =[]) so that it doesn't do anything on the reversal"
                            + " or, even better, to use a reversible scrubber like"
                            + " core.replace(before = 'confidential text', after = 'some text that"
                            + " is safe to be public'): " + replacer.GetLocation());
                }
                string transformed = replacer.Replace(originalFileContent);
                if (!originalFileContent.Equals(transformed))
                {
                    changed.Add(file);
                    File.WriteAllBytes(file.GetPath(), Encoding.UTF8.GetBytes(transformed));
                }
            }
            lock (_lock)
            {
                _matchedFile |= matchedFile;
                _changed.AddRange(changed);
            }
            // We cannot return null here.
            return true;
        }
    }

    public Replacer CreateReplacer() =>
        _before.CreateReplacer(_after, _firstOnly, _multiline, _patternsToIgnore);

    public Glob GetPaths() => _paths;

    public Location Location() => _location;
}
