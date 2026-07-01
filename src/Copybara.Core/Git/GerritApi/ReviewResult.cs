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

using System.Collections.Immutable;
using System.Text.Json.Serialization;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git.GerritApi;

/// <summary>https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-result</summary>
[StarlarkBuiltin("gerritapi.ReviewResult", Doc = "Gerrit review result.")]
public class ReviewResult : IStarlarkPrintableValue
{
    [JsonPropertyName("labels")]
    public IReadOnlyDictionary<string, int>? Labels { get; set; }

    [JsonPropertyName("ready")]
    public bool Ready { get; set; }

    public ReviewResult(IReadOnlyDictionary<string, int>? labels, bool ready)
    {
        Labels = labels;
        Ready = ready;
    }

    public ReviewResult()
    {
    }

    [StarlarkMethod(
        "labels",
        Doc = "Map of labels to values after the review was posted.",
        StructField = true)]
    public IReadOnlyDictionary<string, StarlarkInt> GetLabelsForStarlark()
    {
        var m = ImmutableDictionary.CreateBuilder<string, StarlarkInt>();
        foreach (var e in GetLabels())
        {
            m[e.Key] = StarlarkInt.Of(e.Value);
        }

        return m.ToImmutable();
    }

    public IReadOnlyDictionary<string, int> GetLabels() =>
        Labels is null ? ImmutableDictionary<string, int>.Empty : Labels.ToImmutableDictionary();

    [StarlarkMethod(
        "ready",
        Doc =
            "If true, the change was moved from WIP to ready for review as a result of this action."
            + " Not set if false.",
        StructField = true)]
    public bool IsReady() => Ready;

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() => $"ReviewResult{{labels={Labels}, ready={Ready}}}";
}
