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
using Copybara.Exceptions;
using SysPath = System.IO.Path;

namespace Copybara.Config;

/// <summary>
/// A Skylark dependency resolver that resolves relative paths and absolute paths if
/// <c>rootPath</c> is defined.
/// </summary>
public class PathBasedConfigFile : ConfigFile
{
    private readonly string _path;
    private readonly string? _rootPath;
    private readonly string? _identifierPrefix;

    public PathBasedConfigFile(string path, string? rootPath, string? identifierPrefix)
    {
        Preconditions.CheckArgument(SysPath.IsPathRooted(path), "path must be absolute");
        _path = path;
        _rootPath = rootPath;
        _identifierPrefix = identifierPrefix;
        if (identifierPrefix != null)
        {
            // Check we don't generate weird identifiers like identifierPrefix + "/absolute/path"
            Preconditions.CheckNotNull(rootPath, "identifierPrefix requires a non null root");
        }
    }

    public ConfigFile Resolve(string path)
    {
        string resolved = ConfigFile.IsAbsolute(path)
            ? RelativeToRoot(path)
            : RelativeToCurrentPath(path);

        if (!File.Exists(resolved) && !Directory.Exists(resolved))
        {
            throw new CannotResolveLabel(
                string.Format("Cannot find '{0}'. '{1}' does not exist.", path, resolved));
        }
        if (!File.Exists(resolved))
        {
            throw new CannotResolveLabel(
                string.Format("Cannot find '{0}'. '{1}' is not a file.", path, resolved));
        }
        return new PathBasedConfigFile(resolved, _rootPath, _identifierPrefix);
    }

    public string Path() => _path;

    public string GetIdentifier()
    {
        if (_rootPath == null)
        {
            return Path();
        }

        return (string.IsNullOrEmpty(_identifierPrefix) ? "" : _identifierPrefix + "/")
            + Relativize(_rootPath, _path);
    }

    private string RelativeToCurrentPath(string label)
    {
        string? dir = SysPath.GetDirectoryName(_path);
        return dir == null ? label : SysPath.Combine(dir, label);
    }

    private string RelativeToRoot(string path)
    {
        if (_rootPath == null)
        {
            throw new CannotResolveLabel(
                "Absolute paths are not allowed because the root config path couldn't be"
                + " automatically detected. Use " + GeneralOptions.ConfigRootFlag);
        }
        return SysPath.Combine(_rootPath, path.Substring(2));
    }

    public byte[] ReadContentBytes()
    {
        try
        {
            return File.ReadAllBytes(_path);
        }
        catch (FileNotFoundException e)
        {
            throw new CannotResolveLabel("Cannot resolve " + _path, e);
        }
        catch (DirectoryNotFoundException e)
        {
            throw new CannotResolveLabel("Cannot resolve " + _path, e);
        }
    }

    public override string ToString() =>
        $"PathBasedConfigFile{{path={_path}, rootPath={_rootPath}, identifierPrefix={_identifierPrefix}}}";

    private static string Relativize(string root, string path)
    {
        string rel = SysPath.GetRelativePath(root, path);
        // Normalize to forward slashes so identifiers are stable across platforms.
        return rel.Replace(SysPath.DirectorySeparatorChar, '/');
    }
}
