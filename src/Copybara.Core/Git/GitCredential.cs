/*
 * Copyright (C) 2016 Google Inc.
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
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Util;

namespace Copybara.Git;

/// <summary>
/// Utility class for executing 'git credential' commands. Port of
/// <c>com.google.copybara.git.GitCredential</c>.
/// </summary>
public sealed class GitCredential
{
    private static readonly Regex NewLine = new(@"\r\n|\n|\r", RegexOptions.Compiled);

    private readonly TimeSpan _timeout;
    private readonly GitEnvironment _gitEnv;

    public GitCredential(TimeSpan timeout, GitEnvironment gitEnv)
    {
        _timeout = timeout;
        _gitEnv = Preconditions.CheckNotNull(gitEnv);
    }

    /// <summary>
    /// Execute 'git credential fill' for a url.
    /// </summary>
    /// <param name="gitDir">the directory to execute the command in. This is important if credential
    ///     configuration is set in the local git config.</param>
    /// <param name="url">url to get the credentials from</param>
    /// <returns>a username and password</returns>
    /// <exception cref="RepoException">If the url doesn't have a protocol, is not valid, or the
    ///     username/password couldn't be found.</exception>
    public UserPassword Fill(string gitDir, string url)
    {
        var env = _gitEnv.WithNoGitPrompt().GetEnvironment();

        Uri uri;
        try
        {
            uri = new Uri(url);
        }
        catch (Exception e) when (e is UriFormatException or InvalidOperationException)
        {
            throw new ValidationException("Cannot get credentials for " + url, e);
        }
        string? protocol = uri.Scheme;
        ValidationException.CheckCondition(
            !string.IsNullOrEmpty(protocol), "Cannot find the protocol for %s", url);
        string host = uri.Host;

        var cmd = new Command(
            new[] { _gitEnv.ResolveGitBinary(), "--git-dir=" + gitDir, "credential", "fill" },
            env,
            gitDir);

        var request = new StringBuilder();
        request.Append($"protocol={protocol}\nhost={host}\n");
        if (!string.IsNullOrEmpty(uri.AbsolutePath))
        {
            request.Append($"path={uri.AbsolutePath.TrimStart('/')}\n");
        }
        request.Append('\n');

        CommandOutputWithStatus result;
        try
        {
            // DON'T LOG THE OUTPUT. WE DON'T WANT TO ACCIDENTALLY LOG THE PASSWORD!
            result = new CommandRunner(cmd, _timeout)
                .WithMaxStdOutLogLines(0)
                .WithInput(Encoding.UTF8.GetBytes(request.ToString()))
                .Execute();
        }
        catch (BadExitStatusWithOutputException e)
        {
            string errStr = e.GetOutput().GetStderr();
            ValidationException.CheckCondition(
                !errStr.Contains("could not read"),
                "Interactive prompting of passwords for git is disabled,"
                    + " use git credential store before calling Copybara.");
            throw new RepoException("Error getting credentials:\n" + errStr, e);
        }
        catch (CommandException e)
        {
            throw new RepoException("Error getting credentials", e);
        }

        var map = new Dictionary<string, string>();
        foreach (var line in NewLine.Split(result.GetStdout()))
        {
            if (line.Length == 0)
            {
                continue;
            }
            int idx = line.IndexOf('=');
            if (idx < 0)
            {
                continue;
            }
            map[line.Substring(0, idx)] = line.Substring(idx + 1);
        }

        if (!map.TryGetValue("username", out var username))
        {
            throw new RepoException("git credentials for " + url + " didn't return a username");
        }
        if (!map.TryGetValue("password", out var password))
        {
            throw new RepoException("git credentials for " + url + " didn't return a password");
        }
        return new UserPassword(username, password);
    }

    /// <summary>A class that contains a username and password for git repositories.</summary>
    public sealed class UserPassword
    {
        private readonly string _username;
        private readonly string _password;

        internal UserPassword(string username, string password)
        {
            _username = Preconditions.CheckNotNull(username);
            _password = Preconditions.CheckNotNull(password);
        }

        // DON'T CHANGE THIS: never expose the password in ToString.
        public override string ToString() =>
            $"UserPassword{{username={_username}, password=(hidden)}}";

        public string GetUsername() => _username;

        /// <summary>Get the password. BE CAREFUL AND DON'T LOG IT!</summary>
        public string GetPasswordBeCareful() => _password;
    }
}
