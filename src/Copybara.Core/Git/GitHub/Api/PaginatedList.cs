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

using System.Collections;
using System.Text.RegularExpressions;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// A list that contains additional information on how to fetch the next/prev page.
/// </summary>
/// <remarks>
/// Port of <c>com.google.copybara.git.github.api.PaginatedList</c>. In Java this extends
/// <c>ArrayList</c>; here it wraps a backing list and implements <see cref="IReadOnlyList{T}"/>.
/// When a JSON array is deserialized directly into this type (the "bare list" endpoints), the
/// backing list is populated by <see cref="PaginatedListJsonConverter{T}"/>.
/// </remarks>
public sealed class PaginatedList<T> : IReadOnlyList<T>, IPaginatedPayload<T>
{
    private static readonly Regex LinkHeaderPattern =
        new("<([^>]+)>; rel=\"([a-z]+)\"", RegexOptions.Compiled);

    private readonly IReadOnlyList<T> _elements;

    public PaginatedList()
        : this(Array.Empty<T>(), null, null, null, null)
    {
    }

    public PaginatedList(IEnumerable<T> elements)
        : this(new List<T>(elements), null, null, null, null)
    {
    }

    private PaginatedList(
        IReadOnlyList<T> elements,
        string? firstUrl,
        string? prevUrl,
        string? nextUrl,
        string? lastUrl)
    {
        _elements = elements;
        FirstUrl = firstUrl;
        PrevUrl = prevUrl;
        NextUrl = nextUrl;
        LastUrl = lastUrl;
    }

    public string? NextUrl { get; }

    public string? PrevUrl { get; }

    public string? LastUrl { get; }

    public string? FirstUrl { get; }

    /// <summary>The paginated entities of this page.</summary>
    public IReadOnlyList<T> GetElements() => _elements;

    public int Count => _elements.Count;

    public T this[int index] => _elements[index];

    public IEnumerator<T> GetEnumerator() => _elements.GetEnumerator();

    IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();

    /// <summary>
    /// Return a <see cref="PaginatedList{T}"/> with the next/last/etc. fields populated if
    /// <paramref name="linkHeader"/> is not null.
    /// </summary>
    public PaginatedList<T> WithPaginationInfo(string apiPrefix, string? linkHeader)
    {
        if (linkHeader == null)
        {
            return this;
        }

        string? next = null;
        string? prev = null;
        string? last = null;
        string? first = null;
        foreach (string rawEntry in linkHeader.Split(','))
        {
            string entry = rawEntry.Trim();
            Match matcher = LinkHeaderPattern.Match(entry);
            if (!matcher.Success)
            {
                throw new InvalidOperationException(
                    $"'{entry}' doesn't match Link regex. Header: {linkHeader}");
            }

            string url = matcher.Groups[1].Value;
            string rel = matcher.Groups[2].Value;
            if (!url.StartsWith(apiPrefix, StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    $"Expected '{url}' to start with '{apiPrefix}'");
            }

            url = url.Substring(apiPrefix.Length);
            switch (rel)
            {
                case "first":
                    first = url;
                    break;
                case "prev":
                    prev = url;
                    break;
                case "next":
                    next = url;
                    break;
                case "last":
                    last = url;
                    break;
                default:
                    // fall out
                    break;
            }
        }

        return new PaginatedList<T>(_elements, first, prev, next, last);
    }

    public PaginatedList<T> GetPayload() => this;

    public IPaginatedPayload AnnotatePayload(string apiPrefix, string? linkHeader) =>
        WithPaginationInfo(apiPrefix, linkHeader);
}
