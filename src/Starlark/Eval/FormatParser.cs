// Copyright 2015 The Bazel Authors. All rights reserved.
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

using System.Text;

namespace Starlark.Eval;

/// <summary>
/// Implements <c>str.format</c> replacement-field interpolation. Port of
/// <c>net.starlark.java.eval.FormatParser</c>.
/// </summary>
internal static class FormatParser
{
    internal static string Format(string pattern, Tuple args, Dict kwargs, StarlarkSemantics semantics)
    {
        var sb = new StringBuilder();
        int autoIndex = 0;
        int i = 0;
        int n = pattern.Length;
        while (i < n)
        {
            char c = pattern[i];
            if (c == '{')
            {
                if (i + 1 < n && pattern[i + 1] == '{')
                {
                    sb.Append('{');
                    i += 2;
                    continue;
                }
                int close = pattern.IndexOf('}', i + 1);
                if (close < 0)
                {
                    throw Starlark.Errorf("Found '{{' without matching '}}'");
                }
                string key = pattern.Substring(i + 1, close - i - 1);
                object? value = ResolveKey(key, args, kwargs, ref autoIndex);
                sb.Append(Starlark.Str(value, semantics));
                i = close + 1;
            }
            else if (c == '}')
            {
                if (i + 1 < n && pattern[i + 1] == '}')
                {
                    sb.Append('}');
                    i += 2;
                    continue;
                }
                throw Starlark.Errorf("Found '}}' without matching '{{'");
            }
            else
            {
                sb.Append(c);
                i++;
            }
        }
        return sb.ToString();
    }

    private static object? ResolveKey(string key, Tuple args, Dict kwargs, ref int autoIndex)
    {
        // Field name may contain a trailing conversion/format spec after ':' or '!'; unsupported
        // here beyond field selection, so take the leading identifier/index only.
        int cut = key.IndexOfAny(new[] { ':', '!' });
        string field = cut < 0 ? key : key.Substring(0, cut);

        if (field.Length == 0)
        {
            if (autoIndex >= args.Count)
            {
                throw Starlark.Errorf(
                    "Not enough arguments for format string (needed at least {0})", autoIndex + 1);
            }
            return args[autoIndex++];
        }
        if (int.TryParse(field, out int idx))
        {
            if (idx < 0 || idx >= args.Count)
            {
                throw Starlark.Errorf("Index '{0}' out of range", idx);
            }
            return args[idx];
        }
        object? v = kwargs.Get(field);
        if (v == null && !kwargs.ContainsKeyJava(field))
        {
            throw Starlark.Errorf("Missing argument '{0}'", field);
        }
        return v;
    }
}
