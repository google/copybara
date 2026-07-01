/*
 * Copyright (C) 2020 Google Inc.
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

using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.RemoteFile;

/// <summary>A tarball for a given ref, downloaded from GitHub.</summary>
[StarlarkBuiltin(
    "remote_http_file.GitHubArchive",
    Documented = false,
    Doc = "A GitHub archive that can be downloaded at the given revision. Only exposes the SHA256 "
        + "hash of the archive.")]
public class GithubArchive : RemoteHttpFile
{
    private readonly string _project;
    private readonly ArchiveType _fileType;

    public GithubArchive(
        string project,
        string reference,
        ArchiveType fileType,
        IHttpStreamFactory transport,
        Profiler.Profiler profiler,
        Console console,
        IAuthInterceptor? auth)
        : base(reference, transport, console, profiler, auth)
    {
        _project = Preconditions.CheckNotNull(project);
        _fileType = fileType;
    }

    protected override Uri GetRemote()
    {
        try
        {
            // This is somewhat limited and does not support private repos. We can use
            // https://developer.github.com/v3/repos/contents/#get-archive-link if a use case for
            // private repos comes up.
            return new Uri(string.Format(
                "https://github.com/{0}/archive/{1}.{2}",
                _project, Reference, _fileType.Extension()));
        }
        catch (UriFormatException e)
        {
            throw new ValidationException(
                $"Error assembling URL for archive of {_project} at {Reference}", e);
        }
    }

    protected override Stream GetSink() => Stream.Null;

    /// <summary>We only need the hash of archives as we do not allow introspecting them.</summary>
    public enum ArchiveType
    {
        TARBALL,
        ZIP,
    }
}

internal static class ArchiveTypeExtensions
{
    public static string Extension(this GithubArchive.ArchiveType type) =>
        type switch
        {
            GithubArchive.ArchiveType.TARBALL => "tar.gz",
            GithubArchive.ArchiveType.ZIP => "zip",
            _ => throw new ArgumentOutOfRangeException(nameof(type), type, null),
        };
}
