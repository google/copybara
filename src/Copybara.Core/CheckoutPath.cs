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
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara;

/// <summary>
/// Represents a file that is exposed to Skylark.
///
/// <para>Files are always relative to the checkout dir and normalized. Paths are represented as
/// forward-slash-separated strings, mirroring <c>java.nio.file.Path</c> semantics.</para>
/// </summary>
[StarlarkBuiltin("Path", Doc = "Represents a path in the checkout directory")]
public class CheckoutPath : IComparable<CheckoutPath>, IStarlarkPrintableValue
{
    private readonly string _path;
    private readonly string _checkoutDir;

    internal CheckoutPath(string path, string checkoutDir)
    {
        _path = Preconditions.CheckNotNull(path);
        _checkoutDir = Preconditions.CheckNotNull(checkoutDir);
    }

    private CheckoutPath Create(string path) => CreateWithCheckoutDir(path, _checkoutDir);

    public static CheckoutPath CreateWithCheckoutDir(string relative, string checkoutDir)
    {
        if (PathOps.IsAbsolute(relative))
        {
            throw StarlarkRt.Errorf("Absolute paths are not allowed: {0}", relative);
        }
        string targetPath = PathOps.Normalize(PathOps.Resolve(checkoutDir, relative));
        if (!PathOps.StartsWith(targetPath, checkoutDir))
        {
            throw StarlarkRt.Errorf("Escaping the checkout dir is not allowed: {0}", relative);
        }

        return new CheckoutPath(PathOps.Normalize(relative), checkoutDir);
    }

    [StarlarkMethod(
        "path",
        Doc = "Full path relative to the checkout directory",
        StructField = true)]
    public string PathAsString() => _path;

    /// <summary>
    /// The full path pointing to the real location of the checkout file. Use only for internal
    /// implementations and do not make this available to Starlark.
    /// </summary>
    public string FullPath() => PathOps.Resolve(_checkoutDir, _path);

    [StarlarkMethod(
        "name",
        Doc = "Filename of the path. For foo/bar/baz.txt it would be baz.txt",
        StructField = true)]
    public string Name() => PathOps.GetFileName(_path);

    [StarlarkMethod("parent", Doc = "Get the parent path", StructField = true)]
    public object Parent()
    {
        string? parent = PathOps.GetParent(_path);
        if (parent == null)
        {
            // nio equivalent of new_path("foo").parent returns null, but we want to be able to do
            // foo.parent.resolve("bar").
            return _path.Length == 0 ? StarlarkRt.None : Create("");
        }
        return Create(parent);
    }

    [StarlarkMethod(
        "relativize",
        Doc =
            "Constructs a relative path between this path and a given path. For example:<br>"
            + "    path('a/b').relativize('a/b/c/d')<br>"
            + "returns 'c/d'")]
    public CheckoutPath Relativize(
        [Param(Name = "other", Doc = "The path to relativize against this path")] CheckoutPath other) =>
        Create(PathOps.Relativize(_path, other._path));

    [StarlarkMethod(
        "resolve",
        Doc = "Resolve the given path against this path.")]
    public CheckoutPath Resolve(
        [Param(
            Name = "child",
            AllowedTypes = new[] { typeof(string), typeof(CheckoutPath) },
            Doc = "Resolve the given path against this path. The parameter"
                + " can be a string or a Path.")]
        object child)
    {
        if (child is string s)
        {
            return Create(PathOps.Resolve(_path, s));
        }
        if (child is CheckoutPath cp)
        {
            return Create(PathOps.Resolve(_path, cp._path));
        }
        throw StarlarkRt.Errorf("Cannot resolve children for type {0}: {1}", child.GetType().Name, child);
    }

    [StarlarkMethod(
        "resolve_sibling",
        Doc = "Resolve the given path against this path.")]
    public CheckoutPath ResolveSibling(
        [Param(
            Name = "other",
            AllowedTypes = new[] { typeof(string), typeof(CheckoutPath) },
            Doc = "Resolve the given path against this path. The parameter can be a string or a Path.")]
        object other)
    {
        if (other is string s)
        {
            return Create(PathOps.ResolveSibling(_path, s));
        }
        if (other is CheckoutPath cp)
        {
            return Create(PathOps.ResolveSibling(_path, cp._path));
        }
        throw StarlarkRt.Errorf("Cannot resolve sibling for type {0}: {1}", other.GetType().Name, other);
    }

    [StarlarkMethod("attr", Doc = "Get the file attributes, for example size.", StructField = true)]
    public CheckoutPathAttributes Attr()
    {
        try
        {
            string full = PathOps.Resolve(_checkoutDir, _path);
            bool isSymlink =
                (System.IO.File.GetAttributes(full) & System.IO.FileAttributes.ReparsePoint) != 0;
            long size = isSymlink ? 0 : new System.IO.FileInfo(full).Length;
            return new CheckoutPathAttributes(_path, size, isSymlink);
        }
        catch (System.IO.IOException e)
        {
            throw StarlarkRt.Errorf("Error getting attributes for {0}:{1}", _path, e);
        }
    }

    [StarlarkMethod("read_symlink", Doc = "Read the symlink")]
    public CheckoutPath ReadSymbolicLink()
    {
        try
        {
            string symlinkPath = PathOps.Resolve(_checkoutDir, _path);
            var info = new System.IO.FileInfo(symlinkPath);
            if ((System.IO.File.GetAttributes(symlinkPath) & System.IO.FileAttributes.ReparsePoint) == 0)
            {
                throw StarlarkRt.Errorf("{0} is not a symlink", _path);
            }

            var resolvedSymlink =
                FileUtil.ResolveSymlink(Glob.AllFiles.RelativeTo(_checkoutDir), symlinkPath);
            if (resolvedSymlink.TargetLocationValue != FileUtil.ResolvedSymlink.TargetLocation.Inside)
            {
                throw StarlarkRt.Errorf(
                    "Symlink {0} does not point to a file inside the checkout dir: {1}",
                    symlinkPath, resolvedSymlink.RegularFile);
            }

            return Create(PathOps.Relativize(_checkoutDir, resolvedSymlink.RegularFile));
        }
        catch (System.IO.IOException e)
        {
            throw StarlarkRt.Errorf("Cannot resolve symlink {0}: {1}", _path, e);
        }
    }

    [StarlarkMethod("remove", Doc = "Delete self")]
    public void Remove()
    {
        try
        {
            System.IO.File.Delete(PathOps.Resolve(_checkoutDir, _path));
        }
        catch (System.IO.FileNotFoundException e)
        {
            throw StarlarkRt.Errorf("Could not find file {0}, received error {1}", _path, e.ToString());
        }
        catch (System.IO.IOException e)
        {
            throw new ValidationException("Could not delete file for unknown reason", e);
        }
    }

    [StarlarkMethod(
        "rmdir",
        Doc =
            "Delete all files in a directory. If recursive is true, delete descendants of all files"
            + " in directory")]
    public void RmDir(
        [Param(
            Name = "recursive",
            Named = true,
            Doc = "When true, delete descendants of self and of siblings",
            DefaultValue = "False")]
        bool recursive)
    {
        try
        {
            string full = PathOps.Resolve(_checkoutDir, _path);
            if (!System.IO.Directory.Exists(full) && !System.IO.File.Exists(full))
            {
                return;
            }
            if (recursive)
            {
                FileUtil.DeleteRecursively(full);
            }
            else
            {
                System.IO.Directory.Delete(full);
            }
        }
        catch (System.IO.IOException e)
        {
            throw new ValidationException("Could not delete file for unknown reason", e);
        }
    }

    [StarlarkMethod("exists", Doc = "Check whether a file, directory or symlink exists at this path")]
    public bool FileExists()
    {
        string full = PathOps.Resolve(_checkoutDir, _path);
        return System.IO.File.Exists(full) || System.IO.Directory.Exists(full);
    }

    public string GetPath() => _path;

    public string GetCheckoutDir() => _checkoutDir;

    public override string ToString() => _path;

    public int CompareTo(CheckoutPath? o) =>
        string.CompareOrdinal(_path, o?._path);

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(_path);

    public override int GetHashCode() => _path.GetHashCode();

    public override bool Equals(object? o)
    {
        if (ReferenceEquals(this, o))
        {
            return true;
        }
        if (o is not CheckoutPath other)
        {
            return false;
        }
        return string.Equals(_path, other._path);
    }
}
