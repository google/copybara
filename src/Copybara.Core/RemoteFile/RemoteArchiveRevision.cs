/*
 * Copyright (C) 2022 Google Inc.
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
using Copybara.Revision;

namespace Copybara.RemoteFile;

/// <summary>A <see cref="IRevision"/> for a remote file.</summary>
public class RemoteArchiveRevision : IRevision
{
    private const string ArchiveVersionLabel = "ARCHIVE_VERSION";
    private const string ArchiveFullUrlLabel = "ARCHIVE_FULL_URL ";

    internal readonly RemoteArchiveVersion Version;

    public RemoteArchiveRevision(RemoteArchiveVersion version)
    {
        Version = version;
    }

    public string? GetUrl() => Version.GetFullUrl();

    public DateTimeOffset? ReadTimestamp() => null;

    public string AsString() =>
        !string.IsNullOrEmpty(Version.GetVersion())
            ? Version.GetVersion()!
            : Version.GetFullUrl();

    public string? ContextReference() => Version.GetVersion();

    public string? FixedReference() => AsString();

    public ImmutableListMultimap<string, string> AssociatedLabels()
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put(ArchiveVersionLabel, Version.GetVersion() ?? "");
        builder.Put(ArchiveFullUrlLabel, GetUrl() ?? "");
        return builder.Build();
    }
}
