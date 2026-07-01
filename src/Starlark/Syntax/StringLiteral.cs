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

namespace Starlark.Syntax;

/// <summary>Syntax node for a string literal.</summary>
public sealed class StringLiteral : Expression
{
    private readonly int startOffset;
    private readonly string value;
    private readonly int endOffset;

    internal StringLiteral(FileLocations locs, int startOffset, string value, int endOffset)
        : base(locs, ExpressionKind.STRING_LITERAL)
    {
        this.startOffset = startOffset;
        this.value = value;
        this.endOffset = endOffset;
    }

    /// <summary>Returns the value denoted by the string literal.</summary>
    public string GetValue() => value;

    public Location GetLocation() => Locs.GetLocation(startOffset);

    public override int GetStartOffset() => startOffset;

    public override int GetEndOffset() => endOffset;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);

    /// <summary>Returns an opaque serializable object that may be passed to <see cref="FromSerialization"/>.</summary>
    public object GetFileLocations() => Locs;

    /// <summary>
    /// Returns the value denoted by the Starlark string literal within <paramref name="s"/>.
    /// </summary>
    /// <exception cref="ArgumentException">if s does not contain a valid string literal.</exception>
    public static string Unquote(string s)
    {
        var errors = new List<SyntaxError>();
        var lexer = new Lexer(ParserInput.FromLines(s), errors, FileOptions.DEFAULT);
        lexer.NextToken();
        if (errors.Count != 0)
        {
            throw new ArgumentException(errors[0].Message);
        }
        if (lexer.Start != 0 || lexer.End != s.Length || lexer.Kind != TokenKind.STRING)
        {
            throw new ArgumentException("invalid syntax");
        }
        return (string)lexer.Value!;
    }

    /// <summary>Constructs a StringLiteral from its serialized components.</summary>
    public static StringLiteral FromSerialization(
        object fileLocations, int startOffset, string value, int endOffset)
    {
        return new StringLiteral((FileLocations)fileLocations, startOffset, value, endOffset);
    }
}
