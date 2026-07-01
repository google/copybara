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

/// <summary>Syntax node for the singleton ellipsis expression.</summary>
public sealed class Ellipsis : Expression
{
    private readonly int startOffset;

    internal Ellipsis(FileLocations locs, int startOffset)
        : base(locs, ExpressionKind.ELLIPSIS)
    {
        this.startOffset = startOffset;
    }

    public override int GetStartOffset() => startOffset;

    public override int GetEndOffset() => startOffset + 3;

    public override string ToString() => "...";

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
