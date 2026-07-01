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

/// <summary>
/// Result of evaluating a single submit requirement expression. This API entity is populated from
/// Gerrit's <c>SubmitRequirementExpressionResult</c>.
/// </summary>
[StarlarkBuiltin(
    "gerritapi.SubmitRequirementExpressionInfo",
    Doc = "Result of evaluating submit requirement expression")]
public class SubmitRequirementExpressionInfo : IStarlarkValue
{
    [JsonPropertyName("expression")]
    public string? Expression { get; set; }

    [JsonPropertyName("status")]
    public string? StatusString { get; set; }

    [JsonPropertyName("fulfilled")]
    public bool Fulfilled { get; set; }

    [StarlarkMethod(
        "expression",
        Doc = "The submit requirement expression as a string.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetExpression() => Expression;

    public SubmitRequirementExpressionStatus GetStatus() =>
        Enum.Parse<SubmitRequirementExpressionStatus>(StatusString!);

    [StarlarkMethod(
        "status",
        Doc = "The status of the submit requirement evaluation.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetStatusAsString() => StatusString;

    [StarlarkMethod(
        "fulfilled",
        Doc =
            "If true, this submit requirement result was created from a legacy SubmitRecord."
            + " Otherwise, it was created by evaluating a submit requirement.",
        StructField = true)]
    public bool GetFulfilled() => Fulfilled;

    public override string ToString() =>
        $"SubmitRequirementExpressionInfo{{expression={Expression}, status={StatusString}, "
        + $"fulfilled={Fulfilled}}}";
}
