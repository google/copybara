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

using System.Text;
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.TreeState;
using Copybara.Util;
using Starlark.Syntax;
using StarlarkRt = Starlark.Eval.Starlark;
using FileState = Copybara.TreeState.TreeState.FileState;

namespace Copybara.Transform;

/// <summary>
/// A source code pseudo-transformation which verifies that all specified files satisfy a RegEx.
/// Does not actually transform any code, but will throw errors on failure. Not applied in reversals.
/// </summary>
public sealed class VerifyMatch : ITransformation
{
    private readonly Regex _pattern;
    private readonly bool _verifyNoMatch;
    private readonly bool _alsoOnReversal;
    private readonly Glob _fileMatcherBuilder;
    private readonly LocalParallelizer _parallelizer;
    private readonly Location _location;
    private readonly string? _failureMessage;

    private VerifyMatch(
        Regex pattern,
        bool verifyNoMatch,
        bool alsoOnReversal,
        Glob fileMatcherBuilder,
        string? failureMessage,
        LocalParallelizer parallelizer,
        Location location)
    {
        _pattern = Preconditions.CheckNotNull(pattern);
        _verifyNoMatch = verifyNoMatch;
        _alsoOnReversal = alsoOnReversal;
        _fileMatcherBuilder = Preconditions.CheckNotNull(fileMatcherBuilder);
        _failureMessage = failureMessage;
        _parallelizer = parallelizer;
        _location = Preconditions.CheckNotNull(location);
    }

    public override string ToString() =>
        $"VerifyMatch{{Pattern={_pattern}, verifyNoMatch={_verifyNoMatch},"
        + $" also_on_reversal={_alsoOnReversal}, path={_fileMatcherBuilder}}}";

    public TransformationStatus Transform(TransformWork work)
    {
        string checkoutDir = work.GetCheckoutDir();
        var files = work.GetTreeState().Find(_fileMatcherBuilder.RelativeTo(checkoutDir)).ToList();

        var errors = _parallelizer
            .Run(files, new BatchRun(this, work.GetCheckoutDir()))
            .SelectMany(e => e)
            .ToList();

        int size = 0;
        foreach (string error in errors)
        {
            size++;
            work.GetConsole().Error($"Error validating '{Describe()}': {error}");
        }
        work.GetTreeState().NotifyNoChange();

        ValidationException.CheckCondition(
            size == 0,
            "{0} file(s) failed the validation of {1}, located at {2}.", size, Describe(), _location);

        return TransformationStatus.Success();
    }

    private sealed class BatchRun : LocalParallelizer.TransformFunc<FileState, List<string>>
    {
        private readonly VerifyMatch _owner;
        private readonly string _checkoutDir;

        public BatchRun(VerifyMatch owner, string checkoutDir)
        {
            _owner = owner;
            _checkoutDir = Preconditions.CheckNotNull(checkoutDir);
        }

        public List<string> Run(IEnumerable<FileState> files)
        {
            var errors = new List<string>();
            var batchPattern = new Regex(_owner._pattern.ToString(), _owner._pattern.Options);
            foreach (FileState file in files)
            {
                var fileInfo = new FileInfo(file.GetPath());
                if (fileInfo.LinkTarget != null)
                {
                    continue;
                }
                string originalFileContent =
                    Encoding.UTF8.GetString(File.ReadAllBytes(file.GetPath()));
                Match matcher = batchPattern.Match(originalFileContent);
                if (_owner._verifyNoMatch == matcher.Success)
                {
                    string error = PathOps.Relativize(_checkoutDir, file.GetPath());
                    if (_owner._verifyNoMatch)
                    {
                        int line = originalFileContent
                            .Substring(0, matcher.Index)
                            .Split('\n')
                            .Length;
                        error += string.Format(
                            " - Unexpected match found at line {0} - '{1}'.\n", line, matcher.Value);
                    }
                    else
                    {
                        error += " - Expected string was not present.\n";
                    }
                    if (_owner._failureMessage != null)
                    {
                        error += _owner._failureMessage + "\n";
                    }
                    errors.Add(error);
                }
            }
            return errors;
        }
    }

    public string Describe() => $"verify_match '{_pattern}'";

    public Location Location() => _location;

    public ITransformation Reverse()
    {
        if (_alsoOnReversal)
        {
            return new ExplicitReversal(this, this);
        }
        return new ExplicitReversal(IntentionalNoop.Instance, this);
    }

    public static VerifyMatch Create(
        Location location,
        string regEx,
        Glob paths,
        bool verifyNoMatch,
        bool alsoOnReversal,
        string? failureMessage,
        LocalParallelizer parallelizer)
    {
        Regex parsed;
        try
        {
            parsed = new Regex(regEx, RegexOptions.Multiline);
        }
        catch (ArgumentException ex)
        {
            throw StarlarkRt.Errorf("Regex '{0}' is invalid: {1}", regEx, ex.Message);
        }
        return new VerifyMatch(
            parsed, verifyNoMatch, alsoOnReversal, paths, failureMessage, parallelizer, location);
    }
}
