/*
 * Copyright (C) 2025 Google LLC.
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

namespace Copybara.Git.GitLab.Api.Entities;

/// <summary>Params used when requesting a list of project merge requests from GitLab.</summary>
/// <seealso href="https://docs.gitlab.com/api/merge_requests/#list-project-merge-requests"/>
public sealed record ListProjectMergeRequestParams(string? SourceBranch) : IGitLabApiParams
{
    /// <summary>Creates a <see cref="ListProjectMergeRequestParams"/> instance with no params set.</summary>
    public static ListProjectMergeRequestParams GetDefaultInstance() => new((string?)null);

    public IReadOnlyList<Param> Params()
    {
        var builder = ImmutableArray.CreateBuilder<Param>();
        if (SourceBranch is not null)
        {
            builder.Add(new Param("source_branch", SourceBranch));
        }

        return builder.ToImmutable();
    }
}
