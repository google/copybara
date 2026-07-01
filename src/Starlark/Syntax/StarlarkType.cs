// Copyright 2025 The Bazel Authors. All rights reserved.
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

using System.Linq;

namespace Starlark.Syntax;

/// <summary>
/// Base class for all Starlark types.
///
/// <para>Starlark typing is an experimental feature under development. See the tracking issue:
/// https://github.com/bazelbuild/bazel/issues/27370</para>
/// </summary>
public abstract class StarlarkType : TypeConstructor.Arg
{
    /// <summary>
    /// Returns the list of supertypes of this type.
    ///
    /// <para>Preferred order is from the most specific to the least specific supertype. But if that
    /// is not possible, the order can be arbitrary.</para>
    /// </summary>
    public virtual IReadOnlyList<StarlarkType> GetSupertypes()
    {
        return System.Array.Empty<StarlarkType>();
    }

    /// <summary>
    /// If this type has a field by the given name, returns the type of that field, or null otherwise.
    /// </summary>
    public virtual StarlarkType? GetField(string name, TypeContext context)
    {
        return null;
    }

    /// <summary>
    /// Returns whether a value of type <paramref name="t2"/> can be assigned to a value of type
    /// <paramref name="t1"/>.
    ///
    /// <para>In gradual typing terms, <paramref name="t2"/> must be a "consistent subtype of"
    /// <paramref name="t1"/>. This means that there is a way to substitute zero or more occurrences
    /// of <c>Any</c> in both terms, such that <paramref name="t2"/> becomes a subtype of
    /// <paramref name="t1"/> in the ordinary sense.</para>
    /// </summary>
    public static bool AssignableFrom(StarlarkType t1, StarlarkType t2)
    {
        if (t1.Equals(Types.ANY) || t2.Equals(Types.ANY))
        {
            return true;
        }
        if (t1.Equals(Types.OBJECT))
        {
            return true;
        }
        if (t1.Equals(t2))
        {
            return true;
        }
        if (t2 is Types.UnionType union2)
        {
            return union2.GetTypes().All(sub2 => AssignableFrom(t1, sub2));
        }
        if (t1 is Types.UnionType union1)
        {
            return union1.GetTypes().Any(sub1 => AssignableFrom(sub1, t2));
        }
        if (t2.GetSupertypes().Any(super2 => AssignableFrom(t1, super2)))
        {
            return true;
        }
        return false;
    }

    /// <summary>
    /// Infers the return type of a binary operation having an operand of this type. Intended for use
    /// by <see cref="TypeChecker"/>.
    ///
    /// <para>Returns the inferred type of the operation, or <c>null</c> to indicate that we could not
    /// infer a return type, in which case the caller would fall back to calling
    /// <c>InferBinaryOperator</c> on the other operand's type, or to special-case handling for
    /// certain operators on certain built-in types (e.g. tuple multiplication).</para>
    /// </summary>
    internal virtual StarlarkType? InferBinaryOperator(TokenKind op, StarlarkType that, bool thisLeft)
    {
        return null;
    }

    /// <summary>
    /// Returns true iff the values of the two arbitrary (possibly union) types can be ordering
    /// compared.
    /// </summary>
    public static bool Comparable(StarlarkType x, StarlarkType y)
    {
        return x.IsComparable(y) || y.IsComparable(x);
    }

    /// <summary>
    /// Returns true if this type's values can be ordering compared with values of another type. A
    /// return value of false is ambiguous on its own; two types are considered incomparable iff both
    /// <c>x.IsComparable(y)</c> and <c>y.IsComparable(x)</c> are false.
    ///
    /// <para>Do not call this method directly; instead, use <see cref="Comparable"/>.</para>
    /// </summary>
    protected internal virtual bool IsComparable(StarlarkType that)
    {
        return false;
    }

    /// <summary>
    /// Returns true if an index expression on a value of this type can be used as the LHS of an
    /// assignment.
    /// </summary>
    public virtual bool HasSetIndex()
    {
        return false;
    }

    /// <summary>
    /// Returns true if a dot expression on a value of this type can be used as the LHS of an
    /// assignment.
    /// </summary>
    public virtual bool HasSetField()
    {
        return false;
    }
}
