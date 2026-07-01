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

using System.Text.Json.Serialization;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Represents the response of list check runs for a specific ref.
/// https://developer.github.com/v3/checks/runs/#list-check-runs-for-a-specific-ref
/// </summary>
[StarlarkBuiltin(
    "github_check_runs_obj",
    Doc =
        "List check runs for a specific ref "
        + "https://developer.github.com/v3/checks/runs/#list-check-runs-for-a-specific-ref")]
public class CheckRuns : IStarlarkValue, IPaginatedPayload<CheckRun>
{
    [JsonPropertyName("total_count")]
    public int TotalCount { get; set; }

    [JsonPropertyName("check_runs")]
    public PaginatedList<CheckRun> CheckRunsList { get; set; } = new();

    public CheckRuns()
    {
    }

    private CheckRuns(int totalCount, PaginatedList<CheckRun> checkRuns)
    {
        CheckRunsList = checkRuns;
        TotalCount = totalCount;
    }

    [StarlarkMethod("total_count", Doc = "The total count of check runs.", StructField = true)]
    public int GetTotalCount() => TotalCount;

    [StarlarkMethod("check_runs", Doc = "The list of the detail for each check run.", StructField = true)]
    public IReadOnlyList<CheckRun> GetCheckRuns() => CheckRunsList;

    public override string ToString() =>
        $"CheckRuns{{total_count={TotalCount}, check_runs={CheckRunsList}}}";

    public PaginatedList<CheckRun> GetPayload() => CheckRunsList;

    public IPaginatedPayload AnnotatePayload(string apiPrefix, string? linkHeader) =>
        new CheckRuns(TotalCount, CheckRunsList.WithPaginationInfo(apiPrefix, linkHeader));
}
