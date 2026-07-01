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

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Corresponds to JSON schema response for
/// https://docs.github.com/en/rest/search?apiVersion=2022-11-28#search-issues-and-pull-requests.
///
/// <para>Not all property keys are included here. Add them as needed.</para>
/// </summary>
public class IssuesAndPullRequestsSearchResults
{
    [JsonPropertyName("items")]
    public List<IssuesAndPullRequestsSearchResult>? Items { get; set; }

    public IssuesAndPullRequestsSearchResults()
    {
    }

    public List<IssuesAndPullRequestsSearchResult>? GetItems() => Items;

    public override string ToString() => $"IssuesAndPullRequestsSearchResults{{items={Items}}}";

    /// <summary>A single result entity from fetching issues.</summary>
    public class IssuesAndPullRequestsSearchResult
    {
        [JsonPropertyName("number")]
        public long Number { get; set; }

        public IssuesAndPullRequestsSearchResult()
        {
        }

        public long GetNumber() => Number;
    }
}
