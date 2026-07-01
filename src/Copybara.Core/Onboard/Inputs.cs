/*
 * Copyright (C) 2022 Google Inc.
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

using Copybara.Authoring;
using Copybara.ConfigGen;
using Copybara.Common;
using Copybara.Onboard.Core;
using Copybara.Onboard.Core.Template;
using Copybara.Util;

namespace Copybara.Onboard;

/// <summary>
/// Standard <see cref="Input{T}"/>s that can be used by config generators. Port of
/// <c>com.google.copybara.onboard.Inputs</c>.
///
/// <para><b>Port note:</b> Java uses <c>java.net.URL</c> and <c>java.nio.file.Path</c>. This port
/// uses <see cref="System.Uri"/> and <c>string</c> paths respectively. The heuristics-derived types
/// (<c>GeneratorTransformations</c>, <c>DestinationExcludePaths</c>) live in
/// <c>Copybara.ConfigGen.ConfigGenHeuristics</c> (ported by a peer).</para>
/// </summary>
public static class Inputs
{
    private static readonly IConverter<Uri> UrlConverter = new UriConverter();

    public static readonly Input<Uri> GitOriginUrl = Input<Uri>.Create(
        "git_origin_url", "Git URL to serve as origin repository", null, UrlConverter);

    public static readonly Input<string> GitOriginRef = Input<string>.Create(
        "git_origin_ref",
        "Git branch name or ref to migrate from the origin repository. If not defined, defaults"
            + " to the default branch (e.g. main)",
        null,
        new IdentityConverter());

    public static readonly Input<string> CurrentVersion = Input<string>.Create(
        "current_version",
        "Current imported version or version wanted",
        null,
        new IdentityConverter());

    public static readonly Input<Uri> GitDestinationUrl = Input<Uri>.Create(
        "git_destination_url", "Git URL to serve as origin repository", null, UrlConverter);

    /// <summary>Should be accessed as optional. As it can only be inferred.</summary>
    // TODO(port): depends on Copybara.ConfigGen.ConfigGenHeuristics.GeneratorTransformations.
    public static readonly Input<ConfigGenHeuristics.GeneratorTransformations> Transformations =
        Input<ConfigGenHeuristics.GeneratorTransformations>.CreateInfer(
            "transformations", "`core.move`s and other transformations", null);

    // TODO(port): depends on Copybara.ConfigGen.ConfigGenHeuristics.DestinationExcludePaths.
    public static readonly Input<ConfigGenHeuristics.DestinationExcludePaths> DestinationExcludePaths =
        Input<ConfigGenHeuristics.DestinationExcludePaths>.Create(
            "destination_exclude_paths",
            "automatically detected destination-only paths",
            null,
            new DestinationExcludePathsConverter());

    public static readonly Input<BoxedBool> NewPackage = Input<BoxedBool>.CreateInfer(
        "new_package", "Whether or not this package already exists in third_party", null);

    public static readonly Input<Glob> OriginGlob = Input<Glob>.Create(
        "origin_glob",
        "Glob of files to be migrated from the origin",
        Glob.AllFiles,
        new OriginGlobConverter());

    public static readonly Input<Author> DefaultAuthor = Input<Author>.Create(
        "default_author", "Default author for changes", null, new DefaultAuthorConverter());

    public static readonly Input<GeneratorFolder> GeneratorFolder = Input<GeneratorFolder>.Create(
        "generator_folder", "The folder where the assets will be created",
        null, new GeneratorFolderConverter());

    public static readonly Input<string> MigrationName = Input<string>.Create(
        "migration_name", "Migration name", null, new IdentityConverter());

    public static readonly Input<string> PackageName = Input<string>.Create(
        "package_name", "The name of the package to import", null, new IdentityConverter());

    public static readonly Input<string> PackageDescription = Input<string>.Create(
        "package_description", "The description of the package to import", null, new IdentityConverter());

    private static Input<IConfigGenerator>? _template;

    public static Input<IConfigGenerator> TemplateInput() =>
        Preconditions.CheckNotNull(_template, "Template input has to be set before call");

    public static void MaybeSetTemplates(IReadOnlyList<IConfigGenerator> values)
    {
        if (_template != null)
        {
            return;
        }

        var templates = values.ToImmutableDictionary(v => v.Name, v => v);
        _template = Input<IConfigGenerator>.Create(
            "template_name",
            "Template to use for generating the config",
            null,
            new TemplateConverter(templates));
    }

    // ------------------------------------------------------------------
    // Converters (Java used inline lambdas / anonymous classes).
    // ------------------------------------------------------------------

    private sealed class IdentityConverter : IConverter<string>
    {
        public string Convert(string value, IInputProviderResolver resolver) => value;
    }

    private sealed class UriConverter : IConverter<Uri>
    {
        public Uri Convert(string value, IInputProviderResolver resolver)
        {
            try
            {
                return new Uri(value);
            }
            catch (UriFormatException e)
            {
                throw new CannotConvertException("Invalid url " + value + ": " + e);
            }
        }
    }

    private sealed class DestinationExcludePathsConverter
        : IConverter<ConfigGenHeuristics.DestinationExcludePaths>
    {
        public ConfigGenHeuristics.DestinationExcludePaths Convert(
            string value, IInputProviderResolver resolver)
        {
            var pathStrings = value.Split(',');
            return new ConfigGenHeuristics.DestinationExcludePaths(
                pathStrings.ToImmutableHashSet());
        }
    }

    private sealed class OriginGlobConverter : IConverter<Glob>
    {
        public Glob Convert(string value, IInputProviderResolver resolver)
        {
            try
            {
                return resolver.ParseStarlark<Glob>(value);
            }
            catch (CannotConvertException e)
            {
                throw new CannotConvertException(
                    string.Format(
                        "Invalid value '{0}'for a glob. Use a value like '{1}'. Error: {2}",
                        value, Glob.AllFiles, e.Message));
            }
        }
    }

    private sealed class DefaultAuthorConverter : IConverter<Author>
    {
        public Author Convert(string value, IInputProviderResolver resolver)
        {
            try
            {
                return AuthorParser.Parse(value);
            }
            catch (InvalidAuthorException e)
            {
                throw new CannotConvertException(
                    "Invalid author. Format \"foo <foo@example.com>\": " + e.Message);
            }
        }
    }

    private sealed class GeneratorFolderConverter : IConverter<GeneratorFolder>
    {
        public GeneratorFolder Convert(string value, IInputProviderResolver resolver) =>
            new(value);
    }

    private sealed class TemplateConverter : IConverter<IConfigGenerator>
    {
        private readonly ImmutableDictionary<string, IConfigGenerator> _templates;

        public TemplateConverter(ImmutableDictionary<string, IConfigGenerator> templates)
        {
            _templates = templates;
        }

        public IConfigGenerator Convert(string value, IInputProviderResolver resolver)
        {
            if (_templates.TryGetValue(value, out IConfigGenerator? configGenerator))
            {
                return configGenerator;
            }

            throw new CannotConvertException(
                string.Format(
                    "Invalid template '{0}'. Available templates: {1}",
                    value, string.Join(", ", _templates.Keys)));
        }
    }
}

/// <summary>
/// Reference-type wrapper around a folder path. <see cref="Input{T}"/> requires <c>T : class</c>;
/// Java used <c>java.nio.file.Path</c> (a reference type) while this port uses <c>string</c> paths,
/// so a small boxing type is needed to satisfy the generic constraint.
/// </summary>
public sealed class GeneratorFolder
{
    public GeneratorFolder(string path) => Path = path;

    public string Path { get; }

    public override string ToString() => Path;
}

/// <summary>
/// Reference-type wrapper around a <c>bool</c> so it can be used as an <see cref="Input{T}"/> value
/// (which requires <c>T : class</c>). Mirrors Java's <c>Input&lt;Boolean&gt;</c>.
/// </summary>
public sealed class BoxedBool
{
    public BoxedBool(bool value) => Value = value;

    public bool Value { get; }

    public override string ToString() => Value ? "True" : "False";
}
