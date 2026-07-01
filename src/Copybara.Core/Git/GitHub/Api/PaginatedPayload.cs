/*
 * Copyright (C) 2023 Google Inc.
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

namespace Copybara.Git.GitHub.Api;

/// <summary>JSON response that contains a nested paginated payload.</summary>
/// <remarks>
/// Port of <c>com.google.copybara.git.github.api.PaginatedPayload</c>. In the Java version this is a
/// generic interface parameterized on the element type. Because the C# transport dispatches on the
/// concrete response type, we expose a non-generic marker (<see cref="IPaginatedPayload"/>) that the
/// transport can detect, plus the generic <see cref="IPaginatedPayload{T}"/> that callers use.
/// </remarks>
public interface IPaginatedPayload
{
    /// <summary>
    /// Add prev/next info from HTTP headers, returning a new instance with the data filled.
    /// </summary>
    IPaginatedPayload AnnotatePayload(string apiPrefix, string? linkHeader);
}

/// <summary>JSON response that contains a nested paginated payload of <typeparamref name="T"/>.</summary>
public interface IPaginatedPayload<T> : IPaginatedPayload
{
    /// <summary>Return the list of paginated entities.</summary>
    PaginatedList<T> GetPayload();
}
