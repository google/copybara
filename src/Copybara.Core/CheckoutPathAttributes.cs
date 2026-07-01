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
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara;

/// <summary>Represents file attributes exposed to Skylark.</summary>
[StarlarkBuiltin("PathAttributes", Doc = "Represents a path attributes like size.")]
public class CheckoutPathAttributes : IStarlarkValue
{
    private readonly string _path;
    private readonly long _size;
    private readonly bool _isSymlink;

    internal CheckoutPathAttributes(string path, long size, bool isSymlink)
    {
        _path = Preconditions.CheckNotNull(path);
        _size = size;
        _isSymlink = isSymlink;
    }

    [StarlarkMethod(
        "size",
        Doc = "The size of the file. Throws an error if file size > 2GB.",
        StructField = true)]
    public int Size()
    {
        if (_size is > int.MaxValue or < int.MinValue)
        {
            throw StarlarkRt.Errorf(
                "File {0} is too big to compute the size: {1} bytes", _path, _size);
        }
        return (int)_size;
    }

    [StarlarkMethod("symlink", Doc = "Returns true if it is a symlink", StructField = true)]
    public bool IsSymlink() => _isSymlink;
}
