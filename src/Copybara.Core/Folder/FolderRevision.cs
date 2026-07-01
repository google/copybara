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

using Copybara.Common;
using Copybara.Revision;

namespace Copybara.Folder;

/// <summary>A reference for folder origins.</summary>
public class FolderRevision : IRevision
{
    internal readonly string Path;
    private readonly DateTimeOffset _timestamp;
    internal readonly string? Version;

    public FolderRevision(string path, DateTimeOffset timestamp)
    {
        Preconditions.CheckState(System.IO.Path.IsPathRooted(path), "Path must be absolute");
        Path = path;
        _timestamp = timestamp;
        Version = null;
    }

    internal FolderRevision(string path, DateTimeOffset timestamp, string? version)
    {
        Preconditions.CheckState(System.IO.Path.IsPathRooted(path), "Path must be absolute");
        Path = path;
        _timestamp = timestamp;
        Version = version;
    }

    public string AsString() => Version ?? Path;

    public DateTimeOffset? ReadTimestamp() => _timestamp;
}
