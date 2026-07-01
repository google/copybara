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

/// <summary>Base class for all expression nodes in the AST.</summary>
public abstract class Expression : Node
{
    /// <summary>Kind of the expression.</summary>
    public enum ExpressionKind
    {
        BINARY_OPERATOR,
        CALL,
        CAST,
        COMPREHENSION,
        CONDITIONAL,
        DICT_EXPR,
        DOT,
        ELLIPSIS,
        FLOAT_LITERAL,
        IDENTIFIER,
        INDEX,
        INT_LITERAL,
        ISINSTANCE,
        LAMBDA,
        LIST_EXPR,
        SLICE,
        STRING_LITERAL,
        UNARY_OPERATOR,
        TYPE_APPLICATION,
    }

    private readonly ExpressionKind kind;

    internal Expression(FileLocations locs, ExpressionKind kind)
        : base(locs)
    {
        this.kind = kind;
    }

    /// <summary>Kind of the expression.</summary>
    public ExpressionKind Kind => kind;

    /// <summary>Parses an expression.</summary>
    public static Expression Parse(ParserInput input, FileOptions options)
    {
        return Parser.ParseExpression(input, options);
    }

    /// <summary>Parses an expression with default options.</summary>
    public static Expression Parse(ParserInput input)
    {
        return Parse(input, FileOptions.DEFAULT);
    }

    /// <summary>Parses a type expression.</summary>
    public static Expression ParseTypeExpression(ParserInput input, FileOptions options)
    {
        return Parser.ParseTypeExpression(input, options);
    }

    /// <summary>Parses a type expression with default options.</summary>
    public static Expression ParseTypeExpression(ParserInput input)
    {
        return ParseTypeExpression(input, FileOptions.DEFAULT);
    }
}
