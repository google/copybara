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
/// An object that represents the input parameters for a submit requirement:
///
/// <para>https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#submit-requirement-input</para>
/// </summary>
public class SubmitRequirementInput : IStarlarkValue
{
    /// <summary>Submit requirement name.</summary>
    [JsonPropertyName("name")]
    public string Name { get; set; }

    /// <summary>
    /// Query expression that can be evaluated on any change. If evaluated to true on a change, the
    /// submit requirement is fulfilled and not blocking change submission.
    /// </summary>
    [JsonPropertyName("submittability_expression")]
    public string SubmittabilityExpression { get; set; }

    public SubmitRequirementInput(string name, string submittabilityExpression)
    {
        Name = name;
        SubmittabilityExpression = submittabilityExpression;
    }

    [StarlarkMethod("name", Doc = "The submit requirement name.", StructField = true)]
    public string GetName() => Name;

    [StarlarkMethod(
        "submittability_expression",
        Doc =
            "Query expression that can be evaluated on any change. If evaluated to true on a change,"
            + " the submit requirement is fulfilled and not blocking change submission.",
        StructField = true)]
    public string GetSubmittabilityExpression() => SubmittabilityExpression;
}
