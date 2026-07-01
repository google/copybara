/*
 * Copyright (C) 2016 Google LLC.
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
using Copybara.Common;
using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara;

/// <summary>
/// An object that represents a well formed message: No superfluous new lines, a group of labels,
/// etc.
///
/// <para>This class is immutable.</para>
/// </summary>
[StarlarkBuiltin(
    "ChangeMessage",
    Doc = "Represents a well formed parsed change message with its associated labels.")]
public sealed class ChangeMessage : IStarlarkValue
{
    private const string DoubleNewline = "\n\n";
    private const string DashDashSeparator = "\n--\n";

    private readonly string _text;
    private readonly string _groupSeparator;
    private readonly ImmutableArray<LabelFinder> _labels;

    private ChangeMessage(string text, string groupSeparator, IReadOnlyList<LabelFinder> labels)
    {
        _text = text.Trim('\n');
        _groupSeparator = Preconditions.CheckNotNull(groupSeparator);
        _labels = Preconditions.CheckNotNull(labels).ToImmutableArray();
    }

    /// <summary>
    /// Create a new message object looking for labels in just the last paragraph.
    ///
    /// <para>Use this for Copybara well-formed messages.</para>
    /// </summary>
    public static ChangeMessage ParseMessage(string message)
    {
        string trimMsg = message.Trim('\n');
        int doubleNewLine = trimMsg.LastIndexOf(DoubleNewline, StringComparison.Ordinal);
        int dashDash = trimMsg.LastIndexOf(DashDashSeparator, StringComparison.Ordinal);
        if (doubleNewLine == -1 && dashDash == -1)
        {
            // Empty message like "\n\nfoo: bar" or "\n\nfoo bar baz"
            if (message.StartsWith(DoubleNewline, StringComparison.Ordinal))
            {
                return new ChangeMessage("", DoubleNewline, LinesAsLabels(trimMsg));
            }
            return new ChangeMessage(trimMsg, DoubleNewline, new List<LabelFinder>());
        }
        else if (doubleNewLine > dashDash)
        {
            return new ChangeMessage(
                trimMsg.Substring(0, doubleNewLine),
                DoubleNewline,
                LinesAsLabels(trimMsg.Substring(doubleNewLine + 2)));
        }
        else
        {
            return new ChangeMessage(
                trimMsg.Substring(0, dashDash),
                DashDashSeparator,
                LinesAsLabels(trimMsg.Substring(dashDash + 4)));
        }
    }

    /// <summary>
    /// Create a new message object treating all the lines as possible labels instead of looking just
    /// in the last paragraph for labels.
    /// </summary>
    public static ChangeMessage ParseAllAsLabels(string message)
    {
        Preconditions.CheckNotNull(message);
        return new ChangeMessage("", DoubleNewline, LinesAsLabels(message));
    }

    private static List<LabelFinder> LinesAsLabels(string message)
    {
        Preconditions.CheckNotNull(message);
        return message.TrimEnd('\n').Split('\n').Select(line => new LabelFinder(line)).ToList();
    }

    [StarlarkMethod("first_line", Doc = "First line of this message", StructField = true)]
    public string FirstLine()
    {
        int idx = _text.IndexOf('\n');
        return idx == -1 ? _text : _text.Substring(0, idx);
    }

    [StarlarkMethod(
        "text",
        Doc = "The text description this message, not including the labels.",
        StructField = true)]
    public string GetText() => _text;

    public IReadOnlyList<LabelFinder> GetLabels() => _labels;

    /// <summary>
    /// Returns all the labels in the message. If a label appears multiple times, it respects the
    /// order of appearance.
    /// </summary>
    public ImmutableListMultimap<string, string> LabelsAsMultimap()
    {
        var result = ImmutableListMultimap<string, string>.CreateBuilder();
        foreach (var label in _labels)
        {
            if (label.IsLabel())
            {
                result.Put(label.GetName(), label.GetValue());
            }
        }
        return result.Build();
    }

    [StarlarkMethod(
        "label_values",
        Doc = "Returns a list of values associated with the label name.")]
    public IReadOnlyList<string> GetLabelValues(
        [Param(Name = "label_name", Named = true, Doc = "The label name.")] string labelName)
    {
        var localLabels = LabelsAsMultimap();
        if (localLabels.ContainsKey(labelName))
        {
            return localLabels.Get(labelName);
        }
        return ImmutableArray<string>.Empty;
    }

    public ChangeMessage WithLabel(string name, string separator, string value)
    {
        var newLabels = new List<LabelFinder>(_labels);
        // Add an additional line if none of the previous elements are labels
        if (newLabels.Count != 0 && !newLabels.Any(l => l.IsLabel()))
        {
            newLabels.Add(new LabelFinder(""));
        }
        newLabels.Add(new LabelFinder(
            ValidateLabelName(name) + Preconditions.CheckNotNull(separator)
            + Preconditions.CheckNotNull(value)));
        return new ChangeMessage(_text, _groupSeparator, newLabels);
    }

    public ChangeMessage WithReplacedLabel(string labelName, string separator, string value)
    {
        ValidateLabelName(labelName);
        var newLabels = _labels
            .Select(label => label.IsLabel(labelName)
                ? new LabelFinder(labelName + separator + value)
                : label)
            .ToList();
        return new ChangeMessage(_text, _groupSeparator, newLabels);
    }

    public ChangeMessage WithNewOrReplacedLabel(string labelName, string separator, string value)
    {
        ValidateLabelName(labelName);
        var newLabels = new List<LabelFinder>();
        bool wasReplaced = false;

        foreach (var originalLabel in _labels)
        {
            if (originalLabel.IsLabel(labelName))
            {
                newLabels.Add(new LabelFinder(labelName + separator + value));
                wasReplaced = true;
            }
            else
            {
                newLabels.Add(originalLabel);
            }
        }

        var newChangeMessage = new ChangeMessage(_text, _groupSeparator, newLabels);
        if (!wasReplaced)
        {
            return newChangeMessage.WithLabel(labelName, separator, value);
        }
        return newChangeMessage;
    }

    /// <summary>Filters out all labels that do not match <paramref name="predicate"/>.</summary>
    public ChangeMessage WithLabelsFilteredBy(Func<LabelFinder, bool> predicate)
    {
        var filteredLabels = _labels.Where(predicate).ToList();
        return new ChangeMessage(_text, _groupSeparator, filteredLabels);
    }

    /// <summary>Remove a label by name if it exists.</summary>
    public ChangeMessage WithRemovedLabelByName(string name)
    {
        ValidateLabelName(name);
        var filteredLabels = _labels.Where(label => !label.IsLabel(name)).ToList();
        return new ChangeMessage(_text, _groupSeparator, filteredLabels);
    }

    /// <summary>Remove a label by name and value if it exists.</summary>
    public ChangeMessage WithRemovedLabelByNameAndValue(string name, string value)
    {
        ValidateLabelName(name);
        var filteredLabels = _labels
            .Where(label => !label.IsLabel(name) || !label.GetValue().Equals(value))
            .ToList();
        return new ChangeMessage(_text, _groupSeparator, filteredLabels);
    }

    private static string ValidateLabelName(string label)
    {
        ValidationException.CheckCondition(
            LabelFinder.VALID_LABEL.IsMatch(label), "Label '{0}' is not a valid label", label);
        return label;
    }

    /// <summary>Set the text part of the message, leaving the labels untouched.</summary>
    public ChangeMessage WithText(string text) =>
        new(text.Trim('\n'), _groupSeparator, _labels);

    public override string ToString()
    {
        var sb = new StringBuilder();

        if (_text.Length != 0)
        {
            sb.Append(_text).Append(_labels.Length == 0 ? "\n" : _groupSeparator);
        }
        foreach (var label in _labels)
        {
            sb.Append(label.GetLine()).Append('\n');
        }
        // Let's normalize in case parseAllAsLabels was used and all the labels were removed.
        return sb.ToString().Trim('\n') + '\n';
    }
}
