/*
 * Copyright (C) 2023 Google Inc.
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

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Represents a GitHub App's checkRun detail.
/// https://docs.github.com/en/rest/checks/suites?apiVersion=2022-11-28#list-check-suites-for-a-git-reference
/// </summary>
[StarlarkBuiltin(
    "github_check_suite_obj",
    Doc =
        "Detail about a check run as defined in "
        + "https://developer.github.com/v3/checks/runs/#create-a-check-run")]
public class CheckSuite : IStarlarkValue
{
    [JsonPropertyName("status")]
    public CheckRunStatus Status { get; set; }

    [JsonPropertyName("conclusion")]
    public CheckRunConclusion? Conclusion { get; set; }

    [JsonPropertyName("head_sha")]
    public string? Sha { get; set; }

    [JsonPropertyName("id")]
    public long Id { get; set; }

    [JsonPropertyName("app")]
    public GitHubApp? App { get; set; }

    [StarlarkMethod("id", Doc = "Check suite identifier", StructField = true)]
    public StarlarkInt GetId() => StarlarkInt.Of(Id);

    [StarlarkMethod(
        "status",
        Doc = "The current status of the check run. Can be one of queued, in_progress, pending,"
            + " or completed.",
        StructField = true)]
    public string GetStatus() => Status.ToString().ToLowerInvariant();

    [StarlarkMethod(
        "conclusion",
        Doc = "The final conclusion of the check. Can be one of success, failure, neutral, "
            + "cancelled, timed_out, or action_required.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetConclusion() => Conclusion?.ToString().ToLowerInvariant();

    [StarlarkMethod("sha", Doc = "The SHA-1 the check run is based on", StructField = true)]
    public string? GetSha() => Sha;

    [StarlarkMethod("app", Doc = "The detail of a GitHub App, such as id, slug, and name", StructField = true)]
    public GitHubApp? GetApp() => App;

    public override string ToString() =>
        $"CheckSuite{{id={Id}, status={Status}, conclusion={Conclusion}, sha={Sha}, app={App}}}";
}
