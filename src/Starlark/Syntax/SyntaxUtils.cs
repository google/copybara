// Copyright 2023 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

namespace Starlark.Syntax;

/// <summary>Utility methods for Starlark syntax.</summary>
public static class SyntaxUtils
{
    /// <summary>
    /// Returns the effective bound for a positive-stride slice operation from a user-supplied integer.
    /// </summary>
    public static int ToSliceBound(int index, int length)
    {
        if (index < 0)
        {
            index += length;
        }

        if (index < 0)
        {
            return 0;
        }
        else if (index > length)
        {
            return length;
        }
        else
        {
            return index;
        }
    }

    /// <summary>
    /// Returns the effective bound for a negative-stride slice operation from a user-supplied integer.
    /// </summary>
    public static int ToReverseSliceBound(int index, int length)
    {
        if (index < 0)
        {
            index += length;
        }

        if (index < -1)
        {
            return -1;
        }
        else if (index >= length)
        {
            return length - 1;
        }
        else
        {
            return index;
        }
    }
}
