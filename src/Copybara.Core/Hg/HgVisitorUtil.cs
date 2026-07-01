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

namespace Copybara.Hg;

/// <summary>Utility methods for visiting Mercurial (Hg) repositories.</summary>
public static class HgVisitorUtil
{
    /// <summary>Visits Hg changes, up to the termination point specified by the visitor.</summary>
    internal static void VisitChanges(
        HgRevision start,
        IChangesVisitor visitor,
        ChangeReader.Builder queryChanges,
        GeneralOptions generalOptions,
        string type,
        int visitChangePageSize)
    {
        Preconditions.CheckNotNull(start);
        int offset = 0;
        bool finished = false;

        using (generalOptions.Profiler().Start(type + "/visit_changes"))
        {
            while (!finished)
            {
                IReadOnlyList<Change<HgRevision>> result;
                using (generalOptions.Profiler().Start($"hg_log_{offset}_{visitChangePageSize}"))
                {
                    try
                    {
                        var changes = queryChanges
                            .SetSkip(offset)
                            .SetLimit(visitChangePageSize)
                            .Build()
                            .Run(start.GetGlobalId());
                        result = changes.Reverse().ToList();
                    }
                    catch (ValidationException e)
                    {
                        throw new RepoException($"Error querying changes: {e.Message}", e.InnerException);
                    }
                }

                if (result.Count == 0)
                {
                    break;
                }

                offset += result.Count;
                foreach (Change<HgRevision> current in result)
                {
                    if (visitor.Visit(ToGenericChange(current)) == VisitResult.Terminate)
                    {
                        finished = true;
                        break;
                    }
                }
            }
        }
    }

    /// <summary>
    /// Adapts a <see cref="Change{HgRevision}"/> to a <see cref="Change{IRevision}"/> so it can be
    /// passed to the invariant <see cref="IChangesVisitor"/> interface.
    /// </summary>
    internal static Change<IRevision> ToGenericChange(Change<HgRevision> change)
    {
        var parents = change.GetParents();
        return new Change<IRevision>(
            change.GetRevision(),
            change.GetAuthor(),
            change.GetMessage(),
            change.GetDateTime(),
            change.GetLabels(),
            change.GetChangeFiles(),
            change.IsMerge(),
            parents?.CastArray<IRevision>());
    }
}
