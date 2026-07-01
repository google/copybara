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

using System.Text;

namespace Copybara.Git.GitLab;

/// <summary>
/// Utility class for GitLab endpoints. Port of
/// <c>com.google.copybara.git.gitlab.GitLabUtil</c>.
/// </summary>
public static class GitLabUtil
{
    /// <summary>
    /// Returns the URL-encoded project path for a given GitLab repository URL.
    ///
    /// <para>The project path is defined as the namespace plus the project name.</para>
    ///
    /// <para>The URL encoded project path is used for querying merge requests, and possibly other
    /// entities, from the public REST API.</para>
    /// </summary>
    /// <param name="repoUrl">the URL to extract the project path from.</param>
    /// <returns>the encoded project path.</returns>
    public static string GetUrlEncodedProjectPath(Uri repoUrl)
    {
        string path = repoUrl.AbsolutePath.Trim().ToLowerInvariant();
        // Remove any leading '/'.
        while (path.StartsWith('/'))
        {
            path = path.Substring(1);
        }
        // Remove any trailing .git.
        if (path.EndsWith(".git", StringComparison.Ordinal))
        {
            path = path.Substring(0, path.Length - ".git".Length);
        }
        return UrlEncode(path);
    }

    // Mirrors java.net.URLEncoder.encode(path, UTF_8) (application/x-www-form-urlencoded).
    private static string UrlEncode(string value)
    {
        var sb = new StringBuilder();
        foreach (byte b in Encoding.UTF8.GetBytes(value))
        {
            char c = (char)b;
            if ((c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c is '-' or '_' or '.' or '*')
            {
                sb.Append(c);
            }
            else if (c == ' ')
            {
                sb.Append('+');
            }
            else
            {
                sb.Append('%');
                sb.Append(((int)b).ToString("X2"));
            }
        }
        return sb.ToString();
    }
}
