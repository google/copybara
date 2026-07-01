/*
 * Copyright (C) 2017 Google Inc.
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

/// <summary>A user's permission level at a GitHub repos.</summary>
public class UserPermissionLevel
{
    /// <summary>Type of User permission level at a GitHub Repos.</summary>
    public enum GitHubUserPermission
    {
        ADMIN,
        WRITE,
        READ,
        NONE,
    }

    [JsonPropertyName("user")]
    public User? UserValue { get; set; }

    [JsonPropertyName("permission")]
    public string? Permission { get; set; }

    public UserPermissionLevel()
    {
    }

    public UserPermissionLevel(User user, string permission)
    {
        UserValue = user;
        Permission = permission;
    }

    public GitHubUserPermission GetPermission() =>
        Permission == null
            ? GitHubUserPermission.NONE
            : Enum.Parse<GitHubUserPermission>(Permission.ToUpperInvariant());

    public User? GetUser() => UserValue;

    public override string ToString() =>
        $"UserPermissionLevel{{permission={GetPermission()}, user={UserValue}}}";
}
