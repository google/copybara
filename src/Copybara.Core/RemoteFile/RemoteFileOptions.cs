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

using Copybara.Exceptions;

namespace Copybara.RemoteFile;

/// <summary>Options for loading files from a source other than the origin. Use with caution.</summary>
public class RemoteFileOptions : IOption
{
    [Flag(
        "--remote-http-files-connection-timeout",
        "Timeout for the fetch operation, e.g. 30s.")]
    protected TimeSpan ConnectionTimeout { get; set; } = TimeSpan.FromMinutes(2);

    private readonly Lazy<IHttpStreamFactory> _transport;

    public RemoteFileOptions()
    {
        _transport = new Lazy<IHttpStreamFactory>(
            () => new GclientHttpStreamFactory(ConnectionTimeout));
    }

    /// <exception cref="ValidationException"/>
    public IHttpStreamFactory GetTransport() => _transport.Value;
}
