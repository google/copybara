/*
 * Copyright (C) 2023 Google Inc.
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
using Copybara.Common;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara;

/// <summary>Common Starlark methods that allow users to manipulate paths of the workdir/checkoutPath.</summary>
public class CheckoutFileSystem : IStarlarkValue
{
    private readonly string _checkoutDir;

    public CheckoutFileSystem(string checkoutDir)
    {
        _checkoutDir = Preconditions.CheckNotNull(checkoutDir);
    }

    [StarlarkMethod("new_path", Doc = "Create a new path")]
    public CheckoutPath NewPath(
        [Param(
            Name = "path",
            Doc = "The string representing the path, relative to the checkout root directory")]
        string path) =>
        CheckoutPath.CreateWithCheckoutDir(path, _checkoutDir);

    [StarlarkMethod("create_symlink", Doc = "Create a symlink")]
    public void CreateSymlink(
        [Param(Name = "link", Doc = "The link path")] CheckoutPath link,
        [Param(Name = "target", Doc = "The target path")] CheckoutPath target)
    {
        try
        {
            string linkFullPath = AsCheckoutPath(link);
            // Verify target is inside checkout dir
            _ = AsCheckoutPath(target);

            if (System.IO.File.Exists(linkFullPath) || System.IO.Directory.Exists(linkFullPath))
            {
                string kind = System.IO.Directory.Exists(linkFullPath)
                    ? " and is a directory"
                    : (System.IO.File.GetAttributes(linkFullPath) & System.IO.FileAttributes.ReparsePoint) != 0
                        ? " and is a symlink"
                        : System.IO.File.Exists(linkFullPath)
                            ? " and is a regular file"
                            : " and we don't know what kind of file is";
                throw StarlarkRt.Errorf("'{0}' already exist{1}", link.GetPath(), kind);
            }

            string? linkParent = PathOps.GetParent(link.GetPath());
            string relativized = linkParent == null
                ? target.GetPath()
                : PathOps.Relativize(linkParent, target.GetPath());
            string? fullParent = PathOps.GetParent(linkFullPath);
            if (fullParent != null)
            {
                System.IO.Directory.CreateDirectory(fullParent);
            }

            System.IO.File.CreateSymbolicLink(linkFullPath, relativized);
        }
        catch (System.IO.IOException e)
        {
            throw StarlarkRt.Errorf("Cannot create symlink: {0}", e.Message);
        }
    }

    [StarlarkMethod("write_path", Doc = "Write an arbitrary string to a path (UTF-8 will be used)")]
    public void WritePath(
        [Param(Name = "path", Doc = "The Path to write to")] CheckoutPath path,
        [Param(Name = "content", Doc = "The content of the file")] string content)
    {
        string fullPath = AsCheckoutPath(path);
        string? parent = PathOps.GetParent(fullPath);
        if (parent != null)
        {
            System.IO.Directory.CreateDirectory(parent);
        }
        System.IO.File.WriteAllBytes(fullPath, Encoding.UTF8.GetBytes(content));
    }

    [StarlarkMethod("read_path", Doc = "Read the content of path as UTF-8")]
    public string ReadPath(
        [Param(Name = "path", Doc = "The Path to read from")] CheckoutPath path) =>
        Encoding.UTF8.GetString(System.IO.File.ReadAllBytes(AsCheckoutPath(path)));

    [StarlarkMethod("set_executable", Doc = "Set the executable permission of a file")]
    public void SetExecutable(
        [Param(Name = "path", Doc = "The Path to set the executable permission of")] CheckoutPath path,
        [Param(Name = "value", Doc = "Whether or not the file should be executable")] bool value)
    {
        string full = AsCheckoutPath(path);
        if (!OperatingSystem.IsWindows())
        {
            var mode = System.IO.File.GetUnixFileMode(full);
            var exec = UnixFileMode.UserExecute | UnixFileMode.GroupExecute | UnixFileMode.OtherExecute;
            mode = value ? mode | exec : mode & ~exec;
            System.IO.File.SetUnixFileMode(full, mode);
        }
    }

    /// <summary>
    /// The path containing the repository state to transform. Transformation should be done in-place.
    /// </summary>
    public string GetCheckoutDir() => _checkoutDir;

    [StarlarkMethod("list", Doc = "List files in the checkout/work directory that matches a glob")]
    public StarlarkList List(
        [Param(Name = "paths", Doc = "A glob representing the paths to list")] Glob glob)
    {
        var pathMatcher = glob.RelativeTo(_checkoutDir);
        var result = new List<CheckoutPath>();
        foreach (var full in System.IO.Directory.EnumerateFiles(
                     _checkoutDir, "*", System.IO.SearchOption.AllDirectories))
        {
            string normalized = full.Replace('\\', '/');
            if (pathMatcher.Matches(normalized))
            {
                result.Add(new CheckoutPath(PathOps.Relativize(_checkoutDir, normalized), _checkoutDir));
            }
        }
        return StarlarkList.ImmutableCopyOf(result.Cast<object?>());
    }

    private string AsCheckoutPath(CheckoutPath path)
    {
        string resolved = PathOps.Resolve(_checkoutDir, path.GetPath());
        string normalized = PathOps.Normalize(resolved);
        if (!PathOps.StartsWith(normalized, _checkoutDir))
        {
            throw StarlarkRt.Errorf(
                "{0} is not inside the checkout directory or links to a file outside the path."
                + " Normalized path was {1}, checkout dir was {2}",
                path, normalized, _checkoutDir);
        }
        return normalized;
    }
}
