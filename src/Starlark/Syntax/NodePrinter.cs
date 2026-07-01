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

using System.Globalization;
using System.Text;

namespace Starlark.Syntax;

/// <summary>A pretty-printer for Starlark syntax trees.</summary>
internal sealed class NodePrinter
{
    private readonly StringBuilder buf;
    private int indent;

    internal NodePrinter(StringBuilder buf)
    {
        this.buf = buf;
    }

    internal NodePrinter(StringBuilder buf, int indent)
    {
        this.buf = buf;
        this.indent = indent;
    }

    // Main entry point for an arbitrary node. Called by Node.PrettyPrint.
    internal void PrintNode(Node n)
    {
        if (n is Expression expr)
        {
            PrintExpr(expr);
        }
        else if (n is Statement stmt)
        {
            PrintStmt(stmt);
        }
        else if (n is StarlarkFile file)
        {
            foreach (Statement s in file.GetStatements())
            {
                PrintStmt(s);
            }
        }
        else if (n is Comment comment)
        {
            PrintIndent();
            buf.Append(comment.GetText());
        }
        else if (n is Argument argument)
        {
            PrintArgument(argument);
        }
        else if (n is Parameter parameter)
        {
            PrintParameter(parameter);
        }
        else if (n is DictExpression.Entry entry)
        {
            PrintDictEntry(entry);
        }
        else
        {
            throw new ArgumentException("unexpected: " + n.GetType());
        }
    }

    private void PrintSuite(IReadOnlyList<Statement> statements)
    {
        indent++;
        foreach (Statement stmt in statements)
        {
            PrintStmt(stmt);
        }
        indent--;
    }

    private void PrintIndent()
    {
        for (int i = 0; i < indent; i++)
        {
            buf.Append("  ");
        }
    }

    private void PrintArgument(Argument arg)
    {
        if (arg is Argument.Positional)
        {
            // nop
        }
        else if (arg is Argument.Keyword keyword)
        {
            buf.Append(keyword.GetIdentifier().GetName());
            buf.Append(" = ");
        }
        else if (arg is Argument.Star)
        {
            buf.Append('*');
        }
        else if (arg is Argument.StarStar)
        {
            buf.Append("**");
        }
        PrintExpr(arg.GetValue(), true);
    }

    private void PrintParameter(Parameter param)
    {
        if (param is Parameter.Mandatory)
        {
            buf.Append(param.GetName());
        }
        else if (param is Parameter.Optional)
        {
            buf.Append(param.GetName());
            buf.Append('=');
            PrintExpr(param.GetDefaultValue()!);
        }
        else if (param is Parameter.Star)
        {
            buf.Append('*');
            if (param.GetName() != null)
            {
                buf.Append(param.GetName());
            }
        }
        else if (param is Parameter.StarStar)
        {
            buf.Append("**");
            buf.Append(param.GetName());
        }
    }

    private void PrintDictEntry(DictExpression.Entry e)
    {
        PrintExpr(e.GetKey());
        buf.Append(": ");
        PrintExpr(e.GetValue());
    }

    // Appends "def f(a, ..., z):" to the buf. Also used by DefStatement.ToString.
    internal void PrintDefSignature(DefStatement def)
    {
        buf.Append("def ");
        PrintExpr(def.GetIdentifier());
        if (def.GetTypeParameters().Count != 0)
        {
            buf.Append('[');
            string sep = "";
            foreach (Identifier typeParam in def.GetTypeParameters())
            {
                buf.Append(sep);
                PrintExpr(typeParam);
                sep = ", ";
            }
            buf.Append(']');
        }
        buf.Append('(');
        string psep = "";
        foreach (Parameter param in def.GetParameters())
        {
            buf.Append(psep);
            PrintParameter(param);
            if (param.GetType() != null)
            {
                buf.Append(": ");
                PrintExpr(param.GetType()!, true);
            }
            psep = ", ";
        }
        buf.Append(')');
        if (def.GetReturnType() != null)
        {
            buf.Append(" -> ");
            PrintExpr(def.GetReturnType()!, true);
        }
        buf.Append(':');
    }

    private void PrintStmt(Statement s)
    {
        PrintIndent();

        switch (s.Kind)
        {
            case Statement.StatementKind.ASSIGNMENT:
                {
                    var stmt = (AssignmentStatement)s;
                    PrintExpr(stmt.GetLHS());
                    Expression? type = stmt.GetType();
                    if (type != null)
                    {
                        buf.Append(" : ");
                        PrintExpr(type);
                    }
                    buf.Append(' ');
                    if (stmt.IsAugmented())
                    {
                        buf.Append(stmt.GetOperator()!.Value.ToDisplayString());
                    }
                    buf.Append("= ");
                    PrintExpr(stmt.GetRHS());
                    buf.Append('\n');
                    break;
                }

            case Statement.StatementKind.EXPRESSION:
                {
                    var stmt = (ExpressionStatement)s;
                    PrintExpr(stmt.GetExpression());
                    buf.Append('\n');
                    break;
                }

            case Statement.StatementKind.FLOW:
                {
                    var stmt = (FlowStatement)s;
                    buf.Append(stmt.GetFlowKind().ToDisplayString()).Append('\n');
                    break;
                }

            case Statement.StatementKind.FOR:
                {
                    var stmt = (ForStatement)s;
                    buf.Append("for ");
                    PrintExpr(stmt.GetVars());
                    buf.Append(" in ");
                    PrintExpr(stmt.GetCollection());
                    buf.Append(":\n");
                    PrintSuite(stmt.GetBody());
                    break;
                }

            case Statement.StatementKind.DEF:
                {
                    var stmt = (DefStatement)s;
                    PrintDefSignature(stmt);
                    buf.Append('\n');
                    PrintSuite(stmt.GetBody());
                    break;
                }

            case Statement.StatementKind.IF:
                {
                    var stmt = (IfStatement)s;
                    buf.Append(stmt.IsElif() ? "elif " : "if ");
                    PrintExpr(stmt.GetCondition());
                    buf.Append(":\n");
                    PrintSuite(stmt.GetThenBlock());
                    IReadOnlyList<Statement>? elseBlock = stmt.GetElseBlock();
                    if (elseBlock != null)
                    {
                        if (elseBlock.Count == 1
                            && elseBlock[0] is IfStatement inner
                            && inner.IsElif())
                        {
                            PrintStmt(elseBlock[0]);
                        }
                        else
                        {
                            PrintIndent();
                            buf.Append("else:\n");
                            PrintSuite(elseBlock);
                        }
                    }
                    break;
                }

            case Statement.StatementKind.LOAD:
                {
                    var stmt = (LoadStatement)s;
                    buf.Append("load(");
                    PrintExpr(stmt.GetImport());
                    foreach (LoadStatement.Binding binding in stmt.GetBindings())
                    {
                        buf.Append(", ");
                        Identifier local = binding.GetLocalName();
                        string origName = binding.GetOriginalName().GetName();
                        if (origName == local.GetName())
                        {
                            buf.Append('"');
                            PrintExpr(local);
                            buf.Append('"');
                        }
                        else
                        {
                            PrintExpr(local);
                            buf.Append("=\"");
                            buf.Append(origName);
                            buf.Append('"');
                        }
                    }
                    buf.Append(")\n");
                    break;
                }

            case Statement.StatementKind.RETURN:
                {
                    var stmt = (ReturnStatement)s;
                    buf.Append("return");
                    if (stmt.GetResult() != null)
                    {
                        buf.Append(' ');
                        PrintExpr(stmt.GetResult()!);
                    }
                    buf.Append('\n');
                    break;
                }

            case Statement.StatementKind.TYPE_ALIAS:
                {
                    var stmt = (TypeAliasStatement)s;
                    buf.Append("type ");
                    PrintExpr(stmt.GetIdentifier());
                    if (stmt.GetParameters().Count != 0)
                    {
                        buf.Append('[');
                        string sep = "";
                        foreach (Identifier param in stmt.GetParameters())
                        {
                            buf.Append(sep);
                            PrintExpr(param);
                            sep = ", ";
                        }
                        buf.Append(']');
                    }
                    buf.Append(" = ");
                    PrintExpr(stmt.GetDefinition(), true);
                    buf.Append('\n');
                    break;
                }

            case Statement.StatementKind.VAR:
                {
                    var stmt = (VarStatement)s;
                    PrintExpr(stmt.GetIdentifier());
                    buf.Append(" : ");
                    PrintExpr(stmt.GetType());
                    buf.Append('\n');
                    break;
                }
        }
    }

    private void PrintExpr(Expression expr)
    {
        PrintExpr(expr, false);
    }

    private void PrintExpr(Expression expr, bool canSkipParenthesis)
    {
        switch (expr.Kind)
        {
            case Expression.ExpressionKind.BINARY_OPERATOR:
                {
                    var binop = (BinaryOperatorExpression)expr;
                    if (!canSkipParenthesis)
                    {
                        buf.Append('(');
                    }
                    PrintExpr(binop.GetX());
                    buf.Append(' ');
                    buf.Append(binop.GetOperator().ToDisplayString());
                    buf.Append(' ');
                    PrintExpr(binop.GetY());
                    if (!canSkipParenthesis)
                    {
                        buf.Append(')');
                    }
                    break;
                }

            case Expression.ExpressionKind.COMPREHENSION:
                {
                    var comp = (Comprehension)expr;
                    buf.Append(comp.IsDict() ? '{' : '[');
                    PrintNode(comp.GetBody());
                    foreach (Comprehension.Clause clause in comp.GetClauses())
                    {
                        buf.Append(' ');
                        if (clause is Comprehension.For forClause)
                        {
                            buf.Append("for ");
                            PrintExpr(forClause.GetVars());
                            buf.Append(" in ");
                            PrintExpr(forClause.GetIterable());
                        }
                        else
                        {
                            var ifClause = (Comprehension.If)clause;
                            buf.Append("if ");
                            PrintExpr(ifClause.GetCondition());
                        }
                    }
                    buf.Append(comp.IsDict() ? '}' : ']');
                    break;
                }

            case Expression.ExpressionKind.CONDITIONAL:
                {
                    var cond = (ConditionalExpression)expr;
                    PrintExpr(cond.GetThenCase());
                    buf.Append(" if ");
                    PrintExpr(cond.GetCondition());
                    buf.Append(" else ");
                    PrintExpr(cond.GetElseCase());
                    break;
                }

            case Expression.ExpressionKind.DICT_EXPR:
                {
                    var dictexpr = (DictExpression)expr;
                    buf.Append('{');
                    string sep = "";
                    foreach (DictExpression.Entry entry in dictexpr.GetEntries())
                    {
                        buf.Append(sep);
                        PrintDictEntry(entry);
                        sep = ", ";
                    }
                    buf.Append('}');
                    break;
                }

            case Expression.ExpressionKind.DOT:
                {
                    var dot = (DotExpression)expr;
                    PrintExpr(dot.GetObject());
                    buf.Append('.');
                    PrintExpr(dot.GetField());
                    break;
                }

            case Expression.ExpressionKind.CALL:
                {
                    var call = (CallExpression)expr;
                    PrintExpr(call.GetFunction());
                    buf.Append('(');
                    string sep = "";
                    foreach (Argument arg in call.GetArguments())
                    {
                        buf.Append(sep);
                        PrintArgument(arg);
                        sep = ", ";
                    }
                    buf.Append(')');
                    break;
                }

            case Expression.ExpressionKind.CAST:
                {
                    var cast = (CastExpression)expr;
                    buf.Append("cast(");
                    PrintExpr(cast.GetType(), true);
                    buf.Append(", ");
                    PrintExpr(cast.GetValue(), true);
                    buf.Append(')');
                    break;
                }

            case Expression.ExpressionKind.ELLIPSIS:
                {
                    buf.Append("...");
                    break;
                }

            case Expression.ExpressionKind.IDENTIFIER:
                buf.Append(((Identifier)expr).GetName());
                break;

            case Expression.ExpressionKind.INDEX:
                {
                    var index = (IndexExpression)expr;
                    PrintExpr(index.GetObject());
                    buf.Append('[');
                    PrintExpr(index.GetKey());
                    buf.Append(']');
                    break;
                }

            case Expression.ExpressionKind.INT_LITERAL:
                {
                    buf.Append(((IntLiteral)expr).GetValue());
                    break;
                }

            case Expression.ExpressionKind.ISINSTANCE:
                {
                    var isinstance = (IsInstanceExpression)expr;
                    buf.Append("isinstance(");
                    PrintExpr(isinstance.GetValue(), true);
                    buf.Append(", ");
                    PrintExpr(isinstance.GetType(), true);
                    buf.Append(')');
                    break;
                }

            case Expression.ExpressionKind.FLOAT_LITERAL:
                {
                    buf.Append(FormatDouble(((FloatLiteral)expr).GetValue()));
                    break;
                }

            case Expression.ExpressionKind.LAMBDA:
                {
                    var lambda = (LambdaExpression)expr;
                    buf.Append("lambda");
                    string sep = " ";
                    foreach (Parameter param in lambda.GetParameters())
                    {
                        buf.Append(sep);
                        sep = ", ";
                        PrintParameter(param);
                    }
                    buf.Append(": ");
                    PrintExpr(lambda.GetBody());
                    break;
                }

            case Expression.ExpressionKind.LIST_EXPR:
                {
                    var list = (ListExpression)expr;
                    buf.Append(list.IsTuple() ? '(' : '[');
                    string sep = "";
                    foreach (Expression e in list.GetElements())
                    {
                        buf.Append(sep);
                        PrintExpr(e, true);
                        sep = ", ";
                    }
                    if (list.IsTuple() && list.GetElements().Count == 1)
                    {
                        buf.Append(',');
                    }
                    buf.Append(list.IsTuple() ? ')' : ']');
                    break;
                }

            case Expression.ExpressionKind.SLICE:
                {
                    var slice = (SliceExpression)expr;
                    PrintExpr(slice.GetObject());
                    buf.Append('[');
                    if (slice.GetStart() != null)
                    {
                        PrintExpr(slice.GetStart()!);
                    }
                    buf.Append(':');
                    if (slice.GetStop() != null)
                    {
                        PrintExpr(slice.GetStop()!);
                    }
                    if (slice.GetStep() != null)
                    {
                        buf.Append(':');
                        PrintExpr(slice.GetStep()!);
                    }
                    buf.Append(']');
                    break;
                }

            case Expression.ExpressionKind.STRING_LITERAL:
                {
                    var literal = (StringLiteral)expr;
                    string value = literal.GetValue();
                    buf.Append('"');
                    for (int i = 0; i < value.Length; i++)
                    {
                        char c = value[i];
                        switch (c)
                        {
                            case '"':
                                buf.Append("\\\"");
                                break;
                            case '\\':
                                buf.Append("\\\\");
                                break;
                            case '\r':
                                buf.Append("\\r");
                                break;
                            case '\n':
                                buf.Append("\\n");
                                break;
                            case '\t':
                                buf.Append("\\t");
                                break;
                            default:
                                if (c < 32)
                                {
                                    buf.Append(string.Format("\\x{0:x2}", (int)c));
                                }
                                else
                                {
                                    buf.Append(c);
                                }
                                break;
                        }
                    }
                    buf.Append('"');
                    break;
                }

            case Expression.ExpressionKind.UNARY_OPERATOR:
                {
                    var unop = (UnaryOperatorExpression)expr;
                    buf.Append(unop.GetOperator() == TokenKind.NOT ? "not " : unop.GetOperator().ToDisplayString());
                    if (!canSkipParenthesis)
                    {
                        buf.Append('(');
                    }
                    PrintExpr(unop.GetX());
                    if (!canSkipParenthesis)
                    {
                        buf.Append(')');
                    }
                    break;
                }

            case Expression.ExpressionKind.TYPE_APPLICATION:
                {
                    var typeApplication = (TypeApplication)expr;
                    PrintExpr(typeApplication.GetConstructor());
                    buf.Append('[');
                    string sep = "";
                    foreach (Expression arg in typeApplication.GetArguments())
                    {
                        buf.Append(sep);
                        PrintExpr(arg, true);
                        sep = ", ";
                    }
                    buf.Append(']');
                    break;
                }
        }
    }

    // Approximates Java's Double.toString formatting (always includes a decimal point).
    private static string FormatDouble(double d)
    {
        if (double.IsNaN(d))
        {
            return "NaN";
        }
        if (double.IsPositiveInfinity(d))
        {
            return "Infinity";
        }
        if (double.IsNegativeInfinity(d))
        {
            return "-Infinity";
        }
        string s = d.ToString("R", CultureInfo.InvariantCulture);
        if (s.IndexOf('.') < 0 && s.IndexOf('E') < 0 && s.IndexOf('e') < 0)
        {
            s += ".0";
        }
        return s;
    }
}
