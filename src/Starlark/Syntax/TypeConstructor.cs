// Copyright 2026 The Bazel Authors. All rights reserved.
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

/// <summary>
/// A factory for creating <see cref="StarlarkType"/>s, parameterized by zero or more type arguments.
///
/// <para>Conceptually, a type constructor corresponds to what the user informally thinks of as "a
/// type": a program symbol, like <c>list</c>, that can appear within a type expression. The usage of
/// a constructor in a type expression yields an actual type, like <c>list[int]</c>. In the case of
/// basic types like <c>None</c> that are not parameterized, there is both a trivial nullary type
/// constructor and an underlying singleton type, where the constructor just wraps the underlying
/// type.</para>
/// </summary>
public interface TypeConstructor
{
    /// <summary>
    /// Returns the result of applying this constructor to the given type arguments.
    /// </summary>
    /// <exception cref="Failure">
    /// if the usage of this constructor is invalid (typically due to a mismatch in the number of
    /// arguments).
    /// </exception>
    StarlarkType CreateStarlarkType(IReadOnlyList<Arg> argsTuple);

    /// <summary>Exception thrown when a <see cref="TypeConstructor"/> is called with invalid arguments.</summary>
    public sealed class Failure : Exception
    {
        public Failure(string message)
            : base(message)
        {
        }
    }

    /// <summary>
    /// An argument to a type constructor's <see cref="CreateStarlarkType"/> method.
    ///
    /// <para>Conceptually, a type argument is the result of evaluating a subexpression of a type
    /// expression. Whereas the overall type expression must yield a <see cref="StarlarkType"/>, a
    /// subexpression can also yield other objects such as an ellipsis or a list of other arguments.
    /// These are needed for type expressions like <c>tuple[Any, ...]</c> and <c>Callable[[int],
    /// bool]</c>.</para>
    /// </summary>
    public interface Arg
    {
        public static readonly EllipsisArg ELLIPSIS = new EllipsisArg();
        public static readonly EmptyTupleArg EMPTY_TUPLE = new EmptyTupleArg();

        /// <summary>An ellipsis type argument, <c>...</c>.</summary>
        public sealed class EllipsisArg : Arg
        {
            internal EllipsisArg()
            {
            }

            public override string ToString() => "...";
        }

        /// <summary>An empty tuple type argument, <c>()</c>.</summary>
        public sealed class EmptyTupleArg : Arg
        {
            internal EmptyTupleArg()
            {
            }

            public override string ToString() => "()";
        }
    }
}
