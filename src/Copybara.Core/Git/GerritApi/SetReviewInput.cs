/*
 * Copyright (C) 2017 Google Inc.
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

using System.Text.Json.Serialization;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git.GerritApi;

/// <summary>
/// See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input.
/// </summary>
[StarlarkBuiltin(
    "SetReviewInput",
    Doc =
        "Input for posting a review to Gerrit. See "
        + "https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input")]
public class SetReviewInput : IStarlarkPrintableValue, IEquatable<SetReviewInput>
{
    [JsonPropertyName("message")]
    public string? Message { get; set; }

    [JsonPropertyName("labels")]
    public IReadOnlyDictionary<string, int> Labels { get; set; }

    [JsonPropertyName("tag")]
    public string? Tag { get; set; }

    /// <summary>The notify type, serialized to its Gerrit wire value (null for ALL).</summary>
    [JsonPropertyName("notify")]
    public string? NotifyWire => NotifyType?.ToWireValue();

    [JsonIgnore]
    public NotifyType? NotifyType { get; set; }

    public SetReviewInput()
    {
        Labels = new Dictionary<string, int>();
    }

    private SetReviewInput(
        string? message, IReadOnlyDictionary<string, int> labels, string? tag, NotifyType notify)
    {
        Message = message;
        Labels = labels;
        Tag = tag;
        NotifyType = notify;
    }

    public SetReviewInput(string? message, IReadOnlyDictionary<string, int> labels)
        : this(message, labels, null, global::Copybara.Git.GerritApi.NotifyType.ALL)
    {
    }

    public static SetReviewInput Create(
        string? message, IReadOnlyDictionary<string, int> labels, string? tag) =>
        Create(message, labels, tag, global::Copybara.Git.GerritApi.NotifyType.ALL);

    public static SetReviewInput Create(
        string? message, IReadOnlyDictionary<string, int> labels, string? tag, NotifyType notify) =>
        new(message, labels, tag, notify);

    public string? GetMessage() => Message;

    public IReadOnlyDictionary<string, int> GetLabels() => Labels;

    public NotifyType? GetNotify() => NotifyType;

    public string? GetTag() => Tag;

    public override string ToString() =>
        $"SetReviewInput{{message={Message}, labels={Labels}, tag={Tag}}}";

    public bool Equals(SetReviewInput? other)
    {
        if (ReferenceEquals(this, other))
        {
            return true;
        }

        if (other is null)
        {
            return false;
        }

        return Message == other.Message
            && LabelsEqual(Labels, other.Labels)
            && Tag == other.Tag;
    }

    public override bool Equals(object? o) => o is SetReviewInput other && Equals(other);

    public override int GetHashCode()
    {
        var hash = default(HashCode);
        hash.Add(Message);
        foreach (var e in Labels)
        {
            hash.Add(e.Key);
            hash.Add(e.Value);
        }

        return hash.ToHashCode();
    }

    private static bool LabelsEqual(IReadOnlyDictionary<string, int> a, IReadOnlyDictionary<string, int> b)
    {
        if (a.Count != b.Count)
        {
            return false;
        }

        foreach (var e in a)
        {
            if (!b.TryGetValue(e.Key, out var v) || v != e.Value)
            {
                return false;
            }
        }

        return true;
    }

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());
}
