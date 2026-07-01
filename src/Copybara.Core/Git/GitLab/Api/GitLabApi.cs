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
using Copybara.Common;
using Copybara.Git.GitLab.Api.Entities;

namespace Copybara.Git.GitLab.Api;

/// <summary>An API used for interacting with the GitLab REST API.</summary>
public class GitLabApi
{
    private readonly IGitLabApiTransport _transport;

    public GitLabApi(IGitLabApiTransport transport)
    {
        _transport = transport;
    }

    /// <summary>
    /// Returns GitLab Project information for a given URL-encoded path. The path is defined as the
    /// group and the project separated by a '/', e.g. google/copybara.
    /// </summary>
    /// <param name="urlEncodedPath">the URL-encoded path of the project</param>
    /// <returns>the project response object, or <c>null</c> if none</returns>
    /// <seealso href="https://docs.gitlab.com/api/projects/#get-a-single-project"/>
    public Project? GetProject(string urlEncodedPath)
    {
        return _transport.Get<Project>(
            "projects/" + urlEncodedPath,
            ImmutableListMultimap<string, string>.Empty);
    }

    /// <summary>Returns information about a Merge Request for a GitLab project.</summary>
    /// <param name="projectId">The numeric project ID</param>
    /// <param name="mergeRequestId">The numeric Merge Request ID</param>
    /// <returns>the Merge Request object, or <c>null</c> if none</returns>
    /// <seealso href="https://docs.gitlab.com/api/merge_requests/#get-single-mr"/>
    public MergeRequest? GetMergeRequest(int projectId, int mergeRequestId)
    {
        return _transport.Get<MergeRequest>(
            "/projects/" + projectId + "/merge_requests/" + mergeRequestId,
            ImmutableListMultimap<string, string>.Empty);
    }

    /// <summary>Returns a list of Merge Requests for the given Project ID.</summary>
    /// <param name="projectId">the project id</param>
    /// <param name="parameters">the params to attach to this request</param>
    /// <returns>the list of Merge Requests</returns>
    public IReadOnlyList<MergeRequest> GetProjectMergeRequests(
        int projectId, ListProjectMergeRequestParams parameters)
    {
        return PaginatedGet<MergeRequest>(
            $"/projects/{projectId}/merge_requests",
            ImmutableListMultimap<string, string>.Empty,
            50,
            parameters);
    }

    /// <summary>
    /// Performs a GET request on the GitLab API, for the provided path, and handles the pagination
    /// of responses.
    /// </summary>
    protected IReadOnlyList<T> PaginatedGet<T>(
        string path,
        ImmutableListMultimap<string, string> headers,
        int perPageAmt,
        IGitLabApiParams urlQueryParams)
        where T : IGitLabApiEntity
    {
        path += ExtractQueryString(path) is not null ? "&" : "?";
        path += urlQueryParams.GetQueryString();
        return PaginatedGet<T>(path, headers, perPageAmt);
    }

    /// <summary>Returns information about a commit for a GitLab project.</summary>
    /// <param name="projectId">The ID or URL-encoded path of the project</param>
    /// <param name="refName">The commit hash or name of a repository branch or tag</param>
    /// <returns>the Commit object, or <c>null</c> if none</returns>
    /// <seealso href="https://docs.gitlab.com/ee/api/commits/#get-a-single-commit"/>
    public Commit? GetCommit(int projectId, string refName)
    {
        return _transport.Get<Commit>(
            "/projects/" + projectId + "/repository/commits/" + refName,
            ImmutableListMultimap<string, string>.Empty);
    }

    /// <summary>Returns a list of users that match the given criteria from the GitLab instance.</summary>
    /// <param name="parameters">the parameters to use in the request</param>
    /// <returns>a list of users</returns>
    /// <seealso href="https://docs.gitlab.com/api/users/#list-users">GitLab API List Users docs</seealso>
    public IReadOnlyList<User> GetListUsers(ListUsersParams parameters)
    {
        return PaginatedGet<User>(
            "users", ImmutableListMultimap<string, string>.Empty, 50, parameters);
    }

    /// <summary>
    /// Performs a GET request on the GitLab API, for the provided path, and handles the pagination
    /// of responses.
    /// </summary>
    protected IReadOnlyList<T> PaginatedGet<T>(
        string path, ImmutableListMultimap<string, string> headers, int perPageAmt)
        where T : IGitLabApiEntity
    {
        var response = ImmutableArray.CreateBuilder<T>();
        PaginatedPageList<T>? page =
            _transport.Get<PaginatedPageList<T>>(GetPathWithPerPageParam(path, perPageAmt), headers);

        while (page is not null)
        {
            response.AddRange(page);
            string? nextUrl = page.GetNextUrl();
            page = nextUrl is not null ? _transport.Get<PaginatedPageList<T>>(nextUrl, headers) : null;
        }

        return response.ToImmutable();
    }

    /// <summary>Creates a merge request via the GitLab API.</summary>
    /// <param name="parameters">the parameters to use in the request</param>
    /// <returns>the created merge request info, if returned by the API</returns>
    public MergeRequest? CreateMergeRequest(CreateMergeRequestParams parameters)
    {
        return _transport.Post<MergeRequest>(
            $"/projects/{parameters.ProjectId}/merge_requests",
            parameters,
            ImmutableListMultimap<string, string>.Empty);
    }

    /// <summary>Updates a merge request via the GitLab API.</summary>
    /// <param name="parameters">the parameters to use in the request</param>
    /// <returns>the updated merge request info, if returned by the API</returns>
    public MergeRequest? UpdateMergeRequest(UpdateMergeRequestParams parameters)
    {
        return _transport.Put<MergeRequest>(
            $"/projects/{parameters.ProjectId}/merge_requests/{parameters.MergeRequestIid}",
            parameters,
            ImmutableListMultimap<string, string>.Empty);
    }

    public SetExternalStatusCheckResponse? SetExternalStatusCheck(SetExternalStatusCheckParams parameters)
    {
        return _transport.Post<SetExternalStatusCheckResponse>(
            $"projects/{parameters.ProjectId}/merge_requests/{parameters.MergeRequestIid}/status_check_responses",
            parameters,
            ImmutableListMultimap<string, string>.Empty);
    }

    private static string GetPathWithPerPageParam(string path, int itemsPerPage)
    {
        var queryBuilder = new System.Text.StringBuilder(path);
        queryBuilder.Append(ExtractQueryString(path) is not null ? '&' : '?');
        queryBuilder.Append("per_page=").Append(itemsPerPage);
        return queryBuilder.ToString();
    }

    public static string? ExtractQueryString(string path)
    {
        int lastQuestionMarkIndex = path.LastIndexOf('?');
        if (lastQuestionMarkIndex == -1)
        {
            return null;
        }

        return path.Substring(lastQuestionMarkIndex + 1);
    }
}
