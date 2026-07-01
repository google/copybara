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
/// Corresponds to JSON schema response for individual installations detailed in
/// https://docs.github.com/en/rest/orgs/orgs#list-app-installations-for-an-organization.
///
/// <para>Not all property keys are included here. Add them as needed.</para>
/// </summary>
public class Installation
{
    [JsonPropertyName("app_slug")]
    public string? AppSlug { get; set; }

    [JsonPropertyName("app_id")]
    public int AppId { get; set; }

    [JsonPropertyName("target_type")]
    public string? TargetType { get; set; }

    [JsonPropertyName("repository_selection")]
    public string? RepositorySelection { get; set; }

    public string? GetAppSlug() => AppSlug;

    public int GetAppId() => AppId;

    public string? GetTargetType() => TargetType;

    public string? GetRepositorySelection() => RepositorySelection;

    public override string ToString() =>
        $"Installation{{app_slug={AppSlug}, app_id={AppId}, target_type={TargetType},"
        + $" repository_selection={RepositorySelection}}}";
}
