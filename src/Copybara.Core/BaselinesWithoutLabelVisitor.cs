/*
 * Copyright (C) 2018 Google Inc.
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
using Copybara.Revision;
using Copybara.Util;

namespace Copybara;

/// <summary>A visitor that finds all the parents that match the origin glob.</summary>
public class BaselinesWithoutLabelVisitor<T> : IChangesVisitor
{
    private readonly List<T> _result = new();
    private readonly int _limit;
    private readonly Glob _originFiles;
    private bool _skipFirst;
    private readonly IRevision? _toSkip;

    public BaselinesWithoutLabelVisitor(Glob originFiles, int limit, IRevision? toSkip, bool skipFirst)
    {
        _originFiles = Preconditions.CheckNotNull(originFiles);
        Preconditions.CheckArgument(limit > 0);
        _limit = limit;
        _toSkip = toSkip;
        _skipFirst = skipFirst;
    }

    public IReadOnlyList<T> GetResult() => _result.ToImmutableArray();

    public VisitResult Visit(Change<IRevision> change)
    {
        if (_skipFirst || (_toSkip != null && change.GetRevision().Equals(_toSkip)))
        {
            _skipFirst = false;
            return VisitResult.Continue;
        }
        var files = change.GetChangeFiles();
        if (Glob.AffectsRoots(_originFiles.Roots(), files))
        {
            _result.Add((T)(object)change.GetRevision());
            return _result.Count < _limit ? VisitResult.Continue : VisitResult.Terminate;
        }
        // This change only contains files that are not exported
        return VisitResult.Continue;
    }
}
