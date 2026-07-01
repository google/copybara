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

/// <summary>A syntax node for return statements.</summary>
public sealed class ReturnStatement : Statement
{
    private readonly int returnOffset;
    private readonly Expression? result;

    internal ReturnStatement(FileLocations locs, int returnOffset, Expression? result)
        : base(locs, StatementKind.RETURN)
    {
        this.returnOffset = returnOffset;
        this.result = result;
    }

    /// <summary>
    /// Returns a new return statement that returns expr. It is provided only for use by the evaluator.
    /// </summary>
    internal static ReturnStatement Make(Expression expr) =>
        new(expr.Locs, expr.GetStartOffset(), expr);

    public Expression? GetResult() => result;

    public override int GetStartOffset() => returnOffset;

    public override int GetEndOffset() =>
        result != null ? result.GetEndOffset() : returnOffset + "return".Length;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
