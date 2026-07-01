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

using System.Collections.Immutable;
using System.Text.Json.Serialization;
using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// A commit status object.
///
/// <para>https://developer.github.com/v3/repos/statuses</para>
/// </summary>
[StarlarkBuiltin(
    "github_api_status_obj",
    Doc =
        "Information about a commit status as defined in"
        + " https://developer.github.com/v3/repos/statuses. This is a subset of the available"
        + " fields in GitHub")]
public class Status : IStarlarkValue
{
    [JsonPropertyName("target_url")]
    public string? TargetUrl { get; set; }

    [JsonPropertyName("description")]
    public string? Description { get; set; }

    [JsonPropertyName("context")]
    public string? Context { get; set; }

    [JsonPropertyName("state")]
    public string? StateRaw { get; set; }

    [JsonPropertyName("created_at")]
    public string? CreatedAt { get; set; }

    [JsonPropertyName("updated_at")]
    public string? UpdatedAt { get; set; }

    [JsonPropertyName("creator")]
    public User? Creator { get; set; }

    public DateTimeOffset GetCreatedAt() => DateTimeOffset.Parse(CreatedAt!);

    public DateTimeOffset GetUpdatedAt() => DateTimeOffset.Parse(UpdatedAt!);

    /// <exception cref="RepoException">if the state value cannot be parsed.</exception>
    public StatusState GetState()
    {
        if (StateRaw != null
            && Enum.TryParse<StatusState>(StateRaw, ignoreCase: true, out var parsed))
        {
            return parsed;
        }

        throw new RepoException(
            $"Unable to parse Status notification, got unexpected state value {StateRaw}");
    }

    [StarlarkMethod(
        "state",
        Doc = "The state of the commit status: success, failure, pending or error",
        StructField = true)]
    public string? GetStateForSkylark() => StateRaw?.ToLowerInvariant();

    [StarlarkMethod(
        "target_url",
        Doc = "Get the target url of the commit status. Can be None.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetTargetUrl() => TargetUrl;

    [StarlarkMethod(
        "description",
        Doc = "Description of the commit status. Can be None.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetDescription() => Description;

    [StarlarkMethod(
        "context",
        Doc = "Context of the commit status. This is a relatively stable id",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetContext() => Context;

    /// <summary>Not set in combined status.</summary>
    public User? GetCreator() => Creator;
}

/// <summary>State of the commit status.</summary>
[JsonConverter(typeof(JsonStringEnumConverter<StatusState>))]
public enum StatusState
{
    [JsonStringEnumMemberName("error")]
    ERROR,

    [JsonStringEnumMemberName("failure")]
    FAILURE,

    [JsonStringEnumMemberName("pending")]
    PENDING,

    [JsonStringEnumMemberName("success")]
    SUCCESS,
}

/// <summary>Helpers for <see cref="StatusState"/>.</summary>
public static class StatusStates
{
    public static readonly IReadOnlySet<string> ValidValues =
        Enum.GetValues<StatusState>()
            .Select(e => e.ToString().ToLowerInvariant())
            .ToImmutableHashSet();
}
