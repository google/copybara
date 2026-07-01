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

namespace Copybara.Onboard;

/// <summary>
/// An object that describes an OptionalField for a <see cref="IConfigTemplate"/>. Port of the
/// AutoValue class <c>com.google.copybara.onboard.OptionalField</c>.
/// </summary>
public sealed class OptionalField
{
    private OptionalField(
        string name,
        ConfigTemplateFieldClass fieldClass,
        ConfigTemplateLocation location,
        string helpText,
        Func<string, bool> predicate,
        string defaultValue)
    {
        Name = name;
        FieldClass = fieldClass;
        Location = location;
        HelpText = helpText;
        Predicate = predicate;
        DefaultValue = defaultValue;
    }

    public static OptionalField Create(
        string name,
        ConfigTemplateFieldClass fieldClass,
        ConfigTemplateLocation location,
        string helpText,
        Func<string, bool> predicate,
        string defaultValue) =>
        new(name, fieldClass, location, helpText, predicate, defaultValue);

    public string Name { get; }

    public ConfigTemplateFieldClass FieldClass { get; }

    public ConfigTemplateLocation Location { get; }

    public string HelpText { get; }

    public Func<string, bool> Predicate { get; }

    public string DefaultValue { get; }
}
