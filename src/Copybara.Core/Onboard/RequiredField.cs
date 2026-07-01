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
/// An object that describes a RequiredField for a <see cref="IConfigTemplate"/>. Port of the
/// AutoValue class <c>com.google.copybara.onboard.RequiredField</c>.
/// </summary>
public sealed class RequiredField
{
    private RequiredField(
        string name,
        ConfigTemplateFieldClass fieldClass,
        ConfigTemplateLocation location,
        string helpText,
        Func<string, bool> predicate,
        ConfigFieldPopulator<string>? populator)
    {
        Name = name;
        FieldClass = fieldClass;
        Location = location;
        HelpText = helpText;
        Predicate = predicate;
        Populator = populator;
    }

    public static RequiredField Create(
        string name,
        ConfigTemplateFieldClass fieldClass,
        ConfigTemplateLocation location,
        string helpText,
        Func<string, bool> predicate) =>
        new(name, fieldClass, location, helpText, predicate, null);

    public static RequiredField CreateWithFieldPopulator(
        string name,
        ConfigTemplateFieldClass fieldClass,
        ConfigTemplateLocation location,
        string helpText,
        Func<string, bool> predicate,
        ConfigFieldPopulator<string> populator) =>
        new(name, fieldClass, location, helpText, predicate, populator);

    public string Name { get; }

    public ConfigTemplateFieldClass FieldClass { get; }

    public ConfigTemplateLocation Location { get; }

    public string HelpText { get; }

    public Func<string, bool> Predicate { get; }

    public ConfigFieldPopulator<string>? Populator { get; }
}
