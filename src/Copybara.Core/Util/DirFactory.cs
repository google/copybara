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

namespace Copybara.Util;

/// <summary>
/// A supplier of output directories under a given root. Port of
/// <c>com.google.copybara.util.DirFactory</c>.
///
/// <para>This factory allows Copybara to create all the files in a self-contained root, that can be
/// configured by users, and allows for temporary file cleanup, and directory reuse if necessary.</para>
/// </summary>
public class DirFactory
{
    public const string Tmp = "temp";
    private const string Cache = "cache";

    private readonly string _rootPath;

    public DirFactory(string rootPath) => _rootPath = Preconditions.CheckNotNull(rootPath);

    /// <summary>Get the cache directory for <paramref name="name"/>.</summary>
    public string GetCacheDir(string name)
    {
        string dir = Path.Combine(_rootPath, Cache, name);
        Directory.CreateDirectory(dir);
        return dir;
    }

    /// <summary>Creates a temp directory in the root path.</summary>
    public string NewTempDir(string name)
    {
        string outputPath = GetTmpRoot();
        // Create the output if it does not exist.
        Directory.CreateDirectory(outputPath);
        // Mirror Files.createTempDirectory(outputPath, name): a unique directory whose name starts
        // with the given prefix.
        string dir = Path.Combine(outputPath, name + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(dir);
        return dir;
    }

    public void CleanupTempDirs()
    {
        string outputPath = GetTmpRoot();
        if (Directory.Exists(outputPath))
        {
            FileUtil.DeleteRecursively(outputPath);
        }
    }

    public string GetTmpRoot() => Path.Combine(_rootPath, Tmp);
}
