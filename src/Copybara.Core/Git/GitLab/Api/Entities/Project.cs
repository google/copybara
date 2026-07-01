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

using System.Text.Json.Serialization;

namespace Copybara.Git.GitLab.Api.Entities;

/// <summary>Represents a GitLab project.</summary>
/// <seealso href="https://docs.gitlab.com/api/projects/#get-a-single-project"/>
public class Project : IGitLabApiEntity
{
    [JsonPropertyName("id")]
    public int Id { get; set; }

    /// <summary>Creates a new instance of <see cref="Project"/>.</summary>
    public Project()
    {
    }

    /// <summary>Constructs a new instance of <see cref="Project"/> with the given parameters.</summary>
    /// <param name="id">the project ID</param>
    public Project(int id)
    {
        Id = id;
    }

    /// <summary>Returns the numeric ID of the project.</summary>
    public int GetId() => Id;
}
