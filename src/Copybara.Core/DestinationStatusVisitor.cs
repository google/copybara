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
using Copybara.Revision;
using Copybara.Util;

namespace Copybara;

/// <summary>
/// A visitor that computes the <see cref="DestinationStatus"/> matching the actual files affected by
/// the changes with the destination files glob.
/// </summary>
public class DestinationStatusVisitor : IChangesVisitor
{
    private readonly IPathMatcher _pathMatcher;
    private readonly string _labelName;

    private DestinationStatus? _destinationStatus;

    public DestinationStatusVisitor(IPathMatcher pathMatcher, string labelName)
    {
        _pathMatcher = pathMatcher;
        _labelName = labelName;
    }

    public VisitResult Visit(Change<IRevision> change)
    {
        var changeFiles = change.GetChangeFiles();
        if (changeFiles != null)
        {
            if (change.GetLabels().ContainsKey(_labelName))
            {
                foreach (var file in changeFiles)
                {
                    if (_pathMatcher.Matches("/" + file))
                    {
                        var values = change.GetLabels().Get(_labelName);
                        string lastRev = values[values.Length - 1];
                        _destinationStatus =
                            new DestinationStatus(lastRev, ImmutableArray<string>.Empty);
                        return VisitResult.Terminate;
                    }
                }
            }
        }
        return VisitResult.Continue;
    }

    public DestinationStatus? GetDestinationStatus() => _destinationStatus;
}
