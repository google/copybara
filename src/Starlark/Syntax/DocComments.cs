// Copyright 2024 The Bazel Authors. All rights reserved.
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

using System.Collections.Immutable;
using System.Linq;

namespace Starlark.Syntax;

/// <summary>A block of Sphinx autodoc-style doc comments.</summary>
public sealed class DocComments
{
    private readonly ImmutableArray<Comment> lines;

    public DocComments(IReadOnlyList<Comment> lines)
    {
        if (lines.Count == 0)
        {
            throw new ArgumentException("no lines");
        }
        if (!lines.All(c => c.HasDocCommentPrefix()))
        {
            throw new ArgumentException("all lines must have a doc comment prefix");
        }
        this.lines = lines.ToImmutableArray();
    }

    public IReadOnlyList<Comment> GetLines() => lines;

    public Location GetStartLocation() => lines[0].GetStartLocation();

    public Location GetEndLocation() => lines[lines.Length - 1].GetEndLocation();

    /// <summary>
    /// Returns the text content (trimmed of the leading <c>#: </c> or <c>#:</c> prefixes, and joined
    /// with newlines) of the doc comment block.
    /// </summary>
    public string GetText() => string.Join("\n", lines.Select(c => c.GetDocCommentText()));

    public override string ToString() => string.Join("\n", lines.Select(c => c.ToString()));
}
