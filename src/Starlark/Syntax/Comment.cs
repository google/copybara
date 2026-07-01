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

/// <summary>Syntax node for comments.</summary>
public sealed class Comment : Node
{
    private readonly int offset;
    private readonly string text;

    internal Comment(FileLocations locs, int offset, string text)
        : base(locs)
    {
        this.offset = offset;
        this.text = text;
    }

    /// <summary>Returns the text of the comment, including the leading '#' but not the trailing newline.</summary>
    public string GetText() => text;

    /// <summary>Returns true if the comment starts with <c>#:</c>, like a Sphinx autodoc-style doc comment.</summary>
    public bool HasDocCommentPrefix() => text.StartsWith("#:", StringComparison.Ordinal);

    /// <summary>
    /// If the comment starts with a <c>#: </c> or <c>#:</c> prefix, returns the text following it;
    /// otherwise, returns null.
    /// </summary>
    public string? GetDocCommentText()
    {
        if (HasDocCommentPrefix())
        {
            return text.StartsWith("#: ", StringComparison.Ordinal) ? text.Substring(3) : text.Substring(2);
        }
        return null;
    }

    public override int GetStartOffset() => offset;

    public override int GetEndOffset() => offset + text.Length;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);

    public override string ToString() => text;
}
