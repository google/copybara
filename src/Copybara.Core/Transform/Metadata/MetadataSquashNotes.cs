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
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.TemplateToken;
using Copybara.Transform;
using Starlark.Syntax;

namespace Copybara.Transform.Metadata;

/// <summary>
/// Generates a message that includes a constant prefix text and a list of changes included in the
/// squash change.
/// </summary>
public class MetadataSquashNotes : ITransformation
{
    private readonly LabelTemplate _prefixTemplate;
    private readonly int _max;
    private readonly bool _compact;
    private readonly bool _showAuthor;
    private readonly bool _showDescription;
    private readonly bool _showRef;
    private readonly bool _oldestFirst;
    private readonly bool _useMerge;
    private readonly Location _location;

    public MetadataSquashNotes(
        string prefix,
        int max,
        bool compact,
        bool showRef,
        bool showAuthor,
        bool showDescription,
        bool oldestFirst,
        bool useMerge,
        Location location)
    {
        _prefixTemplate = new LabelTemplate(prefix);
        _max = max;
        _compact = compact;
        _showRef = showRef;
        _showAuthor = showAuthor;
        _showDescription = showDescription;
        _oldestFirst = oldestFirst;
        _useMerge = useMerge;
        _location = location;
    }

    public TransformationStatus Transform(TransformWork work)
    {
        StringBuilder sb;
        try
        {
            sb = new StringBuilder(_prefixTemplate.Resolve(work.GetLabel));
        }
        catch (LabelTemplate.LabelNotFoundException e)
        {
            throw new ValidationException(
                $"Cannot find label '{e.Label}' in message:\n {work.GetMessage()}\nor any of the"
                + " original commit messages");
        }

        if (_max == 0)
        {
            // Don't force changes to be computed if we don't want any change back.
            work.SetMessage(sb.ToString());
            return TransformationStatus.Success();
        }

        int counter = 0;
        var changesList = new List<Change<IRevision>>(
            work.GetChanges().GetCurrent().Cast<Change<IRevision>>());
        if (_oldestFirst)
        {
            changesList.Reverse();
        }
        if (!_useMerge)
        {
            changesList = changesList.Where(e => !e.IsMerge()).ToList();
        }

        for (int i = 0; i < changesList.Count; i++)
        {
            Change<IRevision> c = changesList[i];
            if (counter == _max)
            {
                break;
            }
            var summary = new List<string>();
            if (_compact)
            {
                sb.Append("  - ");
                if (_showRef)
                {
                    summary.Add(c.Ref);
                }
                if (_showDescription)
                {
                    summary.Add(CutIfLong(c.FirstLineMessage()));
                }
                if (_showAuthor)
                {
                    summary.Add("by " + c.GetMappedAuthor());
                }
                sb.Append(string.Join(" ", summary));
                sb.Append('\n');
            }
            else
            {
                sb.Append("--\n");
                if (_showRef)
                {
                    summary.Add(c.Ref);
                }
                else
                {
                    summary.Add($"Change {i + 1} of {changesList.Count}");
                }
                if (_showAuthor)
                {
                    summary.Add("by " + c.GetAuthor());
                }
                sb.Append(string.Join(" ", summary));
                if (_showDescription)
                {
                    sb.Append(":\n\n");
                    sb.Append(c.GetMessage());
                }
                sb.Append('\n');
            }
            counter++;
        }

        if (changesList.Count > _max)
        {
            sb.Append("  (And ").Append(changesList.Count - _max).Append(" more changes)\n");
        }
        work.SetMessage(sb.ToString());

        return TransformationStatus.Success();
    }

    private static string CutIfLong(string msg) =>
        msg.Length < 60 ? msg : msg.Substring(0, 57) + "...";

    public ITransformation Reverse() =>
        new ExplicitReversal(IntentionalNoop.Instance, this);

    public string Describe() => "squash_notes";

    public Location Location() => _location;
}
