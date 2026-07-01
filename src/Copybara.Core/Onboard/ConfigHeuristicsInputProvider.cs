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

using Copybara.ConfigGen;
using Copybara.Exceptions;
using Copybara.Git;
using Copybara.Onboard.Core;
using Copybara.Util;

using Console = Copybara.Util.Console.Console;

namespace Copybara.Onboard;

/// <summary>
/// An input provider that uses the origin and destination content information to infer several
/// fields like the origin_files glob. Port of
/// <c>com.google.copybara.onboard.ConfigHeuristicsInputProvider</c>.
///
/// <para><b>Port note:</b> depends on <c>Copybara.ConfigGen.ConfigGenHeuristics</c> (ported by a
/// peer). Java's <c>java.net.URL</c> maps to <see cref="System.Uri"/> and <c>java.nio.file.Path</c>
/// to <c>string</c>.</para>
/// </summary>
public class ConfigHeuristicsInputProvider : IInputProvider
{
    private static readonly Glob IncludeExcludeNoop =
        Glob.CreateGlob(ImmutableArray.Create("**"), ImmutableArray.Create("**"));

    // Optional sentinel: null means "not computed yet"; a null-valued result means "no result".
    private bool _computed;
    private ConfigGenHeuristics.Result? _cached;

    private readonly GitOptions _gitOptions;
    private readonly GeneralOptions _generalOptions;
    private readonly GeneratorOptions _generatorOptions;
    private readonly ImmutableHashSet<string> _destinationOnlyPaths;
    private readonly int _percentSimilar;
    private readonly Console _console;
    private readonly DestinationPathProvider _destinationPathProvider;

    public ConfigHeuristicsInputProvider(
        GitOptions gitOptions,
        GeneralOptions generalOptions,
        GeneratorOptions generatorOptions,
        ImmutableHashSet<string> destinationOnlyPaths,
        int percentSimilar,
        Console console,
        DestinationPathProvider destinationPathProvider)
    {
        _gitOptions = gitOptions;
        _generalOptions = generalOptions;
        _generatorOptions = generatorOptions;
        _destinationOnlyPaths = destinationOnlyPaths;
        _percentSimilar = percentSimilar;
        _console = console;
        _destinationPathProvider = destinationPathProvider;
    }

    public T? Resolve<T>(Input<T> input, IInputProviderResolver db)
        where T : class
    {
        Uri originUrl = db.Resolve(Inputs.GitOriginUrl);
        string currentVersion = db.Resolve(Inputs.CurrentVersion);
        string destination = _destinationPathProvider(db);
        ConfigGenHeuristics.Result? result = ComputeHeuristic(originUrl, currentVersion, destination);
        if (result == null)
        {
            return null;
        }

        if (ReferenceEquals(input, Inputs.OriginGlob))
        {
            Glob resultGlob = result.GetOriginGlob();
            return resultGlob.Equals(IncludeExcludeNoop)
                ? null
                : (T?)(object)Inputs.OriginGlob.AsValue(resultGlob);
        }

        if (ReferenceEquals(input, Inputs.Transformations))
        {
            ConfigGenHeuristics.GeneratorTransformations transformations = result.GetTransformations();
            return (T?)(object)Inputs.Transformations.AsValue(transformations);
        }

        if (ReferenceEquals(input, Inputs.DestinationExcludePaths))
        {
            ConfigGenHeuristics.DestinationExcludePaths destinationExcludePaths =
                result.GetDestinationExcludePaths();
            if (_generatorOptions.OptimizeGlobs)
            {
                // Filter out paths containing "AUTOPATCHES" unconditionally here. This optimizes the
                // generated globs by excluding autopatch paths, which are intended to be managed by
                // Copybara rather than explicitly listed as excludes.
                ImmutableHashSet<string> filtered =
                    destinationExcludePaths.GetPaths()
                        .Where(p => !p.Contains("AUTOPATCHES"))
                        .ToImmutableHashSet();
                destinationExcludePaths = new ConfigGenHeuristics.DestinationExcludePaths(filtered);
            }

            return (T?)(object)Inputs.DestinationExcludePaths.AsValue(destinationExcludePaths);
        }

        return null;
    }

    protected ConfigGenHeuristics.Result? ComputeHeuristic(
        Uri originUrl, string currentVersion, string destination)
    {
        if (!Directory.Exists(destination))
        {
            return null;
        }

        if (_computed)
        {
            return _cached;
        }

        try
        {
            // TODO(malcon): Refactor this class to not depend on git. IOW, be able to generate
            // configs for existing sources for non-git repositories.
            string origin = _generalOptions.GetDirFactory().NewTempDir("checkout");
            GitRepository repo =
                _gitOptions.CachedBareRepoForUrl(originUrl.ToString()).WithWorkTree(origin);

            var selector = new FuzzyClosestVersionSelector();
            currentVersion =
                selector.SelectVersion(currentVersion, repo, originUrl.ToString(), _console);

            _console.ProgressFmt("Fetching '%s' from %s", currentVersion, originUrl.ToString());
            GitRevision gitRevision;
            try
            {
                gitRevision =
                    repo.FetchSingleRefWithTags(
                        originUrl.ToString(),
                        currentVersion,
                        fetchTags: true,
                        partialFetch: false,
                        null);
            }
            catch (RepoException)
            {
                gitRevision =
                    repo.FetchSingleRef(
                        originUrl.ToString(), currentVersion, partialFetch: false, null);
            }

            Directory.CreateDirectory(origin);
            string git = origin;
            ImmutableArray<string> upstreamTags =
                repo.ShowRef().Keys
                    .Where(r => r.StartsWith("refs/tags/", StringComparison.Ordinal))
                    .ToImmutableArray();

            _console.ProgressFmt("Checking out git files");
            repo.WithWorkTree(git).ForceCheckout(gitRevision.GetHash());

            ConfigGenHeuristics heuristics =
                GetConfigGenHeuristics(
                    destination,
                    origin,
                    _destinationOnlyPaths,
                    _percentSimilar,
                    _generatorOptions,
                    _generalOptions,
                    upstreamTags);

            _console.ProgressFmt("Computing globs");
            _cached = heuristics.Run();
            _computed = true;
            return _cached;
        }
        catch (Exception e) when (e is ValidationException or IOException or RepoException)
        {
            // Cannot compute heuristics for this repository.
            _cached = null;
            _computed = true;
            return _cached;
        }
    }

    /// <summary>Returns a <see cref="ConfigGenHeuristics"/> object.</summary>
    protected ConfigGenHeuristics GetConfigGenHeuristics(
        string destination,
        string origin,
        ImmutableHashSet<string> destinationOnlyPaths,
        int percentSimilar,
        GeneratorOptions generatorOptions,
        GeneralOptions generalOptions,
        ImmutableArray<string> versions) =>
        new(
            origin,
            destination,
            destinationOnlyPaths,
            percentSimilar,
            generatorOptions.ComputeGlobIgnoreCarriageReturn,
            generatorOptions.ComputeGlobIgnoreWhitespace,
            generalOptions,
            versions);

    public IReadOnlyDictionary<IInput, int> Provides() =>
        ((IInputProvider)this).DefaultPriorityMap(
            new IInput[]
            {
                Inputs.OriginGlob, Inputs.Transformations, Inputs.DestinationExcludePaths,
            });

    /// <summary>
    /// Resolves a destination path for glob generation heuristics. This allows the destination path
    /// to be different than the generator output folder, if needed. Port of the functional interface
    /// <c>DestinationPathProvider</c>.
    /// </summary>
    /// <exception cref="System.Threading.ThreadInterruptedException"/>
    /// <exception cref="CannotProvideException"/>
    public delegate string DestinationPathProvider(IInputProviderResolver db);
}
