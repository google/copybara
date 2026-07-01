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

namespace Starlark.Syntax;

/// <summary>
/// The apparent name and contents of a source file, for consumption by the parser. The file name
/// appears in the location information in the syntax tree, and in error messages, but the Starlark
/// interpreter will not attempt to open the file.
///
/// <para>The parser consumes a stream of chars (UTF-16 codes), and the syntax positions reported by
/// <c>Node.GetStartOffset</c> and <c>Location.Column</c> are effectively indices into a char
/// array.</para>
/// </summary>
public sealed class ParserInput
{
    private readonly string file;
    private readonly char[] content;

    private ParserInput(char[] content, string file)
    {
        this.content = content;
        this.file = file ?? throw new ArgumentNullException(nameof(file));
    }

    /// <summary>Returns the content of the input source. Callers must not modify the result.</summary>
    internal char[] GetContent() => content;

    /// <summary>Returns the apparent file name of the input source.</summary>
    public string GetFile() => file;

    /// <summary>
    /// Returns an input source that uses the name and content of the specified UTF-8-encoded text
    /// file.
    /// </summary>
    public static ParserInput ReadFile(string file)
    {
        byte[] utf8 = System.IO.File.ReadAllBytes(file);
        return FromUTF8(utf8, file);
    }

    /// <summary>Returns an unnamed input source that reads from a list of strings, joined by newlines.</summary>
    public static ParserInput FromLines(params string[] lines)
    {
        return FromString(string.Join("\n", lines), "");
    }

    /// <summary>Returns an input source that reads from a UTF-8-encoded byte array.</summary>
    public static ParserInput FromUTF8(byte[] bytes, string file)
    {
        string s = Encoding.UTF8.GetString(bytes);
        return FromCharArray(s.ToCharArray(), file);
    }

    /// <summary>
    /// Returns an input source that reads from a Latin1-encoded byte array.
    /// </summary>
    [Obsolete("This function exists to support legacy uses of Latin1 in Bazel. Do not use Latin1 in new applications.")]
    public static ParserInput FromLatin1(byte[] bytes, string file)
    {
        char[] chars = new char[bytes.Length];
        for (int i = 0; i < bytes.Length; i++)
        {
            chars[i] = (char)(0xff & bytes[i]);
        }
        return new ParserInput(chars, file);
    }

    /// <summary>Returns an input source that reads from the given string.</summary>
    public static ParserInput FromString(string content, string file)
    {
        return FromCharArray(content.ToCharArray(), file);
    }

    /// <summary>
    /// Returns an input source that reads from the given char array. The caller must not subsequently
    /// modify the array.
    /// </summary>
    public static ParserInput FromCharArray(char[] content, string file)
    {
        return new ParserInput(content, file);
    }
}
