/*
 * Copyright (C) 2025 Google LLC
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

namespace Copybara.Git.GitLab.Api.Entities;

/// <summary>Represents a GitLab External Status Check.</summary>
/// <seealso href="https://docs.gitlab.com/api/status_checks"/>
public sealed class ExternalStatusCheck : IGitLabApiEntity
{
    [JsonPropertyName("id")]
    public int StatusCheckId { get; set; }

    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [JsonPropertyName("project_id")]
    public int ProjectId { get; set; }

    [JsonPropertyName("external_url")]
    public string? ExternalUrl { get; set; }

    [JsonPropertyName("protected_branches")]
    public List<string>? ProtectedBranches { get; set; }

    [JsonPropertyName("hmac")]
    public bool Hmac { get; set; }

    public ExternalStatusCheck(
        int statusCheckId,
        string? name,
        int projectId,
        string? externalUrl,
        List<string>? protectedBranches,
        bool hmac)
    {
        StatusCheckId = statusCheckId;
        Name = name;
        ProjectId = projectId;
        ExternalUrl = externalUrl;
        ProtectedBranches = protectedBranches;
        Hmac = hmac;
    }

    public ExternalStatusCheck()
    {
    }

    public int GetStatusCheckId() => StatusCheckId;

    public string? GetName() => Name;

    public int GetProjectId() => ProjectId;

    public string? GetExternalUrl() => ExternalUrl;

    public IReadOnlyList<string> GetProtectedBranches() =>
        ProtectedBranches is null ? ImmutableArray<string>.Empty : ProtectedBranches.ToImmutableArray();

    public bool GetHmac() => Hmac;
}
