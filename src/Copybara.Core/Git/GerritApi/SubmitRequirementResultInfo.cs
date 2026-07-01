/*
 * Copyright (C) 2022 Google Inc.
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

/// <summary>Result of evaluating a submit requirement on a change.</summary>
public class SubmitRequirementResultInfo : IStarlarkValue
{
    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [JsonPropertyName("status")]
    public string? StatusString { get; set; }

    [JsonPropertyName("submittability_expression_result")]
    public SubmitRequirementExpressionInfo? SubmittabilityExpressionResult { get; set; }

    [JsonPropertyName("is_legacy")]
    public bool IsLegacy { get; set; }

    [StarlarkMethod("name", Doc = "The submit requirement name.", StructField = true)]
    public string? GetName() => Name;

    public SubmitRequirementResultStatus GetStatus() =>
        Enum.Parse<SubmitRequirementResultStatus>(StatusString!);

    [StarlarkMethod(
        "status",
        Doc = "The status of the submit requirement evaluation.",
        StructField = true)]
    public string? GetStatusAsString() => StatusString;

    [StarlarkMethod(
        "is_legacy",
        Doc =
            "If true, this submit requirement result was created from a legacy SubmitRecord."
            + " Otherwise, it was created by evaluating a submit requirement.",
        StructField = true)]
    public bool GetIsLegacy() => IsLegacy;

    [StarlarkMethod(
        "submittability_expression_result",
        Doc =
            "A SubmitRequirementExpressionInfo containing the result of evaluating the"
            + " submittabilityexpression. If the submit requirement does not apply, the status"
            + " field of the result will be set to NOT_EVALUATED.",
        StructField = true)]
    public SubmitRequirementExpressionInfo? GetSubmittabilityExpressionResult() =>
        SubmittabilityExpressionResult;

    public override string ToString() =>
        $"SubmitRequirementResultInfo{{name={Name}, status={StatusString}, "
        + $"submittabilityExpressionResult={SubmittabilityExpressionResult}, isLegacy={IsLegacy}}}";
}
