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
/// Describes a parameter of a <see cref="StarlarkMethodAttribute"/>-annotated method. In the .NET
/// port this is applied to the corresponding C# parameter. Port of
/// <c>net.starlark.java.annot.Param</c>.
/// </summary>
[AttributeUsage(AttributeTargets.Parameter, AllowMultiple = false)]
public sealed class ParamAttribute : Attribute
{
    public ParamAttribute() { }

    public ParamAttribute(string name) => Name = name;

    /// <summary>The name of the parameter as seen from Starlark.</summary>
    public string Name { get; set; } = "";

    /// <summary>Documentation for the parameter.</summary>
    public string Doc { get; set; } = "";

    /// <summary>
    /// Default value as a Starlark expression string; empty means the parameter is required.
    /// </summary>
    public string DefaultValue { get; set; } = "";

    /// <summary>Whether the parameter may be specified positionally.</summary>
    public bool Positional { get; set; } = true;

    /// <summary>Whether the parameter may be specified by name.</summary>
    public bool Named { get; set; }

    /// <summary>Whether <c>None</c> is a permissible value.</summary>
    public bool Noneable { get; set; }

    /// <summary>Allowed Starlark types for this parameter (optional documentation/validation).</summary>
    public Type[] AllowedTypes { get; set; } = Array.Empty<Type>();
}

/// <summary>
/// Declares an allowed type for a parameter. Port of <c>net.starlark.java.annot.ParamType</c>.
/// </summary>
[AttributeUsage(AttributeTargets.Parameter, AllowMultiple = true)]
public sealed class ParamTypeAttribute : Attribute
{
    public ParamTypeAttribute(Type type) => Type = type;

    public Type Type { get; }

    /// <summary>Generic element type, when the declared type is a generic collection.</summary>
    public Type? Generic1 { get; set; }
}
