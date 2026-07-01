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

using System.Collections.Immutable;

namespace Starlark.Syntax;

/// <summary>Parser is a recursive-descent parser for Starlark.</summary>
internal sealed class Parser
{
    /// <summary>Combines the parser result into a single value object.</summary>
    internal sealed class ParseResult
    {
        internal readonly FileLocations Locs;
        internal readonly ImmutableArray<Statement> Statements;
        internal readonly ImmutableArray<Comment> Comments;
        internal readonly List<SyntaxError> Errors;

        internal ParseResult(
            FileLocations locs,
            ImmutableArray<Statement> statements,
            ImmutableArray<Comment> comments,
            List<SyntaxError> errors)
        {
            this.Locs = locs;
            this.Statements = statements;
            this.Comments = comments;
            this.Errors = errors;
        }
    }

    private static readonly HashSet<TokenKind> STATEMENT_TERMINATOR_SET = new()
    {
        TokenKind.EOF, TokenKind.NEWLINE, TokenKind.DOC_COMMENT_TRAILING, TokenKind.SEMI,
    };

    private static readonly HashSet<TokenKind> LIST_TERMINATOR_SET = new()
    {
        TokenKind.EOF, TokenKind.RBRACKET, TokenKind.SEMI,
    };

    private static readonly HashSet<TokenKind> DICT_TERMINATOR_SET = new()
    {
        TokenKind.EOF, TokenKind.RBRACE, TokenKind.SEMI,
    };

    private static readonly HashSet<TokenKind> EXPR_LIST_TERMINATOR_SET = new()
    {
        TokenKind.EOF,
        TokenKind.NEWLINE,
        TokenKind.DOC_COMMENT_TRAILING,
        TokenKind.EQUALS,
        TokenKind.RBRACE,
        TokenKind.RBRACKET,
        TokenKind.RPAREN,
        TokenKind.SEMI,
    };

    private static readonly HashSet<TokenKind> EXPR_TERMINATOR_SET = new()
    {
        TokenKind.COLON,
        TokenKind.COMMA,
        TokenKind.EOF,
        TokenKind.FOR,
        TokenKind.MINUS,
        TokenKind.PERCENT,
        TokenKind.PLUS,
        TokenKind.RBRACKET,
        TokenKind.RPAREN,
        TokenKind.SLASH,
    };

    private const string TYPE_SOFT_KEYWORD = "type";

    private const bool DEBUGGING = false;

    private readonly FileOptions options;

    private readonly Lexer lexer;
    private readonly FileLocations locs;
    private readonly List<SyntaxError> errors;

    private DocComments? mostRecentDocCommentBlock = null;

    private bool insideTypeExpr = false;

    private static readonly Dictionary<TokenKind, TokenKind> augmentedAssignments = new()
    {
        { TokenKind.PLUS_EQUALS, TokenKind.PLUS },
        { TokenKind.MINUS_EQUALS, TokenKind.MINUS },
        { TokenKind.STAR_EQUALS, TokenKind.STAR },
        { TokenKind.SLASH_EQUALS, TokenKind.SLASH },
        { TokenKind.SLASH_SLASH_EQUALS, TokenKind.SLASH_SLASH },
        { TokenKind.PERCENT_EQUALS, TokenKind.PERCENT },
        { TokenKind.AMPERSAND_EQUALS, TokenKind.AMPERSAND },
        { TokenKind.CARET_EQUALS, TokenKind.CARET },
        { TokenKind.PIPE_EQUALS, TokenKind.PIPE },
        { TokenKind.GREATER_GREATER_EQUALS, TokenKind.GREATER_GREATER },
        { TokenKind.LESS_LESS_EQUALS, TokenKind.LESS_LESS },
    };

    private static readonly List<HashSet<TokenKind>> operatorPrecedence = new()
    {
        new HashSet<TokenKind> { TokenKind.OR },
        new HashSet<TokenKind> { TokenKind.AND },
        new HashSet<TokenKind> { TokenKind.NOT },
        new HashSet<TokenKind>
        {
            TokenKind.EQUALS_EQUALS,
            TokenKind.NOT_EQUALS,
            TokenKind.LESS,
            TokenKind.LESS_EQUALS,
            TokenKind.GREATER,
            TokenKind.GREATER_EQUALS,
            TokenKind.IN,
            TokenKind.NOT_IN,
        },
        new HashSet<TokenKind> { TokenKind.PIPE },
        new HashSet<TokenKind> { TokenKind.CARET },
        new HashSet<TokenKind> { TokenKind.AMPERSAND },
        new HashSet<TokenKind> { TokenKind.GREATER_GREATER, TokenKind.LESS_LESS },
        new HashSet<TokenKind> { TokenKind.MINUS, TokenKind.PLUS },
        new HashSet<TokenKind> { TokenKind.SLASH, TokenKind.SLASH_SLASH, TokenKind.STAR, TokenKind.PERCENT },
    };

    private int errorsCount;
    private bool recoveryMode;

    private readonly Dictionary<string, string> stringInterner = new();

    private Parser(Lexer lexer, List<SyntaxError> errors, FileOptions options)
    {
        this.lexer = lexer;
        this.locs = lexer.Locs;
        this.errors = errors;
        this.options = options;
        NextToken();
    }

    // token.kind is a prettier alias for lexer.Kind, etc.
    private TokenKind TokenKindCur => lexer.Kind;
    private int TokenStart => lexer.Start;
    private int TokenEnd => lexer.End;
    private object? TokenValue => lexer.Value;

    private string Intern(string s)
    {
        if (stringInterner.TryGetValue(s, out string? prev))
        {
            return prev;
        }
        stringInterner[s] = s;
        return s;
    }

    private static string TokenString(TokenKind kind, object? value)
    {
        return kind == TokenKind.STRING
            ? "\"" + value + "\""
            : value == null ? kind.ToDisplayString() : value.ToString()!;
    }

    // Main entry point for parsing a file.
    internal static ParseResult ParseFile(ParserInput input, FileOptions options)
    {
        var errors = new List<SyntaxError>();
        var lexer = new Lexer(input, errors, options);
        var parser = new Parser(lexer, errors, options);

        StarlarkFile.IParseProfiler? profiler = Parser.Profiler;
        long profileStartNanos = profiler != null ? profiler.Start() : -1;
        try
        {
            ImmutableArray<Statement> statements = parser.ParseFileInput();
            return new ParseResult(lexer.Locs, statements, lexer.GetComments().ToImmutableArray(), errors);
        }
        finally
        {
            if (profileStartNanos != -1)
            {
                profiler!.End(profileStartNanos, input.GetFile());
            }
        }
    }

    internal static StarlarkFile.IParseProfiler? Profiler;

    private void ParseStatement(ImmutableArray<Statement>.Builder list)
    {
        if (TokenKindCur == TokenKind.DEF)
        {
            list.Add(ParseDefStatement());
        }
        else if (TokenKindCur == TokenKind.IF)
        {
            list.Add(ParseIfStatement());
        }
        else if (TokenKindCur == TokenKind.FOR)
        {
            list.Add(ParseForStatement());
        }
        else
        {
            ParseSimpleStatement(list);
        }
    }

    private void MaybeParseDocCommentBlock()
    {
        while (TokenKindCur == TokenKind.DOC_COMMENT_BLOCK)
        {
            mostRecentDocCommentBlock = (DocComments)TokenValue!;
            NextToken();
        }
    }

    private DocComments? GetDocCommentBlockOnPreviousLine(int line)
    {
        if (mostRecentDocCommentBlock != null
            && mostRecentDocCommentBlock.GetEndLocation().Line + 1 == line)
        {
            return mostRecentDocCommentBlock;
        }
        return null;
    }

    /// <summary>Parses an expression, possibly preceded or followed by comments or whitespace.</summary>
    internal static Expression ParseExpression(ParserInput input, FileOptions options)
    {
        return ParseValueOrTypeExpr(input, options, false);
    }

    /// <summary>Parses a type expression, possibly preceded or followed by comments or whitespace.</summary>
    internal static Expression ParseTypeExpression(ParserInput input, FileOptions options)
    {
        return ParseValueOrTypeExpr(input, options, true);
    }

    private static Expression ParseValueOrTypeExpr(ParserInput input, FileOptions options, bool isTypeExpr)
    {
        var errors = new List<SyntaxError>();
        var lexer = new Lexer(input, errors, options);
        var parser = new Parser(lexer, errors, options);
        Expression? result = null;
        while (parser.TokenKindCur == TokenKind.DOC_COMMENT_BLOCK)
        {
            parser.NextToken();
        }
        result = isTypeExpr ? parser.ParseTypeExprWithFallback() : parser.ParseExpr();
        while (parser.TokenKindCur == TokenKind.NEWLINE
            || parser.TokenKindCur == TokenKind.DOC_COMMENT_BLOCK
            || parser.TokenKindCur == TokenKind.DOC_COMMENT_TRAILING)
        {
            parser.NextToken();
        }
        parser.Expect(TokenKind.EOF);
        if (errors.Count != 0)
        {
            throw new SyntaxError.Exception(errors);
        }
        return result;
    }

    private Expression ParseExpr()
    {
        Expression e = ParseTest();
        if (TokenKindCur != TokenKind.COMMA)
        {
            return e;
        }

        var elems = ImmutableArray.CreateBuilder<Expression>();
        elems.Add(e);
        ParseExprList(elems, false);
        return new ListExpression(locs, true, -1, elems.ToImmutable(), -1);
    }

    private void ReportError(int offset, string format, params object?[] args)
    {
        errorsCount++;
        if (errorsCount <= 5)
        {
            Location location = locs.GetLocation(offset);
            errors.Add(new SyntaxError(location, args.Length == 0 ? format : string.Format(format, args)));
        }
    }

    private void SyntaxErrorMsg(string message)
    {
        SyntaxErrorAt(TokenStart, TokenKindCur, TokenValue, message);
    }

    private void SyntaxErrorAt(int offset, TokenKind tokenKind, object? tokenValue, string message)
    {
        if (!recoveryMode)
        {
            if (tokenKind == TokenKind.INDENT)
            {
                ReportError(offset, "indentation error");
            }
            else
            {
                ReportError(offset, "syntax error at '{0}': {1}", TokenString(tokenKind, tokenValue), message);
            }
            recoveryMode = true;
        }
    }

    private int Expect(TokenKind kind)
    {
        if (TokenKindCur != kind)
        {
            SyntaxErrorMsg("expected " + kind.ToDisplayString());
        }
        return NextToken();
    }

    private int ExpectAndRecover(TokenKind kind)
    {
        if (TokenKindCur != kind)
        {
            SyntaxErrorMsg("expected " + kind.ToDisplayString());
        }
        else
        {
            recoveryMode = false;
        }
        return NextToken();
    }

    private int SyncPast(HashSet<TokenKind> terminatingTokens)
    {
        while (!terminatingTokens.Contains(TokenKindCur))
        {
            NextToken();
        }
        int end = TokenEnd;
        NextToken();
        return end;
    }

    private int SyncTo(HashSet<TokenKind> terminatingTokens)
    {
        int previous = TokenEnd;
        NextToken();
        int current = previous;
        while (!terminatingTokens.Contains(TokenKindCur))
        {
            NextToken();
            previous = current;
            current = TokenEnd;
        }
        return previous;
    }

    private static readonly HashSet<TokenKind> FORBIDDEN_KEYWORDS = new()
    {
        TokenKind.AS,
        TokenKind.ASSERT,
        TokenKind.CLASS,
        TokenKind.DEL,
        TokenKind.EXCEPT,
        TokenKind.FINALLY,
        TokenKind.FROM,
        TokenKind.GLOBAL,
        TokenKind.IMPORT,
        TokenKind.IS,
        TokenKind.NONLOCAL,
        TokenKind.RAISE,
        TokenKind.TRY,
        TokenKind.WITH,
        TokenKind.WHILE,
        TokenKind.YIELD,
    };

    private void CheckForbiddenKeywords()
    {
        if (!FORBIDDEN_KEYWORDS.Contains(TokenKindCur))
        {
            return;
        }
        string message = TokenKindCur switch
        {
            TokenKind.ASSERT => "'assert' not supported, use 'fail' instead",
            TokenKind.DEL => "'del' not supported, use '.pop()' to delete an item from a dictionary or a list",
            TokenKind.IMPORT => "'import' not supported, use 'load' instead",
            TokenKind.IS => "'is' not supported, use '==' instead",
            TokenKind.RAISE => "'raise' not supported, use 'fail' instead",
            TokenKind.TRY => "'try' not supported, all exceptions are fatal",
            TokenKind.WHILE => "'while' not supported, use 'for' instead",
            _ => "keyword '" + TokenKindCur.ToDisplayString() + "' not supported",
        };
        ReportError(TokenStart, "{0}", message);
    }

    private int NextToken()
    {
        int prev = TokenStart;
        if (TokenKindCur != TokenKind.EOF)
        {
            lexer.NextToken();
        }
        CheckForbiddenKeywords();
        if (DEBUGGING)
        {
            Console.Error.Write(TokenString(TokenKindCur, TokenValue));
        }
        return prev;
    }

    private Identifier MakeErrorExpression(int start, int end)
    {
        return new Identifier(locs, lexer.BufferSlice(start, end), start);
    }

    private Argument ParseArgument()
    {
        Expression expr;

        if (TokenKindCur == TokenKind.STAR_STAR)
        {
            int starStarOffset = NextToken();
            expr = ParseTest();
            return new Argument.StarStar(locs, starStarOffset, expr);
        }

        if (TokenKindCur == TokenKind.STAR)
        {
            int starOffset = NextToken();
            expr = ParseTest();
            return new Argument.Star(locs, starOffset, expr);
        }

        expr = ParseTest();
        if (expr is Identifier id)
        {
            if (TokenKindCur == TokenKind.EQUALS)
            {
                NextToken();
                Expression arg = ParseTest();
                return new Argument.Keyword(locs, id, arg);
            }
        }

        return new Argument.Positional(locs, expr);
    }

    private Parameter ParseParameter(bool defStatement)
    {
        Expression? type = null;

        if (TokenKindCur == TokenKind.STAR_STAR)
        {
            int starStarOffset = NextToken();
            Identifier id = ParseIdent();
            if (defStatement)
            {
                type = MaybeParseTypeAnnotationAfter(TokenKind.COLON);
            }
            return new Parameter.StarStar(locs, starStarOffset, id, type);
        }

        if (TokenKindCur == TokenKind.STAR)
        {
            int starOffset = NextToken();
            if (TokenKindCur == TokenKind.IDENTIFIER)
            {
                Identifier id = ParseIdent();
                if (defStatement)
                {
                    type = MaybeParseTypeAnnotationAfter(TokenKind.COLON);
                }
                return new Parameter.Star(locs, starOffset, id, type);
            }
            return new Parameter.Star(locs, starOffset, null, null);
        }

        Identifier id2 = ParseIdent();

        if (defStatement)
        {
            type = MaybeParseTypeAnnotationAfter(TokenKind.COLON);
        }

        if (TokenKindCur == TokenKind.EQUALS)
        {
            NextToken();
            Expression expr = ParseTest();
            return new Parameter.Optional(locs, id2, type, expr);
        }

        return new Parameter.Mandatory(locs, id2, type);
    }

    private Expression ParseCallSuffix(Expression fn)
    {
        ImmutableArray<Argument> args = ImmutableArray<Argument>.Empty;
        int lparenOffset = Expect(TokenKind.LPAREN);
        if (TokenKindCur != TokenKind.RPAREN)
        {
            args = ParseArguments();
        }
        int rparenOffset = Expect(TokenKind.RPAREN);
        return new CallExpression(locs, fn, locs.GetLocation(lparenOffset), args, rparenOffset);
    }

    private Expression ParseCastExpression()
    {
        CheckAllowTypeSyntax(TokenStart, TokenKindCur, TokenValue);
        int startOffset = Expect(TokenKind.CAST);
        Expect(TokenKind.LPAREN);
        Expression typeExpr = ParseTypeExprWithFallback();
        Expect(TokenKind.COMMA);
        Expression valueExpr = ParseTest();
        if (TokenKindCur == TokenKind.COMMA)
        {
            Expect(TokenKind.COMMA);
        }
        int rparenOffset = Expect(TokenKind.RPAREN);
        return new CastExpression(locs, startOffset, typeExpr, valueExpr, rparenOffset);
    }

    private Expression ParseIsInstanceExpression()
    {
        CheckAllowTypeSyntax(TokenStart, TokenKindCur, TokenValue);
        int startOffset = Expect(TokenKind.ISINSTANCE);
        Expect(TokenKind.LPAREN);
        Expression valueExpr = ParseTest();
        Expect(TokenKind.COMMA);
        Expression typeExpr = ParseTypeExprWithFallback();
        if (TokenKindCur == TokenKind.COMMA)
        {
            Expect(TokenKind.COMMA);
        }
        int rparenOffset = Expect(TokenKind.RPAREN);
        return new IsInstanceExpression(locs, startOffset, valueExpr, typeExpr, rparenOffset);
    }

    private ImmutableArray<Argument> ParseArguments()
    {
        bool seenArg = false;
        var list = ImmutableArray.CreateBuilder<Argument>();
        while (TokenKindCur != TokenKind.RPAREN && TokenKindCur != TokenKind.EOF)
        {
            if (seenArg)
            {
                if (TokenKindCur == TokenKind.FOR)
                {
                    SyntaxErrorMsg("Starlark does not support Python-style generator expressions");
                }
                Expect(TokenKind.COMMA);
                if (TokenKindCur == TokenKind.RPAREN)
                {
                    break;
                }
            }
            list.Add(ParseArgument());
            seenArg = true;
        }
        return list.ToImmutable();
    }

    private Expression ParseSelectorSuffix(Expression e)
    {
        int dotOffset = Expect(TokenKind.DOT);
        if (TokenKindCur == TokenKind.IDENTIFIER)
        {
            Identifier id = ParseIdent();
            return new DotExpression(locs, e, dotOffset, id);
        }

        SyntaxErrorMsg("expected identifier after dot");
        SyncTo(EXPR_TERMINATOR_SET);
        return e;
    }

    private void ParseExprList(ImmutableArray<Expression>.Builder list, bool trailingCommaAllowed)
    {
        while (TokenKindCur == TokenKind.COMMA)
        {
            Expect(TokenKind.COMMA);
            if (EXPR_LIST_TERMINATOR_SET.Contains(TokenKindCur))
            {
                if (!trailingCommaAllowed)
                {
                    ReportError(TokenStart, "Trailing comma is allowed only in parenthesized tuples.");
                }
                break;
            }
            list.Add(ParseTest());
        }
    }

    private List<DictExpression.Entry> ParseDictEntryList()
    {
        var list = new List<DictExpression.Entry>();
        while (TokenKindCur != TokenKind.RBRACE)
        {
            list.Add(ParseDictEntry());
            if (TokenKindCur == TokenKind.COMMA)
            {
                NextToken();
            }
            else
            {
                break;
            }
        }
        return list;
    }

    private DictExpression.Entry ParseDictEntry()
    {
        Expression key = ParseTest();
        int colonOffset = Expect(TokenKind.COLON);
        Expression value = ParseTest();
        return new DictExpression.Entry(locs, key, colonOffset, value);
    }

    private StringLiteral ParseStringLiteral()
    {
        StringLiteral literal =
            new StringLiteral(locs, TokenStart, Intern((string)TokenValue!), TokenEnd);
        NextToken();
        if (TokenKindCur == TokenKind.STRING)
        {
            ReportError(TokenStart, "Implicit string concatenation is forbidden, use the + operator");
        }
        return literal;
    }

    private Expression ParsePrimary()
    {
        switch (TokenKindCur)
        {
            case TokenKind.INT:
                {
                    var literal = new IntLiteral(locs, lexer.GetRaw(), TokenStart, TokenValue!);
                    NextToken();
                    return literal;
                }

            case TokenKind.FLOAT:
                {
                    var literal = new FloatLiteral(locs, lexer.GetRaw(), TokenStart, (double)TokenValue!);
                    NextToken();
                    return literal;
                }

            case TokenKind.STRING:
                return ParseStringLiteral();

            case TokenKind.IDENTIFIER:
                return ParseIdent();

            case TokenKind.LBRACKET:
                return ParseListMaker();

            case TokenKind.LBRACE:
                return ParseDictExpression();

            case TokenKind.LPAREN:
                {
                    int lparenOffset = NextToken();

                    if (TokenKindCur == TokenKind.RPAREN)
                    {
                        int rparen = NextToken();
                        return new ListExpression(locs, true, lparenOffset, ImmutableArray<Expression>.Empty, rparen);
                    }

                    Expression e = ParseTest();

                    if (TokenKindCur == TokenKind.RPAREN)
                    {
                        NextToken();
                        return e;
                    }

                    if (TokenKindCur == TokenKind.COMMA)
                    {
                        var elems = ImmutableArray.CreateBuilder<Expression>();
                        elems.Add(e);
                        ParseExprList(elems, true);
                        int rparenOffset = Expect(TokenKind.RPAREN);
                        return new ListExpression(locs, true, lparenOffset, elems.ToImmutable(), rparenOffset);
                    }

                    if (TokenKindCur == TokenKind.FOR)
                    {
                        SyntaxErrorMsg("Starlark does not support Python-style generator expressions");
                    }

                    Expect(TokenKind.RPAREN);
                    int end = SyncTo(EXPR_TERMINATOR_SET);
                    return MakeErrorExpression(lparenOffset, end);
                }

            case TokenKind.MINUS:
            case TokenKind.PLUS:
            case TokenKind.TILDE:
                {
                    TokenKind op = TokenKindCur;
                    int offset = NextToken();
                    Expression x = ParsePrimaryWithSuffix();
                    return new UnaryOperatorExpression(locs, op, offset, x);
                }

            case TokenKind.CAST:
                return ParseCastExpression();

            case TokenKind.ISINSTANCE:
                return ParseIsInstanceExpression();

            case TokenKind.ELLIPSIS:
                {
                    if (!insideTypeExpr)
                    {
                        SyntaxErrorMsg("ellipsis ('...') is not allowed outside type expressions");
                    }
                    int offset = NextToken();
                    return new Ellipsis(locs, offset);
                }

            default:
                {
                    int start = TokenStart;
                    SyntaxErrorMsg("expected expression");
                    int end = SyncTo(EXPR_TERMINATOR_SET);
                    return MakeErrorExpression(start, end);
                }
        }
    }

    private Expression ParsePrimaryWithSuffix()
    {
        Expression e = ParsePrimary();
        while (true)
        {
            if (TokenKindCur == TokenKind.DOT)
            {
                e = ParseSelectorSuffix(e);
            }
            else if (TokenKindCur == TokenKind.LBRACKET)
            {
                e = ParseSliceSuffix(e);
            }
            else if (TokenKindCur == TokenKind.LPAREN)
            {
                e = ParseCallSuffix(e);
            }
            else
            {
                return e;
            }
        }
    }

    private Expression ParseSliceSuffix(Expression e)
    {
        int lbracketOffset = Expect(TokenKind.LBRACKET);
        Expression? start = null;
        Expression? end = null;
        Expression? step = null;

        if (TokenKindCur != TokenKind.COLON)
        {
            start = ParseExpr();

            if (TokenKindCur == TokenKind.RBRACKET)
            {
                int rbracketOffset = Expect(TokenKind.RBRACKET);
                return new IndexExpression(locs, e, lbracketOffset, start, rbracketOffset);
            }
        }

        Expect(TokenKind.COLON);
        if (TokenKindCur != TokenKind.COLON && TokenKindCur != TokenKind.RBRACKET)
        {
            end = ParseTest();
        }
        if (TokenKindCur == TokenKind.COLON)
        {
            Expect(TokenKind.COLON);
            if (TokenKindCur != TokenKind.RBRACKET)
            {
                step = ParseTest();
            }
        }
        int rbracketOffset2 = Expect(TokenKind.RBRACKET);
        return new SliceExpression(locs, e, lbracketOffset, start, end, step, rbracketOffset2);
    }

    private Expression ParseForLoopVariables()
    {
        Expression e1 = ParsePrimaryWithSuffix();
        if (TokenKindCur != TokenKind.COMMA)
        {
            return e1;
        }

        var elems = ImmutableArray.CreateBuilder<Expression>();
        elems.Add(e1);
        while (TokenKindCur == TokenKind.COMMA)
        {
            Expect(TokenKind.COMMA);
            if (EXPR_LIST_TERMINATOR_SET.Contains(TokenKindCur))
            {
                break;
            }
            elems.Add(ParsePrimaryWithSuffix());
        }
        return new ListExpression(locs, true, -1, elems.ToImmutable(), -1);
    }

    private Expression ParseComprehensionSuffix(int loffset, Node body, TokenKind closingBracket)
    {
        var clauses = ImmutableArray.CreateBuilder<Comprehension.Clause>();
        while (true)
        {
            if (TokenKindCur == TokenKind.FOR)
            {
                int forOffset = NextToken();
                Expression vars = ParseForLoopVariables();
                Expect(TokenKind.IN);
                Expression seq = ParseTest(0);
                clauses.Add(new Comprehension.For(locs, forOffset, vars, seq));
            }
            else if (TokenKindCur == TokenKind.IF)
            {
                int ifOffset = NextToken();
                Expression cond = ParseTestNoCond();
                clauses.Add(new Comprehension.If(locs, ifOffset, cond));
            }
            else if (TokenKindCur == closingBracket)
            {
                break;
            }
            else
            {
                SyntaxErrorMsg("expected '" + closingBracket.ToDisplayString() + "', 'for' or 'if'");
                int end = SyncPast(LIST_TERMINATOR_SET);
                return MakeErrorExpression(loffset, end);
            }
        }

        bool isDict = closingBracket == TokenKind.RBRACE;
        int roffset = Expect(closingBracket);
        return new Comprehension(locs, isDict, loffset, body, clauses.ToImmutable(), roffset);
    }

    private Expression ParseListMaker()
    {
        int lbracketOffset = Expect(TokenKind.LBRACKET);
        if (TokenKindCur == TokenKind.RBRACKET)
        {
            int rbracketOffset = NextToken();
            return new ListExpression(locs, false, lbracketOffset, ImmutableArray<Expression>.Empty, rbracketOffset);
        }

        Expression expression = ParseTest();
        switch (TokenKindCur)
        {
            case TokenKind.RBRACKET:
                {
                    int rbracketOffset = NextToken();
                    return new ListExpression(
                        locs, false, lbracketOffset, ImmutableArray.Create(expression), rbracketOffset);
                }

            case TokenKind.FOR:
                return ParseComprehensionSuffix(lbracketOffset, expression, TokenKind.RBRACKET);

            case TokenKind.COMMA:
                {
                    var elems = ImmutableArray.CreateBuilder<Expression>();
                    elems.Add(expression);
                    ParseExprList(elems, true);
                    if (TokenKindCur == TokenKind.RBRACKET)
                    {
                        int rbracketOffset = NextToken();
                        return new ListExpression(locs, false, lbracketOffset, elems.ToImmutable(), rbracketOffset);
                    }

                    Expect(TokenKind.RBRACKET);
                    int end = SyncPast(LIST_TERMINATOR_SET);
                    return MakeErrorExpression(lbracketOffset, end);
                }

            default:
                {
                    SyntaxErrorMsg("expected ',', 'for' or ']'");
                    int end = SyncPast(LIST_TERMINATOR_SET);
                    return MakeErrorExpression(lbracketOffset, end);
                }
        }
    }

    private Expression ParseDictExpression()
    {
        int lbraceOffset = Expect(TokenKind.LBRACE);
        if (TokenKindCur == TokenKind.RBRACE)
        {
            int rbraceOffset = NextToken();
            return new DictExpression(locs, lbraceOffset, new List<DictExpression.Entry>(), rbraceOffset);
        }

        DictExpression.Entry entry = ParseDictEntry();
        if (TokenKindCur == TokenKind.FOR)
        {
            return ParseComprehensionSuffix(lbraceOffset, entry, TokenKind.RBRACE);
        }

        var entries = new List<DictExpression.Entry> { entry };
        if (TokenKindCur == TokenKind.COMMA)
        {
            Expect(TokenKind.COMMA);
            entries.AddRange(ParseDictEntryList());
        }
        if (TokenKindCur == TokenKind.RBRACE)
        {
            int rbraceOffset = NextToken();
            return new DictExpression(locs, lbraceOffset, entries, rbraceOffset);
        }

        Expect(TokenKind.RBRACE);
        int end = SyncPast(DICT_TERMINATOR_SET);
        return MakeErrorExpression(lbraceOffset, end);
    }

    private Identifier ParseIdent()
    {
        if (TokenKindCur != TokenKind.IDENTIFIER)
        {
            int start = TokenStart;
            int end = Expect(TokenKind.IDENTIFIER);
            return MakeErrorExpression(start, end);
        }

        string name = (string)TokenValue!;
        int offset = NextToken();
        return new Identifier(locs, name, offset);
    }

    private Expression ParseBinOpExpression(int prec)
    {
        Expression x = ParseTest(prec + 1);
        TokenKind? lastOp = null;
        for (; ; )
        {
            if (TokenKindCur == TokenKind.NOT)
            {
                Expect(TokenKind.NOT);
                if (TokenKindCur != TokenKind.IN)
                {
                    SyntaxErrorMsg("expected 'in'");
                }
                lexer.Kind = TokenKind.NOT_IN;
            }

            TokenKind op = TokenKindCur;
            if (!operatorPrecedence[prec].Contains(op))
            {
                return x;
            }

            if (lastOp != null && operatorPrecedence[prec].Contains(TokenKind.EQUALS_EQUALS))
            {
                ReportError(
                    TokenStart,
                    "Operator '{0}' is not associative with operator '{1}'. Use parens.",
                    lastOp.Value.ToDisplayString(),
                    op.ToDisplayString());
            }

            int opOffset = NextToken();
            Expression y = ParseTest(prec + 1);
            x = OptimizeBinOpExpression(x, op, opOffset, y);
            lastOp = op;
        }
    }

    private Expression OptimizeBinOpExpression(Expression x, TokenKind op, int opOffset, Expression y)
    {
        if (op == TokenKind.PLUS && x is StringLiteral sx && y is StringLiteral sy)
        {
            return new StringLiteral(
                locs,
                x.GetStartOffset(),
                Intern(sx.GetValue() + sy.GetValue()),
                y.GetEndOffset());
        }
        return new BinaryOperatorExpression(locs, x, op, opOffset, y);
    }

    private bool CheckAllowTypeSyntax(int offset, TokenKind tokenKind, object? tokenValue)
    {
        if (options.AllowTypeSyntax)
        {
            return true;
        }
        SyntaxErrorAt(offset, tokenKind, tokenValue, "type annotations are disallowed");
        return false;
    }

    private Expression? MaybeParseTypeAnnotationAfter(TokenKind expectedToken)
    {
        if (TokenKindCur == expectedToken && CheckAllowTypeSyntax(TokenStart, TokenKindCur, TokenValue))
        {
            NextToken();
            return ParseTypeExprWithFallback();
        }
        return null;
    }

    private Expression ParseTypeExprWithFallback()
    {
        Expression result;
        this.insideTypeExpr = true;
        if (options.TolerateInvalidTypeExpressions)
        {
            result = ParseTest();
        }
        else
        {
            result = ParseTypeExpr();
        }
        this.insideTypeExpr = false;
        return result;
    }

    private Expression ParseTypeExpr()
    {
        if (TokenKindCur != TokenKind.IDENTIFIER)
        {
            int start = TokenStart;
            SyntaxErrorMsg("expected a type");
            int end = SyncTo(EXPR_TERMINATOR_SET);
            return MakeErrorExpression(start, end);
        }
        Identifier typeOrConstructor = ParseIdent();
        Expression expr;
        if (TokenKindCur == TokenKind.LBRACKET)
        {
            expr = ParseTypeApplication(typeOrConstructor);
        }
        else
        {
            expr = typeOrConstructor;
        }
        while (TokenKindCur == TokenKind.PIPE)
        {
            int opOffset = NextToken();
            Identifier secondTypeOrConstructor = ParseIdent();
            Expression y;
            if (TokenKindCur == TokenKind.LBRACKET)
            {
                y = ParseTypeApplication(secondTypeOrConstructor);
            }
            else
            {
                y = secondTypeOrConstructor;
            }
            expr = new BinaryOperatorExpression(locs, expr, TokenKind.PIPE, opOffset, y);
        }
        return expr;
    }

    private Expression ParseTypeArgument()
    {
        switch (TokenKindCur)
        {
            case TokenKind.LBRACKET:
                return ParseTypeList();
            case TokenKind.LBRACE:
                return ParseTypeDict();
            case TokenKind.LPAREN:
                {
                    int lparenOffset = Expect(TokenKind.LPAREN);
                    int rparenOffset = Expect(TokenKind.RPAREN);
                    return new ListExpression(locs, true, lparenOffset, ImmutableArray<Expression>.Empty, rparenOffset);
                }
            case TokenKind.STRING:
                return ParseStringLiteral();
            case TokenKind.ELLIPSIS:
                return ParsePrimary();
            default:
                break;
        }
        if (TokenKindCur != TokenKind.IDENTIFIER)
        {
            int start = TokenStart;
            SyntaxErrorMsg("expected a type argument");
            int end = SyncTo(EXPR_TERMINATOR_SET);
            return MakeErrorExpression(start, end);
        }
        return ParseTypeExpr();
    }

    private Expression ParseTypeList()
    {
        int lbracketOffset = Expect(TokenKind.LBRACKET);
        var elems = ImmutableArray.CreateBuilder<Expression>();
        if (TokenKindCur != TokenKind.RBRACKET)
        {
            elems.Add(ParseTypeArgument());
        }
        while (TokenKindCur != TokenKind.RBRACKET && TokenKindCur != TokenKind.EOF)
        {
            Expect(TokenKind.COMMA);
            if (TokenKindCur == TokenKind.RBRACKET)
            {
                break;
            }
            elems.Add(ParseTypeArgument());
        }
        int rbracketOffset = NextToken();
        return new ListExpression(locs, false, lbracketOffset, elems.ToImmutable(), rbracketOffset);
    }

    private DictExpression.Entry ParseTypeDictEntry()
    {
        Expression key = ParseStringLiteral();
        int colonOffset = Expect(TokenKind.COLON);
        Expression value = ParseTypeArgument();
        return new DictExpression.Entry(locs, key, colonOffset, value);
    }

    private Expression ParseTypeDict()
    {
        int lbraceOffset = Expect(TokenKind.LBRACE);

        var entries = new List<DictExpression.Entry>();
        if (TokenKindCur != TokenKind.RBRACE)
        {
            entries.Add(ParseTypeDictEntry());
        }
        while (TokenKindCur != TokenKind.RBRACE && TokenKindCur != TokenKind.EOF)
        {
            Expect(TokenKind.COMMA);
            if (TokenKindCur == TokenKind.RBRACE)
            {
                break;
            }
            entries.Add(ParseTypeDictEntry());
        }

        int rbraceOffset = NextToken();
        return new DictExpression(locs, lbraceOffset, entries, rbraceOffset);
    }

    private Expression ParseTypeApplication(Identifier constructor)
    {
        Expect(TokenKind.LBRACKET);
        var args = ImmutableArray.CreateBuilder<Expression>();
        args.Add(ParseTypeArgument());
        while (TokenKindCur != TokenKind.RBRACKET && TokenKindCur != TokenKind.EOF)
        {
            Expect(TokenKind.COMMA);
            args.Add(ParseTypeArgument());
        }
        int rbracketOffset = Expect(TokenKind.RBRACKET);
        return new TypeApplication(locs, constructor, args.ToImmutable(), rbracketOffset);
    }

    private static bool IsTypeSoftKeyword(Node node)
    {
        return node is Identifier id && id.GetName() == TYPE_SOFT_KEYWORD;
    }

    private Statement ParseTypeAliasStatementTail(Node typeSoftKeywordNode)
    {
        int startOffset = typeSoftKeywordNode.GetStartOffset();
        CheckAllowTypeSyntax(startOffset, TokenKind.IDENTIFIER, TYPE_SOFT_KEYWORD);
        Identifier identifier = ParseIdent();
        ImmutableArray<Identifier> parameters = ParseOptionalTypeParameters();
        Expect(TokenKind.EQUALS);
        Expression definition = ParseTypeExprWithFallback();
        return new TypeAliasStatement(locs, startOffset, identifier, parameters, definition);
    }

    private ImmutableArray<Identifier> ParseOptionalTypeParameters()
    {
        if (TokenKindCur == TokenKind.LBRACKET)
        {
            CheckAllowTypeSyntax(TokenStart, TokenKindCur, TokenValue);
            NextToken();
            var parameters = ImmutableArray.CreateBuilder<Identifier>();
            var uniqueParameterNames = new HashSet<string>();
            parameters.Add(ParseTypeParameter(uniqueParameterNames));
            while (TokenKindCur != TokenKind.RBRACKET && TokenKindCur != TokenKind.EOF)
            {
                Expect(TokenKind.COMMA);
                if (TokenKindCur == TokenKind.RBRACKET)
                {
                    break;
                }
                parameters.Add(ParseTypeParameter(uniqueParameterNames));
            }
            Expect(TokenKind.RBRACKET);
            return parameters.ToImmutable();
        }
        return ImmutableArray<Identifier>.Empty;
    }

    private Identifier ParseTypeParameter(HashSet<string> uniqueParameterNames)
    {
        int tokenStart = TokenStart;
        TokenKind tokenKind = TokenKindCur;
        object? tokenValue = TokenValue;
        Identifier ident = ParseIdent();
        if (Identifier.IsValid(ident.GetName()) && !uniqueParameterNames.Add(ident.GetName()))
        {
            SyntaxErrorAt(tokenStart, tokenKind, tokenValue, "duplicate type parameter");
        }
        return ident;
    }

    private Expression ParseTest()
    {
        int start = TokenStart;
        if (TokenKindCur == TokenKind.LAMBDA)
        {
            return ParseLambda(true);
        }

        Expression expr = ParseTest(0);
        if (TokenKindCur == TokenKind.IF)
        {
            NextToken();
            Expression condition = ParseTest(0);
            if (TokenKindCur == TokenKind.ELSE)
            {
                NextToken();
                Expression elseClause = ParseTest();
                return new ConditionalExpression(locs, expr, condition, elseClause);
            }
            else
            {
                ReportError(start, "missing else clause in conditional expression or semicolon before if");
                return expr;
            }
        }
        return expr;
    }

    private Expression ParseTest(int prec)
    {
        if (prec >= operatorPrecedence.Count)
        {
            return ParsePrimaryWithSuffix();
        }
        if (TokenKindCur == TokenKind.NOT && operatorPrecedence[prec].Contains(TokenKind.NOT))
        {
            return ParseNotExpression(prec);
        }
        return ParseBinOpExpression(prec);
    }

    private LambdaExpression ParseLambda(bool allowCond)
    {
        int lambdaOffset = Expect(TokenKind.LAMBDA);
        ImmutableArray<Parameter> parameters = ParseParameters(false);
        Expect(TokenKind.COLON);
        Expression body = allowCond ? ParseTest() : ParseTestNoCond();
        return new LambdaExpression(locs, lambdaOffset, parameters, body);
    }

    private Expression ParseTestNoCond()
    {
        if (TokenKindCur == TokenKind.LAMBDA)
        {
            return ParseLambda(false);
        }
        return ParseTest(0);
    }

    private Expression ParseNotExpression(int prec)
    {
        int notOffset = Expect(TokenKind.NOT);
        Expression x = ParseTest(prec);
        return new UnaryOperatorExpression(locs, TokenKind.NOT, notOffset, x);
    }

    private ImmutableArray<Statement> ParseFileInput()
    {
        var list = ImmutableArray.CreateBuilder<Statement>();
        while (TokenKindCur != TokenKind.EOF)
        {
            if (TokenKindCur == TokenKind.NEWLINE)
            {
                ExpectAndRecover(TokenKind.NEWLINE);
            }
            else if (recoveryMode)
            {
                SyncTo(STATEMENT_TERMINATOR_SET);
                recoveryMode = false;
            }
            else
            {
                MaybeParseDocCommentBlock();
                if (TokenKindCur == TokenKind.EOF)
                {
                    break;
                }
                ParseStatement(list);
            }
        }
        return list.ToImmutable();
    }

    private Statement ParseLoadStatement()
    {
        int loadOffset = Expect(TokenKind.LOAD);
        Expect(TokenKind.LPAREN);
        if (TokenKindCur != TokenKind.STRING)
        {
            var module0 = new StringLiteral(locs, TokenStart, "", TokenEnd);
            Expect(TokenKind.STRING);
            return new LoadStatement(locs, loadOffset, module0, ImmutableArray<LoadStatement.Binding>.Empty, TokenEnd);
        }

        StringLiteral module = ParseStringLiteral();
        if (TokenKindCur == TokenKind.RPAREN)
        {
            SyntaxErrorMsg("expected at least one symbol to load");
            return new LoadStatement(locs, loadOffset, module, ImmutableArray<LoadStatement.Binding>.Empty, TokenEnd);
        }
        Expect(TokenKind.COMMA);

        var bindings = ImmutableArray.CreateBuilder<LoadStatement.Binding>();
        ParseLoadSymbol(bindings);
        while (TokenKindCur != TokenKind.RPAREN && TokenKindCur != TokenKind.EOF)
        {
            Expect(TokenKind.COMMA);
            if (TokenKindCur == TokenKind.RPAREN)
            {
                break;
            }
            ParseLoadSymbol(bindings);
        }

        int rparen = Expect(TokenKind.RPAREN);
        return new LoadStatement(locs, loadOffset, module, bindings.ToImmutable(), rparen);
    }

    private void ParseLoadSymbol(ImmutableArray<LoadStatement.Binding>.Builder symbols)
    {
        if (TokenKindCur != TokenKind.STRING && TokenKindCur != TokenKind.IDENTIFIER)
        {
            SyntaxErrorMsg("expected either a literal string or an identifier");
            return;
        }

        string name = (string)TokenValue!;
        int nameOffset = TokenStart + (TokenKindCur == TokenKind.STRING ? 1 : 0);
        Identifier local = new Identifier(locs, name, nameOffset);

        Identifier original;
        if (TokenKindCur == TokenKind.STRING)
        {
            original = local;
        }
        else
        {
            Expect(TokenKind.IDENTIFIER);
            Expect(TokenKind.EQUALS);
            if (TokenKindCur != TokenKind.STRING)
            {
                SyntaxErrorMsg("expected string");
                return;
            }
            original = new Identifier(locs, (string)TokenValue!, TokenStart + 1);
        }
        NextToken();
        symbols.Add(new LoadStatement.Binding(local, original));
    }

    private void ParseSimpleStatement(ImmutableArray<Statement>.Builder list)
    {
        list.Add(ParseSmallStatement());
        mostRecentDocCommentBlock = null;

        while (TokenKindCur == TokenKind.SEMI)
        {
            NextToken();
            if (TokenKindCur == TokenKind.NEWLINE || TokenKindCur == TokenKind.DOC_COMMENT_TRAILING)
            {
                break;
            }
            list.Add(ParseSmallStatement());
        }
        if (TokenKindCur == TokenKind.DOC_COMMENT_TRAILING)
        {
            NextToken();
        }
        ExpectAndRecover(TokenKind.NEWLINE);
    }

    private DocComments? MaybeParseTrailingDocComment(Location statementStart)
    {
        DocComments? result;
        if (TokenKindCur == TokenKind.DOC_COMMENT_TRAILING)
        {
            result = (DocComments)TokenValue!;
            NextToken();
        }
        else
        {
            result = GetDocCommentBlockOnPreviousLine(statementStart.Line);
        }
        return result;
    }

    private Statement ParseSmallStatement()
    {
        if (TokenKindCur == TokenKind.RETURN)
        {
            return ParseReturnStatement();
        }

        if (TokenKindCur == TokenKind.BREAK
            || TokenKindCur == TokenKind.CONTINUE
            || TokenKindCur == TokenKind.PASS)
        {
            TokenKind kind = TokenKindCur;
            int offset = NextToken();
            return new FlowStatement(locs, kind, offset);
        }

        if (TokenKindCur == TokenKind.LOAD)
        {
            return ParseLoadStatement();
        }

        Expression lhs = ParseExpr();

        if (TokenKindCur == TokenKind.IDENTIFIER && IsTypeSoftKeyword(lhs))
        {
            return ParseTypeAliasStatementTail(lhs);
        }

        int colonOffset = TokenStart;
        Expression? type = MaybeParseTypeAnnotationAfter(TokenKind.COLON);

        TokenKind? op = augmentedAssignments.TryGetValue(TokenKindCur, out TokenKind aug) ? aug : null;
        if (TokenKindCur == TokenKind.EQUALS || op != null)
        {
            int opOffset = NextToken();
            Expression rhs = ParseExpr();
            DocComments? docComments = MaybeParseTrailingDocComment(lhs.GetStartLocation());
            if (type != null)
            {
                if (lhs is not Identifier)
                {
                    SyntaxErrorAt(
                        colonOffset,
                        TokenKind.COLON,
                        null,
                        "type annotations must have a single identifier on the left-hand side");
                    type = null;
                }
                if (op != null)
                {
                    SyntaxErrorAt(
                        colonOffset,
                        TokenKind.COLON,
                        null,
                        "type annotations not allowed on augmented assignment statements");
                    type = null;
                }
            }
            return new AssignmentStatement(locs, lhs, type, op, opOffset, rhs, docComments);
        }
        else if (type != null)
        {
            DocComments? docComments = MaybeParseTrailingDocComment(lhs.GetStartLocation());
            if (lhs is not Identifier id)
            {
                SyntaxErrorAt(
                    colonOffset,
                    TokenKind.COLON,
                    null,
                    "type annotations must have a single identifier on the left-hand side");
                return new ExpressionStatement(
                    locs, MakeErrorExpression(lhs.GetStartOffset(), type.GetEndOffset()));
            }
            return new VarStatement(locs, id, type, docComments);
        }
        else
        {
            return new ExpressionStatement(locs, lhs);
        }
    }

    private IfStatement ParseIfStatement()
    {
        int ifOffset = Expect(TokenKind.IF);
        Expression cond = ParseTest();
        Expect(TokenKind.COLON);
        ImmutableArray<Statement> body = ParseSuite();
        IfStatement ifStmt = new IfStatement(locs, TokenKind.IF, ifOffset, cond, body);
        IfStatement tail = ifStmt;
        while (TokenKindCur == TokenKind.ELIF)
        {
            int elifOffset = Expect(TokenKind.ELIF);
            cond = ParseTest();
            Expect(TokenKind.COLON);
            body = ParseSuite();
            IfStatement elif = new IfStatement(locs, TokenKind.ELIF, elifOffset, cond, body);
            tail.SetElseBlock(ImmutableArray.Create<Statement>(elif));
            tail = elif;
        }
        if (TokenKindCur == TokenKind.ELSE)
        {
            Expect(TokenKind.ELSE);
            Expect(TokenKind.COLON);
            body = ParseSuite();
            tail.SetElseBlock(body);
        }
        return ifStmt;
    }

    private ForStatement ParseForStatement()
    {
        int forOffset = Expect(TokenKind.FOR);
        Expression vars = ParseForLoopVariables();
        Expect(TokenKind.IN);
        Expression collection = ParseExpr();
        Expect(TokenKind.COLON);
        ImmutableArray<Statement> body = ParseSuite();
        return new ForStatement(locs, forOffset, vars, collection, body);
    }

    private DefStatement ParseDefStatement()
    {
        int defOffset = Expect(TokenKind.DEF);
        Identifier ident = ParseIdent();
        ImmutableArray<Identifier> typeParams = ParseOptionalTypeParameters();
        Expect(TokenKind.LPAREN);
        ImmutableArray<Parameter> parameters = ParseParameters(true);
        Expect(TokenKind.RPAREN);
        Expression? returnType = MaybeParseTypeAnnotationAfter(TokenKind.RARROW);
        Expect(TokenKind.COLON);
        ImmutableArray<Statement> block = ParseSuite();
        return new DefStatement(locs, defOffset, ident, typeParams, parameters, returnType, block);
    }

    private ImmutableArray<Parameter> ParseParameters(bool defStatement)
    {
        bool hasParam = false;
        var list = ImmutableArray.CreateBuilder<Parameter>();

        while (TokenKindCur != TokenKind.RPAREN
            && TokenKindCur != TokenKind.COLON
            && TokenKindCur != TokenKind.EOF)
        {
            if (hasParam)
            {
                Expect(TokenKind.COMMA);
                if (TokenKindCur == TokenKind.RPAREN)
                {
                    break;
                }
            }
            Parameter param = ParseParameter(defStatement);
            hasParam = true;
            list.Add(param);
        }
        return list.ToImmutable();
    }

    private ImmutableArray<Statement> ParseSuite()
    {
        var list = ImmutableArray.CreateBuilder<Statement>();
        if (TokenKindCur == TokenKind.DOC_COMMENT_TRAILING)
        {
            NextToken();
        }
        if (TokenKindCur == TokenKind.NEWLINE)
        {
            Expect(TokenKind.NEWLINE);
            MaybeParseDocCommentBlock();
            if (TokenKindCur != TokenKind.INDENT)
            {
                ReportError(TokenStart, "expected an indented block");
                return list.ToImmutable();
            }
            Expect(TokenKind.INDENT);
            while (TokenKindCur != TokenKind.OUTDENT && TokenKindCur != TokenKind.EOF)
            {
                ParseStatement(list);
                MaybeParseDocCommentBlock();
            }
            ExpectAndRecover(TokenKind.OUTDENT);
        }
        else
        {
            ParseSimpleStatement(list);
        }
        return list.ToImmutable();
    }

    private ReturnStatement ParseReturnStatement()
    {
        int returnOffset = Expect(TokenKind.RETURN);

        Expression? result = null;
        if (!STATEMENT_TERMINATOR_SET.Contains(TokenKindCur))
        {
            result = ParseExpr();
        }
        return new ReturnStatement(locs, returnOffset, result);
    }
}
