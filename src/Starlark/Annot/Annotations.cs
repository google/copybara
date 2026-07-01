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

namespace Starlark.Annot;

/// <summary>
/// Marks a class or interface that represents a Starlark data type. Port of
/// <c>net.starlark.java.annot.StarlarkBuiltin</c>.
/// </summary>
[AttributeUsage(AttributeTargets.Class | AttributeTargets.Interface, Inherited = false)]
public sealed class StarlarkBuiltinAttribute : Attribute
{
    public StarlarkBuiltinAttribute(string name) => Name = name;

    /// <summary>The name of this data type, as returned by <c>type(x)</c>.</summary>
    public string Name { get; }

    /// <summary>Module documentation in HTML.</summary>
    public string Doc { get; set; } = "";

    /// <summary>Whether the module should appear in the documentation.</summary>
    public bool Documented { get; set; } = true;

    /// <summary>Documentation category.</summary>
    public string Category { get; set; } = "";
}

/// <summary>
/// Annotates a method (or property used as a struct field) callable from Starlark. Port of
/// <c>net.starlark.java.annot.StarlarkMethod</c>.
///
/// <para>Convention for the .NET port: apply <see cref="StarlarkMethodAttribute"/> to the method,
/// and describe each argument with a <see cref="ParamAttribute"/> on the corresponding C#
/// parameter (rather than Java's nested <c>parameters = {..}</c> array). Special interpreter-supplied
/// parameters (StarlarkThread, StarlarkSemantics) are matched by C# parameter type.</para>
/// </summary>
[AttributeUsage(AttributeTargets.Method | AttributeTargets.Property, Inherited = true)]
public sealed class StarlarkMethodAttribute : Attribute
{
    public StarlarkMethodAttribute(string name) => Name = name;

    /// <summary>Name of the method, as exposed to Starlark.</summary>
    public string Name { get; }

    /// <summary>Documentation text (may contain HTML).</summary>
    public string Doc { get; set; } = "";

    /// <summary>If true, the function appears in the Starlark documentation.</summary>
    public bool Documented { get; set; } = true;

    /// <summary>If true, this member is accessed as a field (<c>bar.foo</c> not <c>bar.foo()</c>).</summary>
    public bool StructField { get; set; }

    /// <summary>If true, the enclosing value is callable and this method handles the call.</summary>
    public bool SelfCall { get; set; }

    /// <summary>Permits a null result, which is converted to <c>None</c>.</summary>
    public bool AllowReturnNones { get; set; }

    /// <summary>If true, the StarlarkThread is passed to the method.</summary>
    public bool UseStarlarkThread { get; set; }

    /// <summary>If true, the StarlarkSemantics is passed to the method.</summary>
    public bool UseStarlarkSemantics { get; set; }

    /// <summary>Whether this method can act as a type in a type expression.</summary>
    public bool TrustReturnsValid { get; set; }
}
