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

namespace Copybara;

/// <summary>
/// Small helper mirroring the subset of <c>java.nio.file.Path</c> semantics used by
/// <see cref="CheckoutPath"/> / <see cref="CheckoutFileSystem"/>. Paths are treated as
/// forward-slash-separated strings.
/// </summary>
internal static class PathOps
{
    public static bool IsAbsolute(string path) =>
        path.StartsWith('/') || System.IO.Path.IsPathRooted(path);

    /// <summary>Resolves <paramref name="other"/> against <paramref name="basePath"/>.</summary>
    public static string Resolve(string basePath, string other)
    {
        if (other.Length == 0)
        {
            return basePath;
        }
        if (IsAbsolute(other))
        {
            return other;
        }
        if (basePath.Length == 0)
        {
            return other;
        }
        return TrimTrailingSlash(basePath) + "/" + other;
    }

    /// <summary>Resolves <paramref name="other"/> against the parent of <paramref name="path"/>.</summary>
    public static string ResolveSibling(string path, string other)
    {
        string? parent = GetParent(path);
        return parent == null ? other : Resolve(parent, other);
    }

    /// <summary>Normalizes a path, resolving <c>.</c> and <c>..</c> segments.</summary>
    public static string Normalize(string path)
    {
        bool absolute = path.StartsWith('/');
        var parts = path.Split('/', StringSplitOptions.RemoveEmptyEntries);
        var stack = new List<string>();
        foreach (var part in parts)
        {
            if (part == ".")
            {
                continue;
            }
            if (part == "..")
            {
                if (stack.Count > 0 && stack[^1] != "..")
                {
                    stack.RemoveAt(stack.Count - 1);
                }
                else if (!absolute)
                {
                    stack.Add("..");
                }
                continue;
            }
            stack.Add(part);
        }
        string joined = string.Join('/', stack);
        return absolute ? "/" + joined : joined;
    }

    /// <summary>Returns true if <paramref name="path"/> starts with <paramref name="prefix"/>.</summary>
    public static bool StartsWith(string path, string prefix)
    {
        string p = TrimTrailingSlash(Normalize(path));
        string pre = TrimTrailingSlash(Normalize(prefix));
        if (pre.Length == 0)
        {
            return true;
        }
        return p == pre || p.StartsWith(pre + "/", StringComparison.Ordinal);
    }

    public static string GetFileName(string path)
    {
        string trimmed = TrimTrailingSlash(path);
        int idx = trimmed.LastIndexOf('/');
        return idx == -1 ? trimmed : trimmed.Substring(idx + 1);
    }

    /// <summary>Returns the parent path, or null if there is none.</summary>
    public static string? GetParent(string path)
    {
        string trimmed = TrimTrailingSlash(path);
        int idx = trimmed.LastIndexOf('/');
        if (idx == -1)
        {
            return null;
        }
        if (idx == 0)
        {
            return "/";
        }
        return trimmed.Substring(0, idx);
    }

    /// <summary>Constructs a relative path from <paramref name="basePath"/> to <paramref name="other"/>.</summary>
    public static string Relativize(string basePath, string other)
    {
        var baseParts = Normalize(basePath).Split('/', StringSplitOptions.RemoveEmptyEntries);
        var otherParts = Normalize(other).Split('/', StringSplitOptions.RemoveEmptyEntries);
        int common = 0;
        while (common < baseParts.Length && common < otherParts.Length
               && baseParts[common] == otherParts[common])
        {
            common++;
        }
        var result = new List<string>();
        for (int i = common; i < baseParts.Length; i++)
        {
            result.Add("..");
        }
        for (int i = common; i < otherParts.Length; i++)
        {
            result.Add(otherParts[i]);
        }
        return string.Join('/', result);
    }

    private static string TrimTrailingSlash(string path) =>
        path.Length > 1 && path.EndsWith('/') ? path.TrimEnd('/') : path;
}
