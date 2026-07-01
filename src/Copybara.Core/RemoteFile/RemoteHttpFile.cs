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

using System.Security.Cryptography;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Copybara.Profiler;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.RemoteFile;

/// <summary>A Starlark construct to download remote files via HTTP.</summary>
[StarlarkBuiltin(
    "remote_http_file",
    Documented = false,
    Doc = "A file loaded via http(s). This is experimental.")]
public abstract class RemoteHttpFile : IStarlarkValue
{
    protected readonly string Reference;
    private readonly IHttpStreamFactory _transport;
    private readonly Console _console;
    protected readonly Profiler.Profiler Profiler;
    private readonly IAuthInterceptor? _auth;

    protected string? Sha256;
    protected bool Downloaded;

    protected RemoteHttpFile(
        string reference,
        IHttpStreamFactory transport,
        Console console,
        Profiler.Profiler profiler,
        IAuthInterceptor? auth)
    {
        Reference = Preconditions.CheckNotNull(reference);
        _transport = Preconditions.CheckNotNull(transport);
        _console = Preconditions.CheckNotNull(console);
        Profiler = Preconditions.CheckNotNull(profiler);
        _auth = auth;
    }

    /// <summary>Obtain the URL to download the file from.</summary>
    /// <exception cref="ValidationException"/>
    protected abstract Uri GetRemote();

    /// <summary>Sink that receives the downloaded files.</summary>
    /// <exception cref="ValidationException"/>
    protected abstract Stream GetSink();

    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    protected void Download()
    {
        lock (this)
        {
            if (Downloaded)
            {
                return;
            }
            Uri remote = GetRemote();
            try
            {
                _console.ProgressFmt("Fetching {0}", remote);
                using Stream sink = GetSink();
                using var digest = SHA256.Create();
                using (Profiler.Start("remote_file_" + remote))
                {
                    using (Stream source = _transport.Open(remote, _auth))
                    using (var digestStream = new CryptoStream(sink, digest, CryptoStreamMode.Write, leaveOpen: true))
                    {
                        source.CopyTo(digestStream);
                        digestStream.FlushFinalBlock();
                    }
                    Sha256 = Convert.ToHexStringLower(digest.Hash!);
                    Downloaded = true;
                }
            }
            catch (IOException e)
            {
                throw new RepoException($"Error downloading {remote}", e);
            }
        }
    }

    [StarlarkMethod("sha256", Documented = false, Doc = "Sha256 of the file.")]
    public string GetSha256()
    {
        Download();
        return Sha256!;
    }
}
