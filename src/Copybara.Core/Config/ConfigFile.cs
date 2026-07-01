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
using System.Text;
using Copybara.Exceptions;
using Copybara.Util;

namespace Copybara.Config;

/// <summary>
/// An object representing a configuration file and that it can be used to resolve
/// other config files relative to this one.
/// </summary>
public interface ConfigFile
{
    /// <summary>
    /// Check if the path is absolute and validates that the path is normalized.
    /// </summary>
    /// <exception cref="CannotResolveLabel">if the path is not normalized</exception>
    static bool IsAbsolute(string path)
    {
        bool isAbsolute = path.StartsWith("//", StringComparison.Ordinal);
        // Remove '//' for absolute paths
        string withoutPrefix = isAbsolute ? path.Substring(2) : path;
        try
        {
            FileUtil.CheckNormalizedRelative(withoutPrefix);
            return isAbsolute;
        }
        catch (ArgumentException e)
        {
            throw new CannotResolveLabel(
                string.Format("Invalid path '{0}': {1}", withoutPrefix, e.Message));
        }
    }

    /// <summary>
    /// Resolve <paramref name="path"/> relative to the current config file.
    /// </summary>
    /// <exception cref="CannotResolveLabel">if the path cannot be resolved to a content</exception>
    ConfigFile Resolve(string path);

    /// <summary>
    /// Resolve a set of configs paths in a batch. This can be used by implementors to check
    /// existence or preload the bytes of the content in batch/parallel.
    /// </summary>
    /// <param name="paths">a set of paths</param>
    /// <returns>a map from paths to <see cref="ConfigFile"/></returns>
    /// <exception cref="CannotResolveLabel">if any of the paths cannot be resolved</exception>
    ImmutableDictionary<string, ConfigFile> ResolveAll(IReadOnlySet<string> paths)
    {
        var result = ImmutableDictionary.CreateBuilder<string, ConfigFile>();
        foreach (string path in paths)
        {
            result[path] = Resolve(path);
        }
        return result.ToImmutable();
    }

    /// <summary>Resolved, non-relative name of the config file.</summary>
    string Path();

    /// <summary>
    /// Get the contents of the file.
    ///
    /// <para>Implementations of this interface should prefer to not eagerly load the content when
    /// this method is called in order to allow the callers to check their own cache if they already
    /// have <see cref="Path"/>.</para>
    /// </summary>
    byte[] ReadContentBytes();

    /// <summary>Utility function to read the content of the config file as String.</summary>
    string ReadContent() => Encoding.UTF8.GetString(ReadContentBytes());

    /// <summary>
    /// Return a string representing a stable identifier that works between different
    /// <see cref="ConfigFile"/> implementations. Note that this is best effort based on several
    /// heuristics.
    ///
    /// <para>If root is not defined or cannot be computed, it will return the absolute path.</para>
    ///
    /// <para>Users of this method should not try to parse the string, since it is subject to
    /// change.</para>
    /// </summary>
    string GetIdentifier();
}
