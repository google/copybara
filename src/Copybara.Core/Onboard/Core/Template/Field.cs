/*
 * Copyright (C) 2021 Google Inc.
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

namespace Copybara.Onboard.Core.Template;

/// <summary>Type of parameter.</summary>
public enum FieldClass
{
    String,
    Int,
    Starlark,
}

/// <summary>Locations for parameters.</summary>
public enum FieldLocation
{
    Named,
    Keyword,
}

/// <summary>
/// An object that describes a Field for a <see cref="TemplateConfigGenerator"/>. Port of
/// <c>com.google.copybara.onboard.core.template.Field</c>. Equality is by name only (mirrors Java).
/// </summary>
public sealed class Field : IEquatable<Field>
{
    private Field(string name, FieldLocation location, bool required)
    {
        Name = name;
        Location = location;
        Required = required;
    }

    public static Field CreateRequired(string name) => new(name, FieldLocation.Named, true);

    public static Field RequiredKeyword(string name) => new(name, FieldLocation.Keyword, true);

    // Matches Java: optional(...) also creates a KEYWORD/required=true field.
    public static Field Optional(string name) => new(name, FieldLocation.Keyword, true);

    public string Name { get; }

    public bool Required { get; }

    public FieldLocation Location { get; }

    public bool Equals(Field? other) =>
        other is not null && string.Equals(Name, other.Name, StringComparison.Ordinal);

    public override bool Equals(object? obj) => obj is Field f && Equals(f);

    public override int GetHashCode() => Name.GetHashCode();
}
