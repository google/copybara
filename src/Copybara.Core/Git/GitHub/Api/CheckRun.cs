/*
 * Copyright (C) 2019 Google Inc.
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

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Represents a GitHub App's checkRun detail.
/// https://developer.github.com/v3/checks/runs/#create-a-check-run
/// https://developer.github.com/v3/checks/runs/#response
/// </summary>
[StarlarkBuiltin(
    "github_check_run_obj",
    Doc =
        "Detail about a check run as defined in "
        + "https://developer.github.com/v3/checks/runs/#create-a-check-run")]
public class CheckRun : IStarlarkValue
{
    [JsonPropertyName("details_url")]
    public string? DetailUrl { get; set; }

    [JsonPropertyName("status")]
    public CheckRunStatus Status { get; set; }

    [JsonPropertyName("conclusion")]
    public CheckRunConclusion? Conclusion { get; set; }

    [JsonPropertyName("head_sha")]
    public string? Sha { get; set; }

    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [JsonPropertyName("app")]
    public GitHubApp? App { get; set; }

    [JsonPropertyName("output")]
    public Output? Output { get; set; }

    [JsonPropertyName("pull_requests")]
    public List<CheckRunPullRequest>? PullRequests { get; set; }

    [StarlarkMethod(
        "detail_url",
        Doc = "The URL of the integrator's site that has the full details of the check.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetDetailUrl() => DetailUrl;

    [StarlarkMethod(
        "status",
        Doc = "The current status of the check run. Can be one of queued, in_progress, or completed.",
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

    [StarlarkMethod("name", Doc = "The name of the check", StructField = true)]
    public string? GetName() => Name;

    [StarlarkMethod("app", Doc = "The detail of a GitHub App, such as id, slug, and name", StructField = true)]
    public GitHubApp? GetApp() => App;

    [StarlarkMethod(
        "output",
        Doc = "The description of a GitHub App's run, including title, summary, text.",
        StructField = true)]
    public Output? GetOutput() => Output;

    [StarlarkMethod(
        "pulls",
        Doc = "Pull requests associated with this check_run ('number' only)",
        StructField = true)]
    public IReadOnlyList<CheckRunPullRequest> GetPullRequests() =>
        PullRequests == null
            ? ImmutableArray<CheckRunPullRequest>.Empty
            : PullRequests.ToImmutableArray();

    public override string ToString() =>
        $"CheckRun{{details_url={DetailUrl}, status={Status}, conclusion={Conclusion}, sha={Sha},"
        + $" name={Name}, app={App}, output={Output}, pulls={PullRequests}}}";

    /// <summary>PR submessage in check_run.</summary>
    public class CheckRunPullRequest : IStarlarkValue
    {
        [JsonPropertyName("number")]
        public int Number { get; set; }

        [StarlarkMethod("number", Doc = "Number of a PR liked to the check_run", StructField = true)]
        public int GetNumber() => Number;

        public override string ToString() => $"PullRequest{{number={Number}}}";
    }
}

/// <summary>Status of a check run.</summary>
[JsonConverter(typeof(JsonStringEnumConverter<CheckRunStatus>))]
public enum CheckRunStatus
{
    [JsonStringEnumMemberName("queued")]
    QUEUED,

    [JsonStringEnumMemberName("in_progress")]
    IN_PROGRESS,

    [JsonStringEnumMemberName("completed")]
    COMPLETED,

    [JsonStringEnumMemberName("pending")]
    PENDING,
}

/// <summary>Conclusion of a check run status.</summary>
[JsonConverter(typeof(JsonStringEnumConverter<CheckRunConclusion>))]
public enum CheckRunConclusion
{
    NONE,

    [JsonStringEnumMemberName("success")]
    SUCCESS,

    [JsonStringEnumMemberName("failure")]
    FAILURE,

    [JsonStringEnumMemberName("neutral")]
    NEUTRAL,

    [JsonStringEnumMemberName("timed_out")]
    TIMEDOUT,

    [JsonStringEnumMemberName("cancelled")]
    CANCELLED,

    [JsonStringEnumMemberName("action_required")]
    ACTIONREQUIRED,

    [JsonStringEnumMemberName("skipped")]
    SKIPPED,

    [JsonStringEnumMemberName("stale")]
    STALE,

    [JsonStringEnumMemberName("startup_failure")]
    STARTUPFAILURE,
}

/// <summary>Helpers for <see cref="CheckRunConclusion"/>.</summary>
public static class CheckRunConclusions
{
    private static readonly IReadOnlyDictionary<CheckRunConclusion, string> ApiValues =
        new Dictionary<CheckRunConclusion, string>
        {
            [CheckRunConclusion.NONE] = "none",
            [CheckRunConclusion.SUCCESS] = "success",
            [CheckRunConclusion.FAILURE] = "failure",
            [CheckRunConclusion.NEUTRAL] = "neutral",
            [CheckRunConclusion.TIMEDOUT] = "timed_out",
            [CheckRunConclusion.CANCELLED] = "cancelled",
            [CheckRunConclusion.ACTIONREQUIRED] = "action_required",
            [CheckRunConclusion.SKIPPED] = "skipped",
            [CheckRunConclusion.STALE] = "stale",
            [CheckRunConclusion.STARTUPFAILURE] = "startup_failure",
        };

    public static string GetApiVal(this CheckRunConclusion conclusion) => ApiValues[conclusion];

    /// <summary>
    /// Given a String value of Conclusion as returned by GitHub API, returns the equivalent enum
    /// value or null if not found.
    /// </summary>
    public static CheckRunConclusion? FromValue(string val)
    {
        foreach (var kv in ApiValues)
        {
            if (kv.Value == val)
            {
                return kv.Key;
            }
        }

        return null;
    }
}
