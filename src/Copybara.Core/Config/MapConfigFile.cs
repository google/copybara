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

using System.Collections.Immutable;
using Copybara.Exceptions;

namespace Copybara.Config;

/// <summary>
/// A Config file implementation that uses a map for storing the internal data structure.
///
/// <para>Assumes all paths to be absolute.</para>
/// </summary>
public class MapConfigFile : ConfigFile
{
    private readonly ImmutableDictionary<string, byte[]> _configFiles;
    private readonly string _current;

    public MapConfigFile(ImmutableDictionary<string, byte[]> configFiles, string current)
    {
        _configFiles = configFiles;
        _current = current;
    }

    public ConfigFile Resolve(string path)
    {
        string resolved = ConfigFile.IsAbsolute(path)
            ? ContainsLabel(path.Substring(2))
            : RelativeToCurrentPath(path);
        if (!_configFiles.ContainsKey(resolved))
        {
            throw new CannotResolveLabel(
                string.Format("Cannot resolve '{0}': '{1}' does not exist.", path, resolved));
        }
        return new MapConfigFile(_configFiles, resolved);
    }

    public string Path() => _current;

    public string GetIdentifier() => Path();

    public byte[] ReadContentBytes() => _configFiles[_current];

    public override string ToString() =>
        $"MapConfigFile{{current={_current}, configFiles=[{string.Join(", ", _configFiles.Keys)}]}}";

    private string RelativeToCurrentPath(string label)
    {
        int i = _current.LastIndexOf('/');
        string resolved = i == -1 ? label : _current.Substring(0, i) + "/" + label;
        return ContainsLabel(resolved);
    }

    private string ContainsLabel(string resolved)
    {
        if (!_configFiles.ContainsKey(resolved))
        {
            throw new CannotResolveLabel(
                string.Format("Cannot resolve '{0}': does not exist.", resolved));
        }
        return resolved;
    }
}
