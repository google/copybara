/*
 * Copyright (C) 2020 Google Inc.
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

using System.Collections.Immutable;
using System.Reflection;
using static Copybara.Doc.DocBase;

namespace Copybara.Doc;

/// <summary>
/// Generates a Markdown document with the Copybara reference guide. Port of
/// <c>com.google.copybara.doc.Generator</c>.
///
/// <para><b>Port note:</b> Java's <c>Generator.main</c> took a comma-separated list of jar paths,
/// read a <c>starlark_class_list.txt</c> out of each jar (emitted by the build-time
/// <c>AnnotationProcessor</c>), loaded those classes and reflected over them. The .NET port has no
/// annotation processor, so this generator reflects directly over the module types found in the
/// supplied assemblies (see <see cref="ModuleLoader"/>). The template-substitution behavior is
/// preserved.</para>
/// </summary>
public static class Generator
{
    private const string TemplateReplacement = "<!-- Generated reference here -->";

    /// <summary>
    /// Renders the reference Markdown for the given module <paramref name="assemblies"/> and returns
    /// it, optionally substituted into <paramref name="template"/>.
    /// </summary>
    public static string Generate(
        IEnumerable<Assembly> assemblies,
        IEnumerable<Type>? additionalTypes = null,
        string? template = null,
        bool includeFlagAggregate = true)
    {
        ImmutableArray<DocModule> modules = new ModuleLoader().Load(assemblies, additionalTypes);
        return Render(modules, template, includeFlagAggregate);
    }

    /// <summary>
    /// Renders the reference Markdown from an explicit list of module types. Useful for tests and for
    /// callers that already resolved the type list.
    /// </summary>
    public static string GenerateFromTypes(
        IEnumerable<Type> types, string? template = null, bool includeFlagAggregate = true)
    {
        ImmutableArray<DocModule> modules = new ModuleLoader().LoadTypes(types);
        return Render(modules, template, includeFlagAggregate);
    }

    private static string Render(
        ImmutableArray<DocModule> modules, string? template, bool includeFlagAggregate)
    {
        string markdown = new MarkdownRenderer().Render(modules, includeFlagAggregate);
        string effectiveTemplate = template ?? TemplateReplacement;
        return effectiveTemplate.Replace(
            TemplateReplacement, TemplateReplacement + "\n" + markdown);
    }

    /// <summary>
    /// Writes the reference Markdown for the given assemblies to <paramref name="outputFile"/>,
    /// substituting into <paramref name="templateFile"/> when provided. Mirrors Java's
    /// <c>Generator.main</c> file-writing behavior.
    /// </summary>
    public static void Write(
        IEnumerable<Assembly> assemblies,
        string outputFile,
        IEnumerable<Type>? additionalTypes = null,
        string? templateFile = null,
        bool includeFlagAggregate = true)
    {
        string? template = templateFile != null ? File.ReadAllText(templateFile) : null;
        string output = Generate(assemblies, additionalTypes, template, includeFlagAggregate);
        File.WriteAllText(outputFile, output + Environment.NewLine);
    }
}
