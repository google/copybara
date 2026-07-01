// Copyright 2017 The Bazel Authors. All rights reserved.
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

using System.Reflection;
using Starlark.Annot;

namespace Starlark.Eval;

/// <summary>
/// A ParamDescriptor is a descriptor of a formal parameter of a Java method callable from Starlark.
/// Port of <c>net.starlark.java.eval.ParamDescriptor</c>.
///
/// <para>In the .NET port the parameter metadata comes from a <see cref="ParamAttribute"/> applied to
/// the corresponding C# parameter (plus the parameter's declared CLR type).</para>
/// </summary>
public sealed class ParamDescriptor
{
    private readonly string name;
    private readonly object? defaultValue;
    private readonly bool named;
    private readonly bool positional;
    private readonly bool noneable;
    private readonly IReadOnlyList<Type>? allowedClasses; // null means any

    private ParamDescriptor(
        string name,
        string defaultExpr,
        bool named,
        bool positional,
        bool noneable,
        IReadOnlyList<Type>? allowedClasses)
    {
        this.name = name;
        this.defaultValue = string.IsNullOrEmpty(defaultExpr) ? null : EvalDefault(name, defaultExpr);
        this.named = named;
        this.positional = positional;
        this.noneable = noneable;
        this.allowedClasses = allowedClasses;
    }

    /// <summary>Returns the descriptor for the given C# parameter.</summary>
    internal static ParamDescriptor Of(ParameterInfo param, ParamAttribute? attr)
    {
        attr ??= new ParamAttribute();
        string name = string.IsNullOrEmpty(attr.Name) ? param.Name ?? "" : attr.Name;

        // A parameter that is positional by default but not marked named remains positional-only.
        bool positional = attr.Positional;
        bool named = attr.Named;
        if (!positional && !named)
        {
            // Default: positional. (Matches Java Param defaults where positional=true, named=false.)
            positional = true;
        }

        IReadOnlyList<Type>? allowed = null;
        if (attr.AllowedTypes.Length > 0)
        {
            allowed = attr.AllowedTypes;
        }

        return new ParamDescriptor(
            name, attr.DefaultValue, named, positional, attr.Noneable, allowed);
    }

    /// <summary>The parameter's Starlark name.</summary>
    public string Name => name;

    /// <summary>The default value, or null if the parameter is mandatory.</summary>
    public object? DefaultValue => defaultValue;

    /// <summary>Whether the parameter may be specified by name.</summary>
    public bool IsNamed => named;

    /// <summary>Whether the parameter may be specified positionally.</summary>
    public bool IsPositional => positional;

    /// <summary>Whether None is a permissible value.</summary>
    public bool IsNoneable => noneable;

    /// <summary>The allowed CLR classes for this parameter, or null if any is allowed.</summary>
    public IReadOnlyList<Type>? AllowedClasses => allowedClasses;

    public override string ToString() => name;

    // Evaluates the default-value expression of a parameter. Only the small "bootstrap" grammar of
    // literal forms is supported here (which covers every default used by the standard library);
    // this avoids a cycle with the evaluator during type initialization.
    private static object EvalDefault(string name, string expr)
    {
        switch (expr)
        {
            case "None":
                return Starlark.None;
            case "True":
                return true;
            case "False":
                return false;
            case "unbound":
                return Starlark.UNBOUND;
            case "0":
                return StarlarkInt.Of(0);
            case "1":
                return StarlarkInt.Of(1);
            case "-1":
                return StarlarkInt.Of(-1);
            case "[]":
                return StarlarkList.Empty();
            case "()":
                return Tuple.Empty();
            case "{}":
                return Dict.Empty();
            case "\" \"":
                return " ";
            case "\"\"":
                return "";
        }
        // Quoted string literal.
        if (expr.Length >= 2 && expr[0] == '"' && expr[^1] == '"')
        {
            return expr.Substring(1, expr.Length - 2);
        }
        // Plain integer.
        if (long.TryParse(expr, out long l))
        {
            return StarlarkInt.Of(l);
        }
        throw new InvalidOperationException(
            string.Format("unsupported default value expression for parameter {0}: {1}", name, expr));
    }
}
