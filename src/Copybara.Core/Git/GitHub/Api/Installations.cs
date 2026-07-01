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
/// Corresponds to JSON schema response for top level object in
/// https://docs.github.com/en/rest/orgs/orgs#list-app-installations-for-an-organization.
///
/// <para>Not all property keys are included here. Add them as needed.</para>
/// </summary>
public class Installations : IPaginatedPayload<Installation>
{
    [JsonPropertyName("total_count")]
    public int InstallationsCount { get; set; }

    [JsonPropertyName("installations")]
    public PaginatedList<Installation> InstallationsList { get; set; } = new();

    public Installations()
    {
    }

    public Installations(int installationsCount, PaginatedList<Installation> installations)
    {
        InstallationsCount = installationsCount;
        InstallationsList = installations;
    }

    public int GetInstallationsCount() => InstallationsCount;

    public IReadOnlyList<Installation> GetInstallations() => InstallationsList;

    public override string ToString() =>
        $"Installations{{installations_count={InstallationsCount}, installations={InstallationsList}}}";

    public PaginatedList<Installation> GetPayload() => InstallationsList;

    public IPaginatedPayload AnnotatePayload(string apiPrefix, string? linkHeader) =>
        new Installations(
            InstallationsCount, InstallationsList.WithPaginationInfo(apiPrefix, linkHeader));
}
