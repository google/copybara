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

/// <summary>
/// A TokenKind represents the kind of a lexical token. Ported from the Java enum, which associated
/// a human-readable name with each value. The name is preserved via <see cref="Name"/> and
/// <see cref="ToDisplayString"/>.
/// </summary>
public enum TokenKind
{
    AMPERSAND,
    AMPERSAND_EQUALS,
    AND,
    AS,
    ASSERT,
    BREAK,
    CARET,
    CARET_EQUALS,
    /// <summary>Emitted only if --experimental_starlark_type_syntax is enabled.</summary>
    CAST,
    CLASS,
    COLON,
    COMMA,
    CONTINUE,
    DEF,
    DEL,
    /// <summary>A multiline block of Sphinx autodoc-style doc comments.</summary>
    DOC_COMMENT_BLOCK,
    /// <summary>Inline trailing doc comment.</summary>
    DOC_COMMENT_TRAILING,
    DOT,
    ELIF,
    /// <summary>Valid only in type expressions.</summary>
    ELLIPSIS,
    ELSE,
    EOF,
    EQUALS,
    EQUALS_EQUALS,
    EXCEPT,
    FINALLY,
    FLOAT,
    FOR,
    FROM,
    GLOBAL,
    GREATER,
    GREATER_EQUALS,
    GREATER_GREATER,
    GREATER_GREATER_EQUALS,
    IDENTIFIER,
    IF,
    ILLEGAL,
    IMPORT,
    IN,
    INDENT,
    INT,
    IS,
    /// <summary>Emitted only if --experimental_starlark_type_syntax is enabled.</summary>
    ISINSTANCE,
    LAMBDA,
    LBRACE,
    LBRACKET,
    LESS,
    LESS_EQUALS,
    LESS_LESS,
    LESS_LESS_EQUALS,
    LOAD,
    LPAREN,
    MINUS,
    MINUS_EQUALS,
    NEWLINE,
    NONLOCAL,
    NOT,
    NOT_EQUALS,
    NOT_IN,
    OR,
    OUTDENT,
    PASS,
    PERCENT,
    PERCENT_EQUALS,
    PIPE,
    PIPE_EQUALS,
    PLUS,
    PLUS_EQUALS,
    RAISE,
    RARROW,
    RBRACE,
    RBRACKET,
    RETURN,
    RPAREN,
    SEMI,
    SLASH,
    SLASH_EQUALS,
    SLASH_SLASH,
    SLASH_SLASH_EQUALS,
    STAR,
    STAR_EQUALS,
    STAR_STAR,
    STRING,
    TILDE,
    TRY,
    WHILE,
    WITH,
    YIELD,
}

/// <summary>Helpers for <see cref="TokenKind"/> preserving the Java enum's display names.</summary>
public static class TokenKinds
{
    private static readonly string[] Names = BuildNames();

    /// <summary>Returns the human-readable name of a token kind (equivalent to Java's toString()).</summary>
    public static string ToDisplayString(this TokenKind kind) => Names[(int)kind];

    /// <summary>Returns the human-readable name of a token kind.</summary>
    public static string Name(TokenKind kind) => Names[(int)kind];

    private static string[] BuildNames()
    {
        var names = new string[(int)TokenKind.YIELD + 1];
        names[(int)TokenKind.AMPERSAND] = "&";
        names[(int)TokenKind.AMPERSAND_EQUALS] = "&=";
        names[(int)TokenKind.AND] = "and";
        names[(int)TokenKind.AS] = "as";
        names[(int)TokenKind.ASSERT] = "assert";
        names[(int)TokenKind.BREAK] = "break";
        names[(int)TokenKind.CARET] = "^";
        names[(int)TokenKind.CARET_EQUALS] = "^=";
        names[(int)TokenKind.CAST] = "cast";
        names[(int)TokenKind.CLASS] = "class";
        names[(int)TokenKind.COLON] = ":";
        names[(int)TokenKind.COMMA] = ",";
        names[(int)TokenKind.CONTINUE] = "continue";
        names[(int)TokenKind.DEF] = "def";
        names[(int)TokenKind.DEL] = "del";
        names[(int)TokenKind.DOC_COMMENT_BLOCK] = "#:";
        names[(int)TokenKind.DOC_COMMENT_TRAILING] = "trailing #: ";
        names[(int)TokenKind.DOT] = ".";
        names[(int)TokenKind.ELIF] = "elif";
        names[(int)TokenKind.ELLIPSIS] = "...";
        names[(int)TokenKind.ELSE] = "else";
        names[(int)TokenKind.EOF] = "EOF";
        names[(int)TokenKind.EQUALS] = "=";
        names[(int)TokenKind.EQUALS_EQUALS] = "==";
        names[(int)TokenKind.EXCEPT] = "except";
        names[(int)TokenKind.FINALLY] = "finally";
        names[(int)TokenKind.FLOAT] = "float literal";
        names[(int)TokenKind.FOR] = "for";
        names[(int)TokenKind.FROM] = "from";
        names[(int)TokenKind.GLOBAL] = "global";
        names[(int)TokenKind.GREATER] = ">";
        names[(int)TokenKind.GREATER_EQUALS] = ">=";
        names[(int)TokenKind.GREATER_GREATER] = ">>";
        names[(int)TokenKind.GREATER_GREATER_EQUALS] = ">>=";
        names[(int)TokenKind.IDENTIFIER] = "identifier";
        names[(int)TokenKind.IF] = "if";
        names[(int)TokenKind.ILLEGAL] = "illegal character";
        names[(int)TokenKind.IMPORT] = "import";
        names[(int)TokenKind.IN] = "in";
        names[(int)TokenKind.INDENT] = "indent";
        names[(int)TokenKind.INT] = "integer literal";
        names[(int)TokenKind.IS] = "is";
        names[(int)TokenKind.ISINSTANCE] = "isinstance";
        names[(int)TokenKind.LAMBDA] = "lambda";
        names[(int)TokenKind.LBRACE] = "{";
        names[(int)TokenKind.LBRACKET] = "[";
        names[(int)TokenKind.LESS] = "<";
        names[(int)TokenKind.LESS_EQUALS] = "<=";
        names[(int)TokenKind.LESS_LESS] = "<<";
        names[(int)TokenKind.LESS_LESS_EQUALS] = "<<=";
        names[(int)TokenKind.LOAD] = "load";
        names[(int)TokenKind.LPAREN] = "(";
        names[(int)TokenKind.MINUS] = "-";
        names[(int)TokenKind.MINUS_EQUALS] = "-=";
        names[(int)TokenKind.NEWLINE] = "newline";
        names[(int)TokenKind.NONLOCAL] = "nonlocal";
        names[(int)TokenKind.NOT] = "not";
        names[(int)TokenKind.NOT_EQUALS] = "!=";
        names[(int)TokenKind.NOT_IN] = "not in";
        names[(int)TokenKind.OR] = "or";
        names[(int)TokenKind.OUTDENT] = "outdent";
        names[(int)TokenKind.PASS] = "pass";
        names[(int)TokenKind.PERCENT] = "%";
        names[(int)TokenKind.PERCENT_EQUALS] = "%=";
        names[(int)TokenKind.PIPE] = "|";
        names[(int)TokenKind.PIPE_EQUALS] = "|=";
        names[(int)TokenKind.PLUS] = "+";
        names[(int)TokenKind.PLUS_EQUALS] = "+=";
        names[(int)TokenKind.RAISE] = "raise";
        names[(int)TokenKind.RARROW] = "->";
        names[(int)TokenKind.RBRACE] = "}";
        names[(int)TokenKind.RBRACKET] = "]";
        names[(int)TokenKind.RETURN] = "return";
        names[(int)TokenKind.RPAREN] = ")";
        names[(int)TokenKind.SEMI] = ";";
        names[(int)TokenKind.SLASH] = "/";
        names[(int)TokenKind.SLASH_EQUALS] = "/=";
        names[(int)TokenKind.SLASH_SLASH] = "//";
        names[(int)TokenKind.SLASH_SLASH_EQUALS] = "//=";
        names[(int)TokenKind.STAR] = "*";
        names[(int)TokenKind.STAR_EQUALS] = "*=";
        names[(int)TokenKind.STAR_STAR] = "**";
        names[(int)TokenKind.STRING] = "string literal";
        names[(int)TokenKind.TILDE] = "~";
        names[(int)TokenKind.TRY] = "try";
        names[(int)TokenKind.WHILE] = "while";
        names[(int)TokenKind.WITH] = "with";
        names[(int)TokenKind.YIELD] = "yield";
        return names;
    }
}
