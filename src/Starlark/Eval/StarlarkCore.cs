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

namespace Starlark.Eval;

/// <summary>
/// Marker interface for a value that may be exposed to Starlark. Port of
/// <c>net.starlark.java.eval.StarlarkValue</c>. Members such as <c>Repr</c>, <c>Str</c>,
/// <c>Truth</c>, and <c>IsImmutable</c> are added by the interpreter port (Phase 2); this stable
/// surface exists so domain types can be annotated and compiled ahead of the full interpreter.
/// </summary>
public interface IStarlarkValue
{
    /// <summary>Whether the value is truthy in a boolean context. Defaults to true.</summary>
    bool Truth() => true;

    /// <summary>Whether the value is deeply immutable. Defaults to true.</summary>
    bool IsImmutable() => true;
}

/// <summary>The type of the Starlark <c>None</c> value. Port of <c>net.starlark.java.eval.NoneType</c>.</summary>
public sealed class NoneType : IStarlarkValue
{
    public static readonly NoneType None = new();

    private NoneType() { }

    public bool Truth() => false;

    public override string ToString() => "None";
}

/// <summary>
/// An exception thrown by Starlark evaluation, including by builtin methods invoked from Starlark.
/// Port of <c>net.starlark.java.eval.EvalException</c>.
/// </summary>
public class EvalException : Exception
{
    public EvalException(string message) : base(message) { }

    public EvalException(string message, Exception? cause) : base(message, cause) { }
}

/// <summary>
/// Static entry points and helpers of the Starlark interpreter. Port of
/// <c>net.starlark.java.eval.Starlark</c>. Only the surface used by domain code is defined here;
/// the interpreter port (Phase 2) extends this class with evaluation, calling, and value helpers.
/// </summary>
public static partial class Starlark
{
    /// <summary>The Starlark <c>None</c> value.</summary>
    public static readonly NoneType None = NoneType.None;

    /// <summary>Creates a formatted <see cref="EvalException"/>. Port of <c>Starlark.errorf</c>.</summary>
    public static EvalException Errorf(string format, params object?[] args) =>
        new(args.Length == 0 ? format : string.Format(format, args));
}
