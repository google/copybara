// Copyright 2014 The Bazel Authors. All rights reserved.
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
using Starlark.Annot;

namespace Starlark.Eval;

/// <summary>
/// The "string" module, defining the methods of the Starlark string type. Port of
/// <c>net.starlark.java.eval.StringModule</c>. Methods take the receiver string as their first
/// (<c>self</c>) parameter.
/// </summary>
[StarlarkBuiltin("string")]
public sealed class StringModule : IStarlarkValue
{
    /// <summary>The singleton receiver used for all string method dispatch.</summary>
    public static readonly StringModule INSTANCE = new();

    private StringModule() { }

    [StarlarkMethod("join", UseStarlarkThread = true)]
    public string Join(
        [Param(Name = "self")] string self,
        [Param(Name = "elements")] object elements,
        StarlarkThread thread)
    {
        var sb = new StringBuilder();
        int i = 0;
        bool first = true;
        foreach (object? item in Starlark.ToIterable(elements))
        {
            if (item is not string s)
            {
                throw Starlark.Errorf(
                    "expected string for sequence element {0}, got '{1}' of type {2}",
                    i, Starlark.Str(item, thread.GetSemantics()), Starlark.Type(item));
            }
            if (!first)
            {
                sb.Append(self);
            }
            sb.Append(s);
            first = false;
            i++;
        }
        return sb.ToString();
    }

    [StarlarkMethod("lower")]
    public string Lower([Param(Name = "self")] string self) => AsciiToLower(self);

    [StarlarkMethod("upper")]
    public string Upper([Param(Name = "self")] string self) => AsciiToUpper(self);

    [StarlarkMethod("lstrip")]
    public string Lstrip(
        [Param(Name = "self")] string self,
        [Param(Name = "chars", DefaultValue = "None", Noneable = true)] object? chars) =>
        StringLStrip(self, Matcher(chars));

    [StarlarkMethod("rstrip")]
    public string Rstrip(
        [Param(Name = "self")] string self,
        [Param(Name = "chars", DefaultValue = "None", Noneable = true)] object? chars) =>
        StringRStrip(self, Matcher(chars));

    [StarlarkMethod("strip")]
    public string Strip(
        [Param(Name = "self")] string self,
        [Param(Name = "chars", DefaultValue = "None", Noneable = true)] object? chars)
    {
        Func<char, bool> matcher = Matcher(chars);
        return StringLStrip(StringRStrip(self, matcher), matcher);
    }

    [StarlarkMethod("replace", UseStarlarkThread = true)]
    public string Replace(
        [Param(Name = "self")] string self,
        [Param(Name = "old")] string oldString,
        [Param(Name = "new")] string newString,
        [Param(Name = "count", DefaultValue = "-1")] StarlarkInt countI,
        StarlarkThread thread)
    {
        int count = countI.ToInt("count");
        if (count < 0)
        {
            count = int.MaxValue;
        }
        var sb = new StringBuilder();
        int start = 0;
        for (int i = 0; i < count; i++)
        {
            if (oldString.Length == 0)
            {
                sb.Append(newString);
                if (start < self.Length)
                {
                    sb.Append(self[start++]);
                }
                else
                {
                    break;
                }
            }
            else
            {
                int end = self.IndexOf(oldString, start, StringComparison.Ordinal);
                if (end < 0)
                {
                    break;
                }
                sb.Append(self, start, end - start).Append(newString);
                start = end + oldString.Length;
            }
        }
        sb.Append(self, start, self.Length - start);
        return sb.ToString();
    }

    [StarlarkMethod("split", UseStarlarkThread = true)]
    public StarlarkList Split(
        [Param(Name = "self")] string self,
        [Param(Name = "sep", Named = true)] string sep,
        [Param(Name = "maxsplit", DefaultValue = "unbound", Named = true)] object? maxSplitO,
        StarlarkThread thread)
    {
        if (sep.Length == 0)
        {
            throw Starlark.Errorf("Empty separator");
        }
        int maxSplit = int.MaxValue;
        if (!ReferenceEquals(maxSplitO, Starlark.UNBOUND))
        {
            maxSplit = Starlark.ToInt(maxSplitO, "maxsplit");
        }
        var res = new List<object?>();
        int start = 0;
        while (true)
        {
            int end = self.IndexOf(sep, start, StringComparison.Ordinal);
            if (end < 0 || maxSplit-- == 0)
            {
                res.Add(self.Substring(start));
                break;
            }
            res.Add(self.Substring(start, end - start));
            start = end + sep.Length;
        }
        return StarlarkList.CopyOf(thread.Mutability, res);
    }

    [StarlarkMethod("rsplit", UseStarlarkThread = true)]
    public StarlarkList Rsplit(
        [Param(Name = "self")] string self,
        [Param(Name = "sep", Named = true)] string sep,
        [Param(Name = "maxsplit", DefaultValue = "unbound", Named = true)] object? maxSplitO,
        StarlarkThread thread)
    {
        if (sep.Length == 0)
        {
            throw Starlark.Errorf("Empty separator");
        }
        int maxSplit = int.MaxValue;
        if (!ReferenceEquals(maxSplitO, Starlark.UNBOUND))
        {
            maxSplit = Starlark.ToInt(maxSplitO, "maxsplit");
        }
        var res = new List<object?>();
        int end = self.Length;
        while (true)
        {
            int start = end == 0 ? -1 : self.LastIndexOf(sep, end - 1, StringComparison.Ordinal);
            if (start < 0 || maxSplit-- == 0)
            {
                res.Add(self.Substring(0, end));
                break;
            }
            res.Add(self.Substring(start + sep.Length, end - (start + sep.Length)));
            end = start;
        }
        res.Reverse();
        return StarlarkList.CopyOf(thread.Mutability, res);
    }

    [StarlarkMethod("partition")]
    public Tuple Partition(
        [Param(Name = "self")] string self,
        [Param(Name = "sep")] string sep) => PartitionCommon(self, sep, true);

    [StarlarkMethod("rpartition")]
    public Tuple Rpartition(
        [Param(Name = "self")] string self,
        [Param(Name = "sep")] string sep) => PartitionCommon(self, sep, false);

    [StarlarkMethod("capitalize")]
    public string Capitalize([Param(Name = "self")] string self)
    {
        if (self.Length == 0)
        {
            return self;
        }
        return char.ToUpperInvariant(self[0]) + AsciiToLower(self.Substring(1));
    }

    [StarlarkMethod("title")]
    public string Title([Param(Name = "self")] string self)
    {
        char[] data = self.ToCharArray();
        bool previousWasLetter = false;
        for (int pos = 0; pos < data.Length; pos++)
        {
            char current = data[pos];
            bool currentIsLetter = char.IsLetter(current);
            if (currentIsLetter)
            {
                if (previousWasLetter && char.IsUpper(current))
                {
                    data[pos] = char.ToLowerInvariant(current);
                }
                else if (!previousWasLetter && char.IsLower(current))
                {
                    data[pos] = char.ToUpperInvariant(current);
                }
            }
            previousWasLetter = currentIsLetter;
        }
        return new string(data);
    }

    [StarlarkMethod("find")]
    public StarlarkInt Find(
        [Param(Name = "self")] string self,
        [Param(Name = "sub")] string sub,
        [Param(Name = "start", DefaultValue = "0", Noneable = true)] object? start,
        [Param(Name = "end", DefaultValue = "None", Noneable = true)] object? end) =>
        StarlarkInt.Of(StringFind(true, self, sub, start, end));

    [StarlarkMethod("rfind")]
    public StarlarkInt Rfind(
        [Param(Name = "self")] string self,
        [Param(Name = "sub")] string sub,
        [Param(Name = "start", DefaultValue = "0", Noneable = true)] object? start,
        [Param(Name = "end", DefaultValue = "None", Noneable = true)] object? end) =>
        StarlarkInt.Of(StringFind(false, self, sub, start, end));

    [StarlarkMethod("index")]
    public StarlarkInt Index(
        [Param(Name = "self")] string self,
        [Param(Name = "sub")] string sub,
        [Param(Name = "start", DefaultValue = "0", Noneable = true)] object? start,
        [Param(Name = "end", DefaultValue = "None", Noneable = true)] object? end)
    {
        int res = StringFind(true, self, sub, start, end);
        if (res < 0)
        {
            throw Starlark.Errorf("substring not found");
        }
        return StarlarkInt.Of(res);
    }

    [StarlarkMethod("rindex")]
    public StarlarkInt Rindex(
        [Param(Name = "self")] string self,
        [Param(Name = "sub")] string sub,
        [Param(Name = "start", DefaultValue = "0", Noneable = true)] object? start,
        [Param(Name = "end", DefaultValue = "None", Noneable = true)] object? end)
    {
        int res = StringFind(false, self, sub, start, end);
        if (res < 0)
        {
            throw Starlark.Errorf("substring not found");
        }
        return StarlarkInt.Of(res);
    }

    [StarlarkMethod("count")]
    public StarlarkInt Count(
        [Param(Name = "self")] string self,
        [Param(Name = "sub")] string sub,
        [Param(Name = "start", DefaultValue = "0", Noneable = true)] object? start,
        [Param(Name = "end", DefaultValue = "None", Noneable = true)] object? end)
    {
        (int lo, int hi) = SubstringIndices(self, start, end);
        if (sub.Length == 0)
        {
            return StarlarkInt.Of(hi - lo + 1);
        }
        string str = self.Substring(lo, hi - lo);
        int count = 0;
        int index = 0;
        while ((index = str.IndexOf(sub, index, StringComparison.Ordinal)) >= 0)
        {
            count++;
            index += sub.Length;
        }
        return StarlarkInt.Of(count);
    }

    [StarlarkMethod("startswith")]
    public bool Startswith(
        [Param(Name = "self")] string self,
        [Param(Name = "sub")] object sub,
        [Param(Name = "start", DefaultValue = "0", Noneable = true)] object? start,
        [Param(Name = "end", DefaultValue = "None", Noneable = true)] object? end)
    {
        (int lo, int hi) = SubstringIndices(self, start, end);
        if (sub is string prefix)
        {
            return SubstringStartsWith(self, lo, hi, prefix);
        }
        foreach (object? s in Starlark.ToIterable(sub))
        {
            if (SubstringStartsWith(self, lo, hi, (string)s!))
            {
                return true;
            }
        }
        return false;
    }

    [StarlarkMethod("endswith")]
    public bool Endswith(
        [Param(Name = "self")] string self,
        [Param(Name = "sub")] object sub,
        [Param(Name = "start", DefaultValue = "0", Noneable = true)] object? start,
        [Param(Name = "end", DefaultValue = "None", Noneable = true)] object? end)
    {
        (int lo, int hi) = SubstringIndices(self, start, end);
        if (sub is string suffix)
        {
            return SubstringEndsWith(self, lo, hi, suffix);
        }
        foreach (object? s in Starlark.ToIterable(sub))
        {
            if (SubstringEndsWith(self, lo, hi, (string)s!))
            {
                return true;
            }
        }
        return false;
    }

    [StarlarkMethod("format", UseStarlarkThread = true)]
    public string Format(
        [Param(Name = "self")] string self,
        Tuple args,
        Dict kwargs,
        StarlarkThread thread) =>
        FormatParser.Format(self, args, kwargs, thread.GetSemantics());

    [StarlarkMethod("removeprefix")]
    public string Removeprefix(
        [Param(Name = "self")] string self,
        [Param(Name = "prefix")] string prefix) =>
        self.StartsWith(prefix, StringComparison.Ordinal) ? self.Substring(prefix.Length) : self;

    [StarlarkMethod("removesuffix")]
    public string Removesuffix(
        [Param(Name = "self")] string self,
        [Param(Name = "suffix")] string suffix) =>
        self.EndsWith(suffix, StringComparison.Ordinal)
            ? self.Substring(0, self.Length - suffix.Length)
            : self;

    [StarlarkMethod("elems", UseStarlarkThread = true)]
    public StarlarkList Elems([Param(Name = "self")] string self, StarlarkThread thread)
    {
        var strings = new object?[self.Length];
        for (int i = 0; i < self.Length; i++)
        {
            strings[i] = self[i].ToString();
        }
        return StarlarkList.Wrap(thread.Mutability, strings);
    }

    [StarlarkMethod("isalpha")]
    public bool Isalpha([Param(Name = "self")] string self) => AllMatch(self, IsAlpha);

    [StarlarkMethod("isdigit")]
    public bool Isdigit([Param(Name = "self")] string self) => AllMatch(self, c => c is >= '0' and <= '9');

    [StarlarkMethod("isalnum")]
    public bool Isalnum([Param(Name = "self")] string self) =>
        AllMatch(self, c => IsAlpha(c) || c is >= '0' and <= '9');

    [StarlarkMethod("isspace")]
    public bool Isspace([Param(Name = "self")] string self) => AllMatch(self, char.IsWhiteSpace);

    [StarlarkMethod("islower")]
    public bool Islower([Param(Name = "self")] string self) => CasedMatch(self, isUpper: false);

    [StarlarkMethod("isupper")]
    public bool Isupper([Param(Name = "self")] string self) => CasedMatch(self, isUpper: true);

    // ---- helpers ----

    private static bool IsAlpha(char c) => c is >= 'a' and <= 'z' or >= 'A' and <= 'Z';

    private static bool AllMatch(string s, Func<char, bool> pred)
    {
        if (s.Length == 0)
        {
            return false;
        }
        foreach (char c in s)
        {
            if (!pred(c))
            {
                return false;
            }
        }
        return true;
    }

    private static bool CasedMatch(string s, bool isUpper)
    {
        if (s.Length == 0)
        {
            return false;
        }
        int cased = 0;
        foreach (char c in s)
        {
            bool lower = c is >= 'a' and <= 'z';
            bool upper = c is >= 'A' and <= 'Z';
            if (isUpper && lower)
            {
                return false;
            }
            if (!isUpper && upper)
            {
                return false;
            }
            if (lower || upper)
            {
                cased++;
            }
        }
        return cased > 0;
    }

    private static string AsciiToLower(string s)
    {
        var sb = new StringBuilder(s.Length);
        foreach (char c in s)
        {
            sb.Append(c is >= 'A' and <= 'Z' ? (char)(c + 32) : c);
        }
        return sb.ToString();
    }

    private static string AsciiToUpper(string s)
    {
        var sb = new StringBuilder(s.Length);
        foreach (char c in s)
        {
            sb.Append(c is >= 'a' and <= 'z' ? (char)(c - 32) : c);
        }
        return sb.ToString();
    }

    private static Func<char, bool> Matcher(object? charsOrNone)
    {
        if (charsOrNone is string chars)
        {
            return c => chars.IndexOf(c) >= 0;
        }
        return char.IsWhiteSpace;
    }

    private static string StringLStrip(string self, Func<char, bool> matcher)
    {
        for (int i = 0; i < self.Length; i++)
        {
            if (!matcher(self[i]))
            {
                return self.Substring(i);
            }
        }
        return "";
    }

    private static string StringRStrip(string self, Func<char, bool> matcher)
    {
        for (int i = self.Length - 1; i >= 0; i--)
        {
            if (!matcher(self[i]))
            {
                return self.Substring(0, i + 1);
            }
        }
        return "";
    }

    private static Tuple PartitionCommon(string input, string separator, bool first)
    {
        if (separator.Length == 0)
        {
            throw Starlark.Errorf("empty separator");
        }
        string a = "";
        string b = "";
        string c = "";
        int pos = first
            ? input.IndexOf(separator, StringComparison.Ordinal)
            : input.LastIndexOf(separator, StringComparison.Ordinal);
        if (pos < 0)
        {
            if (first)
            {
                a = input;
            }
            else
            {
                c = input;
            }
        }
        else
        {
            a = input.Substring(0, pos);
            b = separator;
            c = input.Substring(pos + separator.Length);
        }
        return Tuple.Triple(a, b, c);
    }

    private static (int lo, int hi) SubstringIndices(string str, object? start, object? end)
    {
        int n = str.Length;
        int istart = 0;
        if (!ReferenceEquals(start, Starlark.None))
        {
            istart = Starlark.ToSliceBound(Starlark.ToInt(start, "start"), n);
        }
        int iend = n;
        if (!ReferenceEquals(end, Starlark.None))
        {
            iend = Starlark.ToSliceBound(Starlark.ToInt(end, "end"), n);
        }
        if (iend < istart)
        {
            iend = istart;
        }
        return (istart, iend);
    }

    private static int StringFind(bool forward, string self, string sub, object? start, object? end)
    {
        (int lo, int hi) = SubstringIndices(self, start, end);
        if (forward)
        {
            if (lo > hi)
            {
                return -1;
            }
            int idx = self.IndexOf(sub, lo, StringComparison.Ordinal);
            if (idx < 0 || idx + sub.Length > hi)
            {
                // Ensure the match fits within [lo, hi).
                int i = lo;
                while (i <= hi - sub.Length)
                {
                    if (string.CompareOrdinal(self, i, sub, 0, sub.Length) == 0)
                    {
                        return i;
                    }
                    i++;
                }
                if (sub.Length == 0 && lo <= hi)
                {
                    return lo;
                }
                return -1;
            }
            return idx;
        }
        string window = self.Substring(lo, hi - lo);
        int subpos = window.LastIndexOf(sub, StringComparison.Ordinal);
        return subpos < 0 ? subpos : subpos + lo;
    }

    private static bool SubstringStartsWith(string str, int start, int end, string prefix) =>
        start + prefix.Length <= end && string.CompareOrdinal(str, start, prefix, 0, prefix.Length) == 0;

    private static bool SubstringEndsWith(string str, int start, int end, string suffix)
    {
        int nn = suffix.Length;
        return start + nn <= end && string.CompareOrdinal(str, end - nn, suffix, 0, nn) == 0;
    }
}
