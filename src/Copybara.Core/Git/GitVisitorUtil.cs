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

using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Revision;

namespace Copybara.Git;

/// <summary>Utility methods for visiting Git repos. Port of <c>GitVisitorUtil</c>.</summary>
public static class GitVisitorUtil
{
    /// <summary>Visits changes reachable from <paramref name="start"/> in pages.</summary>
    internal static void VisitChanges(
        GitRevision start,
        IChangesVisitor visitor,
        ChangeReader.Builder queryChanges,
        GeneralOptions generalOptions,
        string type,
        int visitChangePageSize)
    {
        Preconditions.CheckNotNull(start);
        int skip = 0;
        bool finished = false;
        using (generalOptions.Profiler().Start(type + "/visit_changes"))
        {
            while (!finished)
            {
                IReadOnlyList<Change<GitRevision>> result;
                using (generalOptions.Profiler().Start($"git_log_{skip}_{visitChangePageSize}"))
                {
                    var changes = queryChanges.SetSkip(skip).SetLimit(visitChangePageSize).Build()
                        .Run(start);
                    result = changes.Reverse().ToList();
                }
                if (result.Count == 0)
                {
                    break;
                }
                skip += result.Count;
                foreach (var current in result)
                {
                    if (visitor.Visit(ToGenericChange(current)) == VisitResult.Terminate)
                    {
                        finished = true;
                        break;
                    }
                }
            }
        }
        if (skip == 0)
        {
            throw new CannotResolveRevisionException(
                "Cannot resolve reference " + start.GetHash());
        }
    }

    /// <summary>
    /// Adapts a <see cref="Change{GitRevision}"/> to a <see cref="Change{IRevision}"/> so it can be
    /// passed to the invariant <see cref="IChangesVisitor"/> interface.
    /// </summary>
    internal static Change<IRevision> ToGenericChange(Change<GitRevision> change)
    {
        var parents = change.GetParents();
        var genericChange = new Change<IRevision>(
            change.GetRevision(),
            change.GetAuthor(),
            change.GetMessage(),
            change.GetDateTime(),
            change.GetLabels(),
            change.GetChangeFiles(),
            change.IsMerge(),
            parents == null
                ? null
                : parents.Value.CastArray<IRevision>());
        return genericChange;
    }
}
