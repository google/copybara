// Copyright 2015 The Bazel Authors. All rights reserved.
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

using Starlark.Syntax;

namespace Starlark.Eval;

/// <summary>
/// The printing surface of <c>net.starlark.java.eval.StarlarkValue</c> (<c>repr</c>, <c>str</c>,
/// <c>debugPrint</c>). The base marker <see cref="IStarlarkValue"/> (with <c>Truth</c>/
/// <c>IsImmutable</c>) lives in StarlarkCore.cs; this companion interface adds the interpreter
/// printing hooks so value types can be rendered by <see cref="Printer"/>.
/// </summary>
public interface IStarlarkPrintableValue : IStarlarkValue
{
    /// <summary>Prints an official (parseable) representation of this value.</summary>
    void Repr(Printer printer, StarlarkSemantics semantics) =>
        printer.Append("<unknown object " + GetType().Name + ">");

    /// <summary>Prints an informal, human-readable representation of this value.</summary>
    void Str(Printer printer, StarlarkSemantics semantics) => Repr(printer, semantics);

    /// <summary>Prints an informal debug representation of this value.</summary>
    void DebugPrint(Printer printer, StarlarkThread thread) => Str(printer, thread.GetSemantics());

    /// <summary>Returns normally if the value is hashable and thus suitable as a dict key.</summary>
    void CheckHashable()
    {
        if (!IsImmutable())
        {
            throw Starlark.Errorf("unhashable type: '{0}'", Starlark.Type(this));
        }
    }
}

/// <summary>
/// A StarlarkIterable value may be iterated by Starlark constructs such as for-loops and
/// comprehensions. Port of <c>net.starlark.java.eval.StarlarkIterable</c>.
/// </summary>
public interface IStarlarkIterable<out T> : IStarlarkValue, IEnumerable<T> { }

/// <summary>
/// A Starlark value that supports membership tests (<c>key in object</c>). Port of
/// <c>net.starlark.java.eval.StarlarkMembershipTestable</c>.
/// </summary>
public interface IStarlarkMembershipTestable : IStarlarkValue
{
    bool ContainsKey(StarlarkSemantics semantics, object key);
}

/// <summary>
/// A Starlark value that supports indexed access (<c>object[key]</c>). Port of
/// <c>net.starlark.java.eval.StarlarkIndexable</c>.
/// </summary>
public interface IStarlarkIndexable : IStarlarkMembershipTestable
{
    object? GetIndex(StarlarkSemantics semantics, object key);

    /// <summary>Variant providing a StarlarkThread on method calls.</summary>
    public interface IThreaded
    {
        object? GetIndex(StarlarkThread thread, StarlarkSemantics semantics, object key);

        bool ContainsKey(StarlarkThread thread, StarlarkSemantics semantics, object key);
    }
}

/// <summary>
/// A Starlark value that supports binary operators such as <c>x + y</c>. Port of
/// <c>net.starlark.java.eval.HasBinary</c>. The token kind is represented as a
/// <see cref="TokenKind"/> from the syntax package.
/// </summary>
public interface IHasBinary : IStarlarkValue
{
    /// <summary>
    /// Returns <c>this op that</c> if <paramref name="thisLeft"/>, else <c>that op this</c>. Returns
    /// null if the operation is not supported.
    /// </summary>
    object? BinaryOp(TokenKind op, object that, bool thisLeft);
}

/// <summary>
/// A Starlark value with fields accessed using <c>x.field</c>. Port of
/// <c>net.starlark.java.eval.Structure</c>.
/// </summary>
public interface IStructure : IStarlarkValue
{
    /// <summary>Returns the value of the named field, or null if the field does not exist.</summary>
    object? GetValue(string name);

    /// <summary>Returns the value of the named field, with access to the semantics.</summary>
    object? GetValue(StarlarkSemantics semantics, string name) => GetValue(name);

    /// <summary>Returns the names of this value's fields, in some stable order.</summary>
    IReadOnlyCollection<string> GetFieldNames();

    /// <summary>Returns the error message for an attempt to access an undefined field, or null.</summary>
    string? GetErrorMessageForUnknownField(string field);

    /// <summary>Updates the named field of this value as if by <c>this.field = value</c>.</summary>
    void SetField(string field, object value) =>
        throw Starlark.Errorf("{0} value does not support field assignment", Starlark.Type(this));
}
