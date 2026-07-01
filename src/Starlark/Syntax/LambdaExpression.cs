// Copyright 2019 The Bazel Authors. All rights reserved.
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

namespace Starlark.Syntax;

/// <summary>A LambdaExpression (<c>lambda params: body</c>) denotes an anonymous function.</summary>
public sealed class LambdaExpression : Expression
{
    private readonly int lambdaOffset; // offset of 'lambda' token
    private readonly ImmutableArray<Parameter> parameters;
    private readonly Expression body;

    // set by resolver
    private Resolver.Function? resolved;

    internal LambdaExpression(
        FileLocations locs, int lambdaOffset, ImmutableArray<Parameter> parameters, Expression body)
        : base(locs, ExpressionKind.LAMBDA)
    {
        this.lambdaOffset = lambdaOffset;
        this.parameters = parameters;
        this.body = body;
    }

    public IReadOnlyList<Parameter> GetParameters() => parameters;

    public Expression GetBody() => body;

    /// <summary>Returns information about the resolved function. Set by the resolver.</summary>
    public Resolver.Function? GetResolvedFunction() => resolved;

    internal void SetResolvedFunction(Resolver.Function resolved) => this.resolved = resolved;

    public override int GetStartOffset() => lambdaOffset;

    public override int GetEndOffset() => body.GetEndOffset();

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
