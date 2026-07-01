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

using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Transform;
using Starlark.Syntax;

namespace Copybara.Transform.Metadata;

/// <summary>
/// Given a label that is not present in the change message but it is in the changes metadata, expose
/// it as a text label.
/// </summary>
public class ExposeLabelInMessage : ITransformation
{
    private readonly string _label;
    private readonly string _newLabelName;
    private readonly string _separator;
    private readonly bool _ignoreNotFound;
    private readonly bool _all;
    private readonly string? _joiner;
    private readonly Location _location;

    internal ExposeLabelInMessage(
        string label,
        string newLabelName,
        string separator,
        bool ignoreNotFound,
        bool all,
        string? joiner,
        Location location)
    {
        _label = Preconditions.CheckNotNull(label);
        _newLabelName = Preconditions.CheckNotNull(newLabelName);
        _separator = Preconditions.CheckNotNull(separator);
        _ignoreNotFound = ignoreNotFound;
        _all = all;
        _joiner = joiner;
        _location = Preconditions.CheckNotNull(location);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        if (_all)
        {
            return ExposeAllLabels(work);
        }

        string? value = work.GetLabel(_label);
        if (value == null)
        {
            ValidationException.CheckCondition(_ignoreNotFound, "Cannot find label {0}", _label);
            return TransformationStatus.Success();
        }
        if (_label.Equals(_newLabelName))
        {
            work.RemoveLabelWithValue(_label, value, wholeMessage: true);
        }
        work.AddLabel(_newLabelName, value, _separator, hidden: false);
        return TransformationStatus.Success();
    }

    private TransformationStatus ExposeAllLabels(TransformWork work)
    {
        // Preserve insertion order and de-duplicate, like a LinkedHashSet.
        var values = new List<string>();
        var seen = new HashSet<string>();
        foreach (var value in work.GetAllLabels(_label))
        {
            if (seen.Add(value))
            {
                values.Add(value);
            }
        }

        if (values.Count == 0)
        {
            ValidationException.CheckCondition(_ignoreNotFound, "Cannot find label {0}", _label);
            return TransformationStatus.Success();
        }

        // If the label name is the same, we remove it and add it at the end, since the format
        // of the message will be more consistent.
        if (_label.Equals(_newLabelName))
        {
            // Remove the old label since we want it with a different name/separator.
            work.RemoveLabel(_label, wholeMessage: true);
        }
        if (_joiner != null)
        {
            work.AddLabel(_newLabelName, string.Join(_joiner, values), _separator, hidden: false);
        }
        else
        {
            foreach (var value in values)
            {
                work.AddLabel(_newLabelName, value, _separator, hidden: false);
            }
        }

        return TransformationStatus.Success();
    }

    public ITransformation Reverse() =>
        new ExplicitReversal(IntentionalNoop.Instance, this);

    public string Describe() => $"Exposing label {_label} as {_newLabelName}";

    public Location Location() => _location;
}
