/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

namespace Copybara.Doc.Annotations;

/// <summary>
/// Documentation for elements of Copybara configuration, like Origins, Destinations, etc. Port of
/// <c>com.google.copybara.doc.annotations.DocElement</c>.
/// </summary>
[AttributeUsage(AttributeTargets.Class | AttributeTargets.Interface, Inherited = false)]
public sealed class DocElementAttribute : Attribute
{
    /// <summary>Text explaining what the element does and how to use it.</summary>
    public string Description { get; set; } = "";

    /// <summary>Kind of the element, can be Origin, Destination, etc.</summary>
    public Type ElementKind { get; set; } = typeof(object);

    /// <summary>Associated flag classes annotated with <see cref="FlagAttribute"/>.</summary>
    public Type[] Flags { get; set; } = Array.Empty<Type>();
}

/// <summary>
/// A field documentation for a <see cref="DocElementAttribute"/> type (in practice, used on enum
/// members). Port of <c>com.google.copybara.doc.annotations.DocField</c>.
/// </summary>
[AttributeUsage(AttributeTargets.Method | AttributeTargets.Field | AttributeTargets.Property)]
public sealed class DocFieldAttribute : Attribute
{
    public DocFieldAttribute(string description) => Description = description;

    public string Description { get; }

    public bool Required { get; set; } = true;

    public string DefaultValue { get; set; } = "none";

    public bool Undocumented { get; set; }

    public bool Deprecated { get; set; }

    /// <summary>
    /// Use when the elements of a list field are always of the same type so that we can avoid
    /// using <c>!FieldClass</c>.
    /// </summary>
    public Type ListType { get; set; } = typeof(object);
}

/// <summary>
/// Annotation for documenting fields for a <c>[Param]</c> or return types. Repeatable in the .NET
/// port via <see cref="AttributeUsageAttribute.AllowMultiple"/>. Port of
/// <c>com.google.copybara.doc.annotations.DocDefault</c> (its container
/// <c>DocDefaults</c> is folded into the repeatable usage here).
/// </summary>
[AttributeUsage(AttributeTargets.Method | AttributeTargets.Property, AllowMultiple = true)]
public sealed class DocDefaultAttribute : Attribute
{
    public DocDefaultAttribute(string field) => Field = field;

    public string Field { get; }

    /// <summary>The documented default value (Java's confusingly named <c>value()</c>).</summary>
    public string Value { get; set; } = "";

    public string[] AllowedTypes { get; set; } = Array.Empty<string>();
}

/// <summary>
/// Adds a custom prefix to the signature example and reference in the generated Markdown. Port of
/// <c>com.google.copybara.doc.annotations.DocSignaturePrefix</c>.
/// </summary>
[AttributeUsage(AttributeTargets.Class | AttributeTargets.Interface, Inherited = false)]
public sealed class DocSignaturePrefixAttribute : Attribute
{
    public DocSignaturePrefixAttribute(string value) => Value = value;

    /// <summary>
    /// When generating documentation, use <c>varPrefix + "." + method/field</c>. For example
    /// <c>ctx.origin</c>.
    /// </summary>
    public string Value { get; }
}

/// <summary>
/// Associates an example snippet with a configuration element. Repeatable in the .NET port. Port of
/// <c>com.google.copybara.doc.annotations.Example</c> (its container <c>Examples</c> is folded into
/// the repeatable usage here).
/// </summary>
[AttributeUsage(
    AttributeTargets.Class | AttributeTargets.Interface | AttributeTargets.Field
        | AttributeTargets.Method | AttributeTargets.Property,
    AllowMultiple = true)]
public sealed class ExampleAttribute : Attribute
{
    public ExampleAttribute(string title, string before, string code)
    {
        Title = title;
        Before = before;
        Code = code;
    }

    /// <summary>Title of the example.</summary>
    public string Title { get; }

    /// <summary>Description shown before the snippet.</summary>
    public string Before { get; }

    /// <summary>The code of the snippet, e.g. <c>core.move('', 'foo')</c>.</summary>
    public string Code { get; }

    /// <summary>Description shown after the code snippet.</summary>
    public string After { get; set; } = "";

    /// <summary>
    /// If set, the test should check for an existing variable in <see cref="Code"/>. Otherwise it is
    /// assumed to be an expression.
    /// </summary>
    public string TestExistingVariable { get; set; } = "";
}

/// <summary>
/// Marks a class whose <c>[StarlarkMethod]</c>-annotated methods are predeclared in the environment
/// and added to the generated documentation. Port of
/// <c>com.google.copybara.doc.annotations.Library</c>.
/// </summary>
[AttributeUsage(AttributeTargets.Class | AttributeTargets.Interface, Inherited = false)]
public sealed class LibraryAttribute : Attribute
{
}

/// <summary>
/// Associates flags with functions in Starlark modules. Can be set on a whole module or on specific
/// methods. Port of <c>com.google.copybara.doc.annotations.UsesFlags</c>.
/// </summary>
[AttributeUsage(
    AttributeTargets.Class | AttributeTargets.Interface | AttributeTargets.Field
        | AttributeTargets.Method | AttributeTargets.Property)]
public sealed class UsesFlagsAttribute : Attribute
{
    public UsesFlagsAttribute(params Type[] value) => Value = value;

    /// <summary>Associated flag classes annotated with <see cref="FlagAttribute"/>.</summary>
    public Type[] Value { get; }
}
