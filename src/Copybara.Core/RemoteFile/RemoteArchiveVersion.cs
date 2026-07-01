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

namespace Copybara.RemoteFile;

/// <summary>A class to represent a version for RemoteArchive endpoints.</summary>
public sealed class RemoteArchiveVersion
{
    private readonly string _fullUrl;
    private readonly string? _version;

    public RemoteArchiveVersion(string fullUrl, string? version)
    {
        _version = version;
        _fullUrl = fullUrl;
    }

    public string GetFullUrl() => _fullUrl;

    public string? GetVersion() => _version;
}
