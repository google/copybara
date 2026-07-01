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
using System.Globalization;
using System.Text;

namespace Starlark.Syntax;

/// <summary>A scanner for Starlark.</summary>
internal sealed class Lexer
{
    // --- These fields are accessed directly by the parser: ---

    // Mapping from file offsets to Locations.
    internal readonly FileLocations Locs;

    // Information about current token. Updated by NextToken.
    internal TokenKind Kind;
    internal int Start; // start offset
    internal int End; // end offset
    internal object? Value; // String, Integer/Long/BigInteger, or Double value of token

    private bool kindSet; // whether Kind is meaningful (mirrors Java's null check)

    // --- end of parser-visible fields ---

    private readonly List<SyntaxError> errors;

    private readonly FileOptions options;

    // Input buffer and position
    private readonly char[] buffer;
    private int pos;

    // The stack of enclosing indentation levels in spaces.
    private readonly List<int> indentStack = new();

    private readonly ImmutableArray<Comment>.Builder comments = ImmutableArray.CreateBuilder<Comment>();

    private int openParenStackDepth = 0;

    private bool checkIndentation;

    private int dents;

    private bool lineOnlyWhitespaceOrComments;

    private static readonly Dictionary<char, TokenKind> EQUAL_TOKENS = new()
    {
        { '=', TokenKind.EQUALS_EQUALS },
        { '!', TokenKind.NOT_EQUALS },
        { '>', TokenKind.GREATER_EQUALS },
        { '<', TokenKind.LESS_EQUALS },
        { '+', TokenKind.PLUS_EQUALS },
        { '-', TokenKind.MINUS_EQUALS },
        { '*', TokenKind.STAR_EQUALS },
        { '/', TokenKind.SLASH_EQUALS },
        { '%', TokenKind.PERCENT_EQUALS },
        { '^', TokenKind.CARET_EQUALS },
        { '&', TokenKind.AMPERSAND_EQUALS },
        { '|', TokenKind.PIPE_EQUALS },
    };

    internal Lexer(ParserInput input, List<SyntaxError> errors, FileOptions options)
    {
        this.Locs = FileLocations.Create(input.GetContent(), input.GetFile());
        this.buffer = input.GetContent();
        this.pos = 0;
        this.errors = errors;
        this.options = options;
        this.checkIndentation = true;
        this.dents = 0;
        this.lineOnlyWhitespaceOrComments = true;

        indentStack.Add(0);
    }

    internal IReadOnlyList<Comment> GetComments() => comments.ToImmutable();

    /// <summary>
    /// Reads the next token, updating the Lexer's token fields. The end state is EOF, after which any
    /// further calls to <c>NextToken()</c> will produce only EOF.
    /// </summary>
    internal void NextToken()
    {
        bool afterNewline =
            kindSet && (Kind == TokenKind.NEWLINE || Kind == TokenKind.DOC_COMMENT_BLOCK);
        Tokenize();
        if (!kindSet)
        {
            throw new InvalidOperationException("kind not set");
        }

        if (Kind == TokenKind.EOF && !afterNewline)
        {
            Kind = TokenKind.NEWLINE;
        }
        if (Kind != TokenKind.NEWLINE
            && Kind != TokenKind.INDENT
            && Kind != TokenKind.OUTDENT
            && Kind != TokenKind.DOC_COMMENT_BLOCK
            && Kind != TokenKind.DOC_COMMENT_TRAILING)
        {
            lineOnlyWhitespaceOrComments = false;
        }
    }

    private void PopParen()
    {
        if (openParenStackDepth == 0)
        {
            Error("indentation error", pos - 1);
        }
        else
        {
            openParenStackDepth--;
        }
    }

    private void Error(string message, int errPos)
    {
        errors.Add(new SyntaxError(Locs.GetLocation(errPos), message));
    }

    private void SetToken(TokenKind kind, int start, int end)
    {
        this.Kind = kind;
        this.kindSet = true;
        this.Start = start;
        this.End = end;
        this.Value = null;
    }

    private void SetValue(object value)
    {
        this.Value = value;
    }

    /// <summary>Returns the raw input text associated with the current token.</summary>
    internal string GetRaw() => BufferSlice(Start, End);

    private void Newline()
    {
        lineOnlyWhitespaceOrComments = true;
        if (openParenStackDepth > 0)
        {
            NewlineInsideExpression();
        }
        else
        {
            checkIndentation = true;
            SetToken(TokenKind.NEWLINE, pos - 1, pos);
        }
    }

    private void NewlineInsideExpression()
    {
        while (pos < buffer.Length)
        {
            switch (buffer[pos])
            {
                case ' ':
                case '\t':
                case '\r':
                    pos++;
                    break;
                default:
                    return;
            }
        }
    }

    private void ComputeIndentation()
    {
        int indentLen = 0;
        while (pos < buffer.Length)
        {
            char c = buffer[pos];
            if (c == ' ')
            {
                indentLen++;
                pos++;
            }
            else if (c == '\r')
            {
                pos++;
            }
            else if (c == '\t')
            {
                indentLen++;
                pos++;
                Error("Tab characters are not allowed for indentation. Use spaces instead.", pos);
            }
            else if (c == '\n')
            {
                indentLen = 0;
                pos++;
            }
            else if (c == '#')
            {
                if (Peek(1) == ':' && openParenStackDepth == 0)
                {
                    return;
                }
                int oldPos = pos;
                ScanToNewline();
                AddComment(oldPos, pos);
                indentLen = 0;
            }
            else
            {
                break;
            }
        }

        if (pos == buffer.Length)
        {
            indentLen = 0;
        }

        int peekedIndent = IndentPeek();
        if (peekedIndent < indentLen)
        {
            indentStack.Add(indentLen);
            dents++;
        }
        else if (peekedIndent > indentLen)
        {
            while (peekedIndent > indentLen)
            {
                indentStack.RemoveAt(indentStack.Count - 1);
                dents--;
                peekedIndent = IndentPeek();
            }

            if (peekedIndent < indentLen)
            {
                Error("indentation error", pos - 1);
            }
        }
    }

    private int IndentPeek() => indentStack[indentStack.Count - 1];

    private bool SkipTripleQuote(char quot)
    {
        if (Peek(0) == quot && Peek(1) == quot)
        {
            pos += 2;
            return true;
        }
        return false;
    }

    private void EscapedStringLiteral(char quot, bool isRaw)
    {
        int literalStartPos = isRaw ? pos - 2 : pos - 1;
        bool inTriplequote = SkipTripleQuote(quot);
        var literal = new StringBuilder();
        while (pos < buffer.Length)
        {
            char c = buffer[pos];
            pos++;
            switch (c)
            {
                case '\n':
                    if (inTriplequote)
                    {
                        literal.Append(c);
                        break;
                    }
                    else
                    {
                        Error("unclosed string literal", literalStartPos);
                        SetToken(TokenKind.STRING, literalStartPos, pos);
                        SetValue(literal.ToString());
                        return;
                    }
                case '\\':
                    if (pos == buffer.Length)
                    {
                        Error("unclosed string literal", literalStartPos);
                        SetToken(TokenKind.STRING, literalStartPos, pos);
                        SetValue(literal.ToString());
                        return;
                    }
                    if (isRaw)
                    {
                        literal.Append('\\');
                        if (Peek(0) == '\r' && Peek(1) == '\n')
                        {
                            literal.Append('\n');
                            pos += 2;
                        }
                        else if (buffer[pos] == '\r' || buffer[pos] == '\n')
                        {
                            literal.Append('\n');
                            pos += 1;
                        }
                        else
                        {
                            literal.Append(buffer[pos]);
                            pos += 1;
                        }
                        break;
                    }
                    c = buffer[pos];
                    pos++;
                    switch (c)
                    {
                        case '\r':
                            if (Peek(0) == '\n')
                            {
                                pos += 1;
                            }
                            break;
                        case '\n':
                            break;
                        case 'a':
                            literal.Append('\u0007');
                            break;
                        case 'b':
                            literal.Append('\b');
                            break;
                        case 'f':
                            literal.Append('\f');
                            break;
                        case 'n':
                            literal.Append('\n');
                            break;
                        case 'r':
                            literal.Append('\r');
                            break;
                        case 't':
                            literal.Append('\t');
                            break;
                        case 'v':
                            literal.Append('\u000b');
                            break;
                        case '\\':
                            literal.Append('\\');
                            break;
                        case '\'':
                            literal.Append('\'');
                            break;
                        case '"':
                            literal.Append('"');
                            break;
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                            {
                                int octal = c - '0';
                                if (pos < buffer.Length)
                                {
                                    c = buffer[pos];
                                    if (c >= '0' && c <= '7')
                                    {
                                        pos++;
                                        octal = (octal << 3) | (c - '0');
                                        if (pos < buffer.Length)
                                        {
                                            c = buffer[pos];
                                            if (c >= '0' && c <= '7')
                                            {
                                                pos++;
                                                octal = (octal << 3) | (c - '0');
                                            }
                                        }
                                    }
                                }
                                if (octal > 0xff)
                                {
                                    Error("octal escape sequence out of range (maximum is \\377)", pos - 1);
                                }
                                else if (options.StringLiteralsAreAsciiOnly && octal >= 0x80)
                                {
                                    Error("octal escape sequence denotes non-ASCII character", pos - 1);
                                }
                                literal.Append((char)(octal & 0xff));
                                break;
                            }
                        case 'N':
                        case 'u':
                        case 'U':
                        default:
                            Error("invalid escape sequence: \\" + c + ". Use '\\\\' to insert '\\'.", pos - 1);
                            literal.Append('\\');
                            literal.Append(c);
                            break;
                    }
                    break;
                case '\'':
                case '"':
                    if (c != quot || (inTriplequote && !SkipTripleQuote(quot)))
                    {
                        literal.Append(c);
                    }
                    else
                    {
                        SetToken(TokenKind.STRING, literalStartPos, pos);
                        SetValue(literal.ToString());
                        return;
                    }
                    break;
                default:
                    literal.Append(c);
                    if (options.StringLiteralsAreAsciiOnly && c >= 0x80)
                    {
                        Error("string literal contains non-ASCII character", pos - 1);
                    }
                    break;
            }
        }
        Error("unclosed string literal", literalStartPos);
        SetToken(TokenKind.STRING, literalStartPos, pos);
        SetValue(literal.ToString());
    }

    private void StringLiteralScan(char quot, bool isRaw)
    {
        int literalStartPos = isRaw ? pos - 2 : pos - 1;
        int contentStartPos = pos;

        if (SkipTripleQuote(quot))
        {
            pos -= 2;
            EscapedStringLiteral(quot, isRaw);
            return;
        }

        while (pos < buffer.Length)
        {
            char c = buffer[pos++];
            switch (c)
            {
                case '\n':
                    Error("unclosed string literal", literalStartPos);
                    SetToken(TokenKind.STRING, literalStartPos, pos);
                    SetValue(BufferSlice(contentStartPos, pos - 1));
                    return;
                case '\\':
                    if (isRaw)
                    {
                        if (Peek(0) == '\r' && Peek(1) == '\n')
                        {
                            pos = contentStartPos;
                            EscapedStringLiteral(quot, true);
                            return;
                        }
                        else
                        {
                            pos++;
                            break;
                        }
                    }
                    pos = contentStartPos;
                    EscapedStringLiteral(quot, false);
                    return;
                case '\'':
                case '"':
                    if (c == quot)
                    {
                        SetToken(TokenKind.STRING, literalStartPos, pos);
                        SetValue(BufferSlice(contentStartPos, pos - 1));
                        if (options.StringLiteralsAreAsciiOnly)
                        {
                            for (int i = contentStartPos; i < pos - 1; i++)
                            {
                                if (buffer[i] >= 0x80)
                                {
                                    Error("string literal contains non-ASCII character", i);
                                }
                            }
                        }
                        return;
                    }
                    break;
                default:
                    break;
            }
        }

        if (pos > buffer.Length)
        {
            pos = buffer.Length;
        }

        Error("unclosed string literal", literalStartPos);
        SetToken(TokenKind.STRING, literalStartPos, pos);
        SetValue(BufferSlice(contentStartPos, pos));
    }

    private void DocCommentsScan(Comment first, int firstStartPos, bool isBlock)
    {
        int lastEndPos = pos;
        var docComments = new List<Comment> { first };
        if (isBlock)
        {
            int prevLine = first.GetStartLocation().Line;
            while (Peek(0) == '\n')
            {
                checkIndentation = false;
                ComputeIndentation();
                if (Peek(0) != '#' || Peek(1) != ':')
                {
                    break;
                }
                int line = Locs.GetLocation(pos).Line;
                if (line != prevLine + 1)
                {
                    break;
                }
                prevLine = line;
                int startPos = pos;
                ScanToNewline();
                Comment comment = AddComment(startPos, pos);
                lastEndPos = pos;
                docComments.Add(comment);
            }
            SetToken(TokenKind.DOC_COMMENT_BLOCK, firstStartPos, lastEndPos);
        }
        else
        {
            SetToken(TokenKind.DOC_COMMENT_TRAILING, firstStartPos, lastEndPos);
        }
        SetValue(new DocComments(docComments));
    }

    private void ScanToNewline()
    {
        for (; pos < buffer.Length; pos++)
        {
            if (buffer[pos] == '\n')
            {
                break;
            }
        }
    }

    private static readonly Dictionary<string, TokenKind> keywordMap = new()
    {
        { "and", TokenKind.AND },
        { "as", TokenKind.AS },
        { "assert", TokenKind.ASSERT },
        { "break", TokenKind.BREAK },
        { "class", TokenKind.CLASS },
        { "continue", TokenKind.CONTINUE },
        { "def", TokenKind.DEF },
        { "del", TokenKind.DEL },
        { "elif", TokenKind.ELIF },
        { "else", TokenKind.ELSE },
        { "except", TokenKind.EXCEPT },
        { "finally", TokenKind.FINALLY },
        { "for", TokenKind.FOR },
        { "from", TokenKind.FROM },
        { "global", TokenKind.GLOBAL },
        { "if", TokenKind.IF },
        { "import", TokenKind.IMPORT },
        { "in", TokenKind.IN },
        { "is", TokenKind.IS },
        { "lambda", TokenKind.LAMBDA },
        { "load", TokenKind.LOAD },
        { "nonlocal", TokenKind.NONLOCAL },
        { "not", TokenKind.NOT },
        { "or", TokenKind.OR },
        { "pass", TokenKind.PASS },
        { "raise", TokenKind.RAISE },
        { "return", TokenKind.RETURN },
        { "try", TokenKind.TRY },
        { "while", TokenKind.WHILE },
        { "with", TokenKind.WITH },
        { "yield", TokenKind.YIELD },
    };

    private static readonly Dictionary<string, TokenKind> typeSyntaxExtraKeywordMap = new()
    {
        { "cast", TokenKind.CAST },
        { "isinstance", TokenKind.ISINSTANCE },
    };

    private void IdentifierOrKeyword()
    {
        int oldPos = pos - 1;
        string id = string.Intern(ScanIdentifier());
        if (!keywordMap.TryGetValue(id, out TokenKind kind))
        {
            if (options.AllowTypeSyntax && typeSyntaxExtraKeywordMap.TryGetValue(id, out TokenKind tk))
            {
                SetToken(tk, oldPos, pos);
                return;
            }
            SetToken(TokenKind.IDENTIFIER, oldPos, pos);
            SetValue(id);
        }
        else
        {
            SetToken(kind, oldPos, pos);
        }
    }

    private string ScanIdentifier()
    {
        int oldPos = pos - 1;
        while (pos < buffer.Length)
        {
            char c = buffer[pos];
            if (c == '_'
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9'))
            {
                pos++;
            }
            else
            {
                return BufferSlice(oldPos, pos);
            }
        }
        return BufferSlice(oldPos, pos);
    }

    private bool TokenizeTwoChars()
    {
        if (pos + 2 >= buffer.Length)
        {
            return false;
        }
        char c1 = buffer[pos];
        char c2 = buffer[pos + 1];
        TokenKind? tok = null;
        if (c2 == '=')
        {
            if (EQUAL_TOKENS.TryGetValue(c1, out TokenKind t))
            {
                tok = t;
            }
        }
        else if (c2 == '*' && c1 == '*')
        {
            tok = TokenKind.STAR_STAR;
        }
        if (tok == null)
        {
            return false;
        }
        SetToken(tok.Value, pos, pos + 2);
        return true;
    }

    // Returns the ith unconsumed char, or -1 for EOF.
    private int Peek(int i) => pos + i < buffer.Length ? buffer[pos + i] : -1;

    // Consumes a char and returns the next unconsumed char, or -1 for EOF.
    private int Next()
    {
        pos++;
        return Peek(0);
    }

    private void Tokenize()
    {
        if (checkIndentation)
        {
            checkIndentation = false;
            ComputeIndentation();
        }

        if (dents != 0)
        {
            if (dents < 0)
            {
                dents++;
                SetToken(TokenKind.OUTDENT, pos - 1, pos);
            }
            else
            {
                dents--;
                SetToken(TokenKind.INDENT, pos - 1, pos);
            }
            return;
        }

        kindSet = false;
        while (pos < buffer.Length)
        {
            if (TokenizeTwoChars())
            {
                pos += 2;
                return;
            }
            char c = buffer[pos];
            pos++;
            switch (c)
            {
                case '{':
                    SetToken(TokenKind.LBRACE, pos - 1, pos);
                    openParenStackDepth++;
                    break;
                case '}':
                    SetToken(TokenKind.RBRACE, pos - 1, pos);
                    PopParen();
                    break;
                case '(':
                    SetToken(TokenKind.LPAREN, pos - 1, pos);
                    openParenStackDepth++;
                    break;
                case ')':
                    SetToken(TokenKind.RPAREN, pos - 1, pos);
                    PopParen();
                    break;
                case '[':
                    SetToken(TokenKind.LBRACKET, pos - 1, pos);
                    openParenStackDepth++;
                    break;
                case ']':
                    SetToken(TokenKind.RBRACKET, pos - 1, pos);
                    PopParen();
                    break;
                case '>':
                    if (Peek(0) == '>' && Peek(1) == '=')
                    {
                        SetToken(TokenKind.GREATER_GREATER_EQUALS, pos - 1, pos + 2);
                        pos += 2;
                    }
                    else if (Peek(0) == '>')
                    {
                        SetToken(TokenKind.GREATER_GREATER, pos - 1, pos + 1);
                        pos += 1;
                    }
                    else
                    {
                        SetToken(TokenKind.GREATER, pos - 1, pos);
                    }
                    break;
                case '<':
                    if (Peek(0) == '<' && Peek(1) == '=')
                    {
                        SetToken(TokenKind.LESS_LESS_EQUALS, pos - 1, pos + 2);
                        pos += 2;
                    }
                    else if (Peek(0) == '<')
                    {
                        SetToken(TokenKind.LESS_LESS, pos - 1, pos + 1);
                        pos += 1;
                    }
                    else
                    {
                        SetToken(TokenKind.LESS, pos - 1, pos);
                    }
                    break;
                case ':':
                    SetToken(TokenKind.COLON, pos - 1, pos);
                    break;
                case ',':
                    SetToken(TokenKind.COMMA, pos - 1, pos);
                    break;
                case '+':
                    SetToken(TokenKind.PLUS, pos - 1, pos);
                    break;
                case '-':
                    if (Peek(0) == '>')
                    {
                        SetToken(TokenKind.RARROW, pos - 1, pos + 1);
                        pos += 1;
                    }
                    else
                    {
                        SetToken(TokenKind.MINUS, pos - 1, pos);
                    }
                    break;
                case '|':
                    SetToken(TokenKind.PIPE, pos - 1, pos);
                    break;
                case '=':
                    SetToken(TokenKind.EQUALS, pos - 1, pos);
                    break;
                case '%':
                    SetToken(TokenKind.PERCENT, pos - 1, pos);
                    break;
                case '~':
                    SetToken(TokenKind.TILDE, pos - 1, pos);
                    break;
                case '&':
                    SetToken(TokenKind.AMPERSAND, pos - 1, pos);
                    break;
                case '^':
                    SetToken(TokenKind.CARET, pos - 1, pos);
                    break;
                case '/':
                    if (Peek(0) == '/' && Peek(1) == '=')
                    {
                        SetToken(TokenKind.SLASH_SLASH_EQUALS, pos - 1, pos + 2);
                        pos += 2;
                    }
                    else if (Peek(0) == '/')
                    {
                        SetToken(TokenKind.SLASH_SLASH, pos - 1, pos + 1);
                        pos += 1;
                    }
                    else
                    {
                        SetToken(TokenKind.SLASH, pos - 1, pos);
                    }
                    break;
                case ';':
                    SetToken(TokenKind.SEMI, pos - 1, pos);
                    break;
                case '*':
                    SetToken(TokenKind.STAR, pos - 1, pos);
                    break;
                case ' ':
                case '\t':
                case '\r':
                    break;
                case '\\':
                    if (Peek(0) == '\n')
                    {
                        pos += 1;
                    }
                    else if (Peek(0) == '\r' && Peek(1) == '\n')
                    {
                        pos += 2;
                    }
                    else
                    {
                        SetToken(TokenKind.ILLEGAL, pos - 1, pos);
                        SetValue(c.ToString());
                    }
                    break;
                case '\n':
                    Newline();
                    break;
                case '#':
                    {
                        int oldPos = pos - 1;
                        ScanToNewline();
                        Comment comment = AddComment(oldPos, pos);
                        if (comment.HasDocCommentPrefix() && openParenStackDepth == 0)
                        {
                            DocCommentsScan(comment, oldPos, lineOnlyWhitespaceOrComments);
                        }
                        break;
                    }
                case '\'':
                case '"':
                    StringLiteralScan(c, false);
                    break;
                default:
                    // detect raw strings, e.g. r"str"
                    if (c == 'r')
                    {
                        int c0 = Peek(0);
                        if (c0 == '\'' || c0 == '"')
                        {
                            pos++;
                            StringLiteralScan((char)c0, true);
                            break;
                        }
                    }

                    if (c == '.' || IsDigit(c))
                    {
                        pos--; // unconsume
                        ScanNumberOrDotOrEllipsis(c);
                        break;
                    }

                    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_')
                    {
                        IdentifierOrKeyword();
                    }
                    else
                    {
                        Error("invalid character: '" + c + "'", pos - 1);
                    }
                    break;
            }
            if (kindSet)
            {
                return;
            }
        }

        if (indentStack.Count > 1)
        {
            SetToken(TokenKind.NEWLINE, pos - 1, pos);
            while (indentStack.Count > 1)
            {
                indentStack.RemoveAt(indentStack.Count - 1);
                dents--;
            }
            return;
        }

        SetToken(TokenKind.EOF, pos, pos);
    }

    private void ScanNumberOrDotOrEllipsis(int c)
    {
        int start = this.pos;
        bool fraction = false;
        bool exponent = false;

        if (c == '.')
        {
            if (!IsDigit(Peek(1)))
            {
                if (Peek(1) == '.' && Peek(2) == '.')
                {
                    pos += 3;
                    SetToken(TokenKind.ELLIPSIS, start, pos);
                    return;
                }
                else
                {
                    pos++;
                    SetToken(TokenKind.DOT, start, pos);
                    return;
                }
            }
            fraction = true;
        }
        else if (c == '0')
        {
            c = Next();
            if (c == '.')
            {
                fraction = true;
            }
            else if (c == 'x' || c == 'X')
            {
                c = Next();
                if (!IsXDigit(c))
                {
                    Error("invalid hex literal", start);
                }
                while (IsXDigit(c))
                {
                    c = Next();
                }
            }
            else if (c == 'o' || c == 'O')
            {
                c = Next();
                while (IsDigit(c))
                {
                    c = Next();
                }
            }
            else if (c == 'b' || c == 'B')
            {
                c = Next();
                if (!IsBDigit(c))
                {
                    Error("invalid binary literal", start);
                }
                while (IsBDigit(c))
                {
                    c = Next();
                }
            }
            else
            {
                while (IsDigit(c))
                {
                    c = Next();
                }
                if (c == '.')
                {
                    fraction = true;
                }
                else if (c == 'e' || c == 'E')
                {
                    exponent = true;
                }
            }
        }
        else
        {
            while (IsDigit(c))
            {
                c = Next();
            }
            if (c == '.')
            {
                fraction = true;
            }
            else if (c == 'e' || c == 'E')
            {
                exponent = true;
            }
        }

        if (fraction)
        {
            c = Next(); // consume '.'
            while (IsDigit(c))
            {
                c = Next();
            }

            if (c == 'e' || c == 'E')
            {
                exponent = true;
            }
        }

        if (exponent)
        {
            c = Next(); // consume [eE]
            if (c == '+' || c == '-')
            {
                c = Next();
            }
            while (IsDigit(c))
            {
                c = Next();
            }
        }

        if (fraction || exponent)
        {
            SetToken(TokenKind.FLOAT, start, pos);
            double value = 0.0;
            if (double.TryParse(BufferSlice(start, pos), NumberStyles.Float, CultureInfo.InvariantCulture, out value))
            {
                if (!double.IsFinite(value))
                {
                    Error("floating-point literal too large", start);
                }
            }
            else
            {
                Error("invalid float literal", start);
            }
            SetValue(value);
            return;
        }

        SetToken(TokenKind.INT, start, pos);
        string literal = BufferSlice(start, pos);
        object value2 = 0;
        try
        {
            value2 = IntLiteral.Scan(literal);
        }
        catch (FormatException ex)
        {
            Error(ex.Message, start);
        }
        SetValue(value2);
    }

    private static bool IsDigit(int c) => '0' <= c && c <= '9';

    private static bool IsXDigit(int c) =>
        IsDigit(c) || ('A' <= c && c <= 'F') || ('a' <= c && c <= 'f');

    private static bool IsBDigit(int c) => c == '0' || c == '1';

    /// <summary>
    /// Returns a string containing the part of the source buffer beginning at offset <c>start</c> and
    /// ending immediately before offset <c>end</c>.
    /// </summary>
    internal string BufferSlice(int start, int end) => new string(this.buffer, start, end - start);

    private Comment AddComment(int start, int end)
    {
        string content = BufferSlice(start, end);
        Comment comment = new Comment(Locs, start, content);
        comments.Add(comment);
        return comment;
    }
}
