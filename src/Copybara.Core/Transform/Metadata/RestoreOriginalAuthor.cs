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

using Copybara.Authoring;
using Copybara.Common;
using Copybara.Revision;
using Starlark.Eval;
using Starlark.Syntax;

namespace Copybara.Transform.Metadata;

/// <summary>Restores an original author stored in a label.</summary>
public class RestoreOriginalAuthor : ITransformation
{
    private readonly string _label;
    private readonly string _separator;
    private readonly bool _searchAllChanges;
    private readonly Location _location;

    internal RestoreOriginalAuthor(
        string label, string separator, bool searchAllChanges, Location location)
    {
        _label = label;
        _separator = separator;
        _searchAllChanges = searchAllChanges;
        _location = Preconditions.CheckNotNull(location);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        Author? author = null;
        // If multiple commits are included (for example on a squash for skipping a bad change),
        // last author wins.
        foreach (var changeObj in work.GetChanges().GetCurrent())
        {
            var change = (Change<IRevision>)changeObj;
            var labelValue = change.GetLabels().Get(_label);
            if (labelValue.Length != 0)
            {
                try
                {
                    author = Author.Parse(labelValue[labelValue.Length - 1]);
                }
                catch (EvalException e)
                {
                    // Don't fail the migration because the label is wrong since it is very
                    // difficult for a user to recover from this.
                    work.GetConsole().Warn("Cannot restore original author: " + e.Message);
                }
            }
            if (!_searchAllChanges)
            {
                break;
            }
        }

        if (author != null)
        {
            work.SetAuthor(author);
            work.RemoveLabel(_label, wholeMessage: true);
        }
        return TransformationStatus.Success();
    }

    public ITransformation Reverse() =>
        new SaveOriginalAuthor(_label, _separator, _location);

    public string Describe() => "Restoring original author";

    public Location Location() => _location;
}
