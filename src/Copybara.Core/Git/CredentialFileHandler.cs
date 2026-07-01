/*
 * Copyright (C) 2024 Google LLC.
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
using System.Text;
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Credentials;
using Copybara.Exceptions;

namespace Copybara.Git;

/// <summary>
/// Holder to handle https access tokens for Git Repos. Port of
/// <c>com.google.copybara.git.CredentialFileHandler</c>.
/// </summary>
public class CredentialFileHandler
{
    private static readonly object FileLock = new();

    private readonly string _scheme;
    private readonly string _host;
    private readonly string _path;
    private readonly CredentialIssuer _username;
    private readonly CredentialIssuer _password;
    private Credential? _currentPassword;
    private Credential? _currentUsername;
    private readonly object _sync = new();

    private readonly bool _enabled;

    public CredentialFileHandler(
        string scheme,
        string host,
        string path,
        CredentialIssuer username,
        CredentialIssuer password,
        bool enabled)
    {
        _scheme = Preconditions.CheckNotNull(scheme);
        _host = Preconditions.CheckNotNull(host);
        _path = Preconditions.CheckNotNull(path);
        _username = Preconditions.CheckNotNull(username);
        _password = Preconditions.CheckNotNull(password);
        _enabled = enabled;
    }

    public CredentialFileHandler(
        string host, string path, CredentialIssuer username, CredentialIssuer password, bool enable)
        : this("https", host, path, username, password, enable)
    {
    }

    public CredentialFileHandler(
        string host, string path, CredentialIssuer username, CredentialIssuer password)
        : this("https", host, path, username, password, true)
    {
    }

    public CredentialFileHandler(
        string scheme, string host, string path, CredentialIssuer username, CredentialIssuer password)
        : this(scheme, host, path, username, password, true)
    {
    }

    /// <summary>Obtain a token for the username field from the username Issuer.</summary>
    public string GetUsername() => GetUsernameCred().ProvideSecret();

    /// <summary>Obtain a token for the password field from the password Issuer.</summary>
    public string GetPassword() => GetPasswordCred().ProvideSecret();

    private Credential GetPasswordCred()
    {
        lock (_sync)
        {
            if (_currentPassword == null || !_currentPassword.Valid())
            {
                _currentPassword = _password.Issue();
            }
            return _currentPassword;
        }
    }

    private Credential GetUsernameCred()
    {
        lock (_sync)
        {
            if (_currentUsername == null || !_currentUsername.Valid())
            {
                _currentUsername = _username.Issue();
            }
            return _currentUsername;
        }
    }

    public void Install(GitRepository repo, string credentialHelper)
    {
        if (!_enabled)
        {
            return;
        }
        repo.ReplaceLocalConfigField("credential", "useHttpPath", "true");
        WriteTokenToCredFile(credentialHelper);
        repo.WithCredentialHelper("store --file=" + credentialHelper);
    }

    /// <summary>
    /// Writes an entry for the token into the given creds file. If the token has expired, calling
    /// this again will update the token.
    /// </summary>
    public void WriteTokenToCredFile(string file)
    {
        lock (FileLock)
        {
            try
            {
                var lines = new List<string>();
                if (File.Exists(file))
                {
                    lines.AddRange(File.ReadAllLines(file));
                }
                string entry;
                Regex pattern;
                try
                {
                    entry = GetCredentialEntry(GetPasswordCred().ProvideSecret());
                    pattern = new Regex(
                        "^"
                        + Regex.Escape(GetCredentialEntry("PASSWORD_PLACEHOLDER"))
                            .Replace("PASSWORD_PLACEHOLDER", "[^@]+")
                        + "$");
                }
                catch (Exception e)
                    when (e is CredentialRetrievalException or CredentialIssuingException)
                {
                    throw new RepoException("Issue minting token", e);
                }
                bool missing = true;
                for (int i = 0; i < lines.Count; i++)
                {
                    string line = lines[i];
                    if (line == entry)
                    {
                        return;
                    }
                    if (pattern.IsMatch(line))
                    {
                        lines[i] = entry;
                        missing = false;
                    }
                }
                if (missing)
                {
                    lines.Add(entry);
                }
                File.WriteAllText(
                    file,
                    string.Join("\n", lines.Where(s => !string.IsNullOrEmpty(s))) + "\n");
            }
            catch (IOException e)
            {
                throw new RepoException($"Error writing access token for {_host}/{_path}", e);
            }
        }
    }

    private string GetCredentialEntry(string pw) =>
        $"{_scheme}://{Uri.EscapeDataString(GetUsername())}:{Uri.EscapeDataString(pw)}@{_host}/{_path}";

    /// <summary>
    /// Helper to print a cred files without exposing tokens. Do not use this output for anything but
    /// debugging.
    /// </summary>
    public string GetScrubbedFileContentForDebug(string file)
    {
        if (!File.Exists(file))
        {
            return "<does not exist>";
        }
        try
        {
            return ScrubCredential(File.ReadAllText(file));
        }
        catch (IOException e)
        {
            return "<IOException: " + e + ">";
        }
    }

    private static string ScrubCredential(string line) =>
        Regex.Replace(
            line, "([^:\\n]+://[^:\\n]+):[^@\\n]+(@[^\\n]*)", "$1:<scrubbed>$2");

    public override string ToString() =>
        $"CredentialFileHandler{{host={_host}, path={_path}, password={_password.Describe()},"
            + $" username={_username.Describe()}}}";

    public IReadOnlyList<ImmutableSetMultimap<string, string>> DescribeCredentials() =>
        ImmutableArray.Create(_username.Describe(), _password.Describe());
}
