/*
 * Copyright (C) 2018 Google Inc.
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
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Util;
using static Copybara.Hg.HgRepository;
using AuthoringModel = Copybara.Authoring.Authoring;

namespace Copybara.Hg;

/// <summary>A class for manipulating Hg repositories.</summary>
public class HgOrigin : IOrigin<HgRevision>
{
    public static readonly Regex CompleteSha1Pattern = new("^[a-f0-9]{40}$", RegexOptions.Compiled);

    private readonly GeneralOptions _generalOptions;
    private readonly HgOptions _hgOptions;
    private readonly string _repoUrl;
    private readonly string? _configRef;
    private readonly HgOriginOptions _hgOriginOptions;

    private HgOrigin(
        GeneralOptions generalOptions,
        HgOptions hgOptions,
        string repoUrl,
        string? @ref,
        HgOriginOptions hgOriginOptions)
    {
        _generalOptions = generalOptions;
        _hgOptions = hgOptions;
        _repoUrl = Preconditions.CheckNotNull(repoUrl).TrimEnd('/');
        _configRef = @ref;
        _hgOriginOptions = hgOriginOptions;
    }

    public HgRepository GetRepository() => _hgOptions.CachedBareRepoForUrl(_repoUrl);

    /// <summary>Resolves a hg changeset reference to a revision. Pulls revision into repo.</summary>
    public HgRevision Resolve(string? reference)
    {
        HgRepository repo = GetRepository();
        string? @ref = reference;
        if (string.IsNullOrEmpty(@ref))
        {
            if (string.IsNullOrEmpty(_configRef))
            {
                throw new CannotResolveRevisionException(
                    "No source reference was passed through the command line and the default"
                        + " reference is empty");
            }

            @ref = _configRef;
        }

        // Avoid fetch if a SHA-1 is passed and we already have in our local repo.
        if (CompleteSha1Pattern.IsMatch(@ref))
        {
            try
            {
                return repo.Identify(@ref);
            }
            catch (CannotResolveRevisionException)
            {
                // Not present locally. Pull from remote instead.
            }
        }

        // Fetch all instead of a specific ref:
        //  - It is usually faster
        //  - fetching a specific ref creates a local tag 'tip' instead of the original name. Then
        //  the next hg identify call needs to be aware of using tip (but not always, for example
        //  if user tries to resolve '0'
        repo.PullFromRef(_repoUrl, @ref: null);
        return repo.Identify(@ref);
    }

    internal sealed class ReaderImpl : IOrigin<HgRevision>.IReader<HgRevision>
    {
        private readonly string _repoUrl;
        private readonly HgOptions _hgOptions;
        private readonly AuthoringModel _authoring;
        private readonly GeneralOptions _generalOptions;
        private readonly HgOriginOptions _hgOriginOptions;
        internal readonly Glob OriginFiles;

        internal ReaderImpl(
            string repoUrl,
            HgOptions hgOptions,
            AuthoringModel authoring,
            GeneralOptions generalOptions,
            HgOriginOptions hgOriginOptions,
            Glob originFiles)
        {
            _repoUrl = Preconditions.CheckNotNull(repoUrl);
            _hgOptions = hgOptions;
            _authoring = authoring;
            _generalOptions = generalOptions;
            _hgOriginOptions = hgOriginOptions;
            OriginFiles = originFiles;
        }

        private ChangeReader.Builder ChangeReaderBuilder() =>
            ChangeReader.Builder.ForOrigin(GetRepository(), _authoring, _generalOptions.GetConsole());

        internal HgRepository GetRepository() => _hgOptions.CachedBareRepoForUrl(_repoUrl);

        public void Checkout(HgRevision revision, string workDir)
        {
            HgRepository repo = GetRepository();
            string revId = revision.GetGlobalId();
            repo.PullFromRef(_repoUrl, revId);
            repo.CleanUpdate(revId);
            try
            {
                FileUtil.DeleteRecursively(workDir);
                repo.Archive(workDir); // update the working directory
            }
            catch (RepoException e)
            {
                if (e.Message is not null && e.Message.Contains("abort: no files match the archive pattern"))
                {
                    throw new ValidationException("The origin repository is empty", e);
                }

                throw;
            }
            catch (IOException e)
            {
                throw new RepoException("Error checking out " + _repoUrl, e);
            }

            _hgOriginOptions.MaybeRunCheckoutHook(workDir, _generalOptions);
        }

        public Origin.ChangesResponse<HgRevision> Changes(HgRevision? fromRef, HgRevision toRef)
        {
            string fromRefExpression = fromRef == null ? "null" : fromRef.GetGlobalId();
            // The "<from>::<to>" part is to filter out unrelated history. The "only()" bit is
            // so we include commits merged in from a side branch.
            string refRange = string.Format(
                "only({0}::{1}, {2})", fromRefExpression, toRef.GetGlobalId(), fromRefExpression);

            try
            {
                ChangeReader reader = ChangeReaderBuilder().Build();
                IReadOnlyList<Change<HgRevision>> changes = reader.Run(refRange);

                if (changes.Count != 0)
                {
                    return Origin.ChangesResponse<HgRevision>.ForChangesWithMerges(changes);
                }

                if (fromRef == null)
                {
                    return Origin.ChangesResponse<HgRevision>.NoChanges(Origin.EmptyReason.NoChanges);
                }

                return Origin.ChangesResponse<HgRevision>.NoChanges(
                    GetEmptyReason(fromRef.GetGlobalId(), toRef.GetGlobalId()));
            }
            catch (ValidationException e)
            {
                throw new RepoException($"Error querying changes: {e.Message}", e.InnerException);
            }
        }

        private Origin.EmptyReason GetEmptyReason(string fromRef, string toRef)
        {
            Preconditions.CheckNotNull(fromRef);
            Preconditions.CheckNotNull(toRef);
            IReadOnlyList<HgLogEntry> logEntries = GetRepository().Log()
                .WithReferenceExpression(string.Format("ancestor({0}, {1})", fromRef, toRef))
                .Run();

            if (logEntries.Count == 0)
            {
                // If fromRef equals toRef and there are no common ancestors, there must be no changes
                if (fromRef == toRef)
                {
                    return Origin.EmptyReason.NoChanges;
                }

                // No common ancestors
                return Origin.EmptyReason.UnrelatedRevisions;
            }

            // fromRef is an ancestor of toRef but changes are irrelevant
            if (logEntries[0].GetGlobalId() == fromRef)
            {
                return Origin.EmptyReason.NoChanges;
            }

            // toRef is an ancestor of fromRef
            if (logEntries[0].GetGlobalId() == toRef)
            {
                return Origin.EmptyReason.ToIsAncestor;
            }

            // fromRef and toRef share an ancestor but are not directly related to each other
            return Origin.EmptyReason.UnrelatedRevisions;
        }

        public Change<HgRevision> Change(HgRevision @ref)
        {
            IReadOnlyList<Change<HgRevision>> changes;

            try
            {
                ChangeReader reader = ChangeReaderBuilder().SetLimit(1).Build();
                changes = reader.Run(@ref.GetGlobalId());
            }
            catch (ValidationException e)
            {
                throw new RepoException($"Error getting change: {e.Message}");
            }

            if (changes.Count == 0)
            {
                throw new EmptyChangeException($"{@ref.AsString()} reference cannot be found");
            }

            Change<HgRevision> rev = changes[0];

            return new Change<HgRevision>(
                @ref,
                rev.GetAuthor(),
                rev.GetMessage(),
                rev.GetDateTime(),
                rev.GetLabels(),
                rev.GetChangeFiles(),
                rev.IsMerge(),
                rev.GetParents());
        }

        public void VisitChanges(HgRevision? start, IChangesVisitor visitor)
        {
            ChangeReader.Builder queryChanges = ChangeReaderBuilder();
            var roots = OriginFiles.Roots();

            HgVisitorUtil.VisitChanges(
                start!,
                new RootFilteringVisitor(roots, visitor),
                queryChanges,
                _generalOptions,
                "origin",
                _hgOptions.VisitChangeDepth);
        }

        // Mirrors the affectsRoots(...) lambda in the Java HgOrigin.visitChanges.
        private sealed class RootFilteringVisitor : IChangesVisitor
        {
            private readonly ImmutableHashSet<string> _roots;
            private readonly IChangesVisitor _delegate;

            public RootFilteringVisitor(ImmutableHashSet<string> roots, IChangesVisitor @delegate)
            {
                _roots = roots;
                _delegate = @delegate;
            }

            public VisitResult Visit(Change<IRevision> input) =>
                Glob.AffectsRoots(_roots, input.GetChangeFiles())
                    ? _delegate.Visit(input)
                    : VisitResult.Continue;
        }
    }

    public IOrigin<HgRevision>.IReader<HgRevision> NewReader(Glob originFiles, AuthoringModel authoring) =>
        new ReaderImpl(_repoUrl, _hgOptions, authoring, _generalOptions, _hgOriginOptions, originFiles);

    public override string ToString() => $"HgOrigin{{url = {_repoUrl}, ref = {_configRef}}}";

    public string GetLabelName() => HgOriginRevId;

    public string GetTypeName() => "hg.origin";

    public ImmutableListMultimap<string, string> Describe(Glob? originFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", GetTypeName());
        builder.Put("url", _repoUrl);
        if (_configRef != null)
        {
            builder.Put("ref", _configRef);
        }

        return builder.Build();
    }

    /// <summary>Builds a new <see cref="HgOrigin"/>.</summary>
    internal static HgOrigin NewHgOrigin(Options options, string url, string @ref) =>
        new(
            options.Get<GeneralOptions>(),
            options.Get<HgOptions>(),
            url,
            @ref,
            options.Get<HgOriginOptions>());
}
