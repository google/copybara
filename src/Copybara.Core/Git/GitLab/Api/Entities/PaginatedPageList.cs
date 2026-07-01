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

using System.Net.Http.Headers;
using System.Text.RegularExpressions;
using Copybara.Common;

namespace Copybara.Git.GitLab.Api.Entities;

/// <summary>
/// Non-generic view of a <see cref="PaginatedPageList{T}"/> so the transport can annotate the
/// pagination info without knowing the element type at compile time.
/// </summary>
public interface IPaginatedPageList : IGitLabApiEntity
{
    /// <summary>Returns the "next" URL that this object is annotated with, if any.</summary>
    string? GetNextUrl();

    /// <summary>Annotates a copy of this list with the "next" link derived from the headers.</summary>
    IPaginatedPageList WithPaginatedInfo(string apiUrl, HttpResponseHeaders httpHeaders);
}

/// <summary>
/// A list that can contain information on retrieving the next page of a paginated response.
///
/// <para>This class is intended to represent a partial response set (i.e. a page), where the next
/// URL can be followed to obtain further elements in all pages.</para>
/// </summary>
/// <typeparam name="T">the type of elements in the list</typeparam>
public class PaginatedPageList<T> : List<T>, IPaginatedPageList
{
    private static readonly Regex LinkHeaderPattern =
        new("<([^>]+)>; rel=\"([a-z]+)\"", RegexOptions.Compiled);

    private readonly string? _nextUrl;

    public PaginatedPageList()
        : this(Array.Empty<T>(), null)
    {
    }

    private PaginatedPageList(IEnumerable<T> elements, string? nextUrl)
        : base(elements)
    {
        _nextUrl = nextUrl;
    }

    /// <summary>Returns the "next" URL that this object is annotated with, if any.</summary>
    public string? GetNextUrl() => _nextUrl;

    /// <summary>
    /// Annotates this <see cref="PaginatedPageList{T}"/> instance with the "next" link from the
    /// provided header.
    /// </summary>
    /// <param name="apiUrl">the URL of the API endpoint, used to verify that the "next" link points
    ///     to the correct endpoint</param>
    /// <param name="httpHeaders">the response headers</param>
    /// <returns>the new list, with the "next" link set</returns>
    IPaginatedPageList IPaginatedPageList.WithPaginatedInfo(string apiUrl, HttpResponseHeaders httpHeaders) =>
        WithPaginatedInfo(apiUrl, httpHeaders);

    /// <inheritdoc cref="IPaginatedPageList.WithPaginatedInfo"/>
    public PaginatedPageList<T> WithPaginatedInfo(string apiUrl, HttpResponseHeaders httpHeaders)
    {
        if (!httpHeaders.TryGetValues("link", out var values))
        {
            return this;
        }

        string? linkHeader = values.SingleOrDefault();
        if (linkHeader is null)
        {
            return this;
        }

        var links = new Dictionary<string, string>();
        foreach (string rawLink in linkHeader.Split(','))
        {
            string link = rawLink.Trim();
            if (link.Length == 0)
            {
                continue;
            }

            Match matcher = LinkHeaderPattern.Match(link);
            Preconditions.CheckState(
                matcher.Success && matcher.Length == link.Length,
                "'{0}' does not match link header regex.",
                link);
            string url = matcher.Groups[1].Value;
            string rel = matcher.Groups[2].Value;
            Preconditions.CheckState(
                url.StartsWith(apiUrl, StringComparison.Ordinal),
                "{0} doesn't start with {1}",
                url,
                apiUrl);
            // key is the "rel" value (e.g. next, prev). Value is the URL.
            links[rel] = url.Substring(apiUrl.Length);
        }

        links.TryGetValue("next", out string? next);
        return new PaginatedPageList<T>(this, next);
    }
}
