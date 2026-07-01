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

using Copybara.Exceptions;

namespace Copybara.Git.GitLab.Api;

/// <summary>Exception that contains the error message and other information from GitLab.</summary>
public class GitLabApiException : RepoException
{
    private readonly int? _responseCode;

    public GitLabApiException(string message, int responseCode, Exception? cause)
        : base(message, cause)
    {
        _responseCode = responseCode;
    }

    public GitLabApiException(string message, int responseCode)
        : base(message)
    {
        _responseCode = responseCode;
    }

    public GitLabApiException(string message, Exception? cause)
        : base(message, cause)
    {
        _responseCode = null;
    }

    public int? GetResponseCode() => _responseCode;
}
