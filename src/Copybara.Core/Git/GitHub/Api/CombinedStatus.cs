/*
 * Copyright (C) 2018 Google Inc.
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
/// A combined commit status object.
///
/// <para>https://developer.github.com/v3/repos/statuses</para>
/// </summary>
[StarlarkBuiltin(
    "github_api_combined_status_obj",
    Doc =
        "Combined Information about a commit status as defined in"
        + " https://developer.github.com/v3/repos/statuses. This is a subset of the available"
        + " fields in GitHub")]
public class CombinedStatus : IStarlarkValue
{
    [JsonPropertyName("state")]
    public StatusState State { get; set; }

    [JsonPropertyName("sha")]
    public string? Sha { get; set; }

    [JsonPropertyName("total_count")]
    public int TotalCount { get; set; }

    [JsonPropertyName("statuses")]
    public List<Status>? Statuses { get; set; }

    public StatusState GetState() => State;

    [StarlarkMethod(
        "state",
        Doc = "The overall state of all statuses for a commit: success, failure, pending or error",
        StructField = true)]
    public string GetStateForSkylark() => State.ToString().ToLowerInvariant();

    [StarlarkMethod("sha", Doc = "The SHA-1 of the commit", StructField = true)]
    public string? GetSha() => Sha;

    [StarlarkMethod("total_count", Doc = "Total number of statuses", StructField = true)]
    public int GetTotalCount() => TotalCount;

    [StarlarkMethod("statuses", Doc = "List of statuses for the commit", StructField = true)]
    public ISequence<object?> GetStatuses() =>
        StarlarkList.ImmutableCopyOf((Statuses ?? new List<Status>()).Cast<object?>());
}
