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

/// <summary>A class for flow statements (break, continue, and pass).</summary>
public sealed class FlowStatement : Statement
{
    private readonly TokenKind flowKind; // BREAK | CONTINUE | PASS
    private readonly int offset;

    internal FlowStatement(FileLocations locs, TokenKind flowKind, int offset)
        : base(locs, StatementKind.FLOW)
    {
        this.flowKind = flowKind;
        this.offset = offset;
    }

    public TokenKind GetFlowKind() => flowKind;

    public override string ToString() => flowKind.ToDisplayString() + "\n";

    public override int GetStartOffset() => offset;

    public override int GetEndOffset() => offset + flowKind.ToDisplayString().Length;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
