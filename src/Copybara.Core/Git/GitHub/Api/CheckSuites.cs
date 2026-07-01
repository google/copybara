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
/// Represents a GitHub App's checkSuites response detail.
/// https://docs.github.com/en/rest/checks/suites?apiVersion=2022-11-28#list-check-suites-for-a-git-reference
/// </summary>
[StarlarkBuiltin(
    "github_check_suites_response_obj",
    Doc =
        "Detail about a check run as defined in "
        + "https://docs.github.com/en/rest/checks/suites?apiVersion=2022-11-28#list-check-suites-for-a-git-reference")]
public class CheckSuites : IStarlarkValue, IPaginatedPayload<CheckSuite>
{
    [JsonPropertyName("total_count")]
    public int TotalCount { get; set; }

    [JsonPropertyName("check_suites")]
    public PaginatedList<CheckSuite> CheckSuitesList { get; set; } = new();

    public CheckSuites()
    {
    }

    private CheckSuites(int totalCount, PaginatedList<CheckSuite> checkSuites)
    {
        CheckSuitesList = checkSuites;
        TotalCount = totalCount;
    }

    public PaginatedList<CheckSuite> GetPayload() => CheckSuitesList;

    public IPaginatedPayload AnnotatePayload(string apiPrefix, string? linkHeader) =>
        new CheckSuites(TotalCount, CheckSuitesList.WithPaginationInfo(apiPrefix, linkHeader));
}
