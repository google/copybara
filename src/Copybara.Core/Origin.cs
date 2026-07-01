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

using System.Collections.Immutable;
using Copybara.Approval;
using Copybara.Authoring;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara;

/// <summary>An <c>Origin</c> represents a source control repository from which source is copied.</summary>
/// <typeparam name="R">the origin type of the references/revisions this origin handles.</typeparam>
public interface IOrigin<R> : IConfigItemDescription, Starlark.Eval.IStarlarkValue
    where R : class, IRevision
{
    /// <summary>
    /// Resolves a migration reference into a revision. For example for git it would resolve 'main' to
    /// the SHA-1.
    /// </summary>
    R Resolve(string reference);

    /// <summary>Resolves a migration last migrated reference into a revision.</summary>
    R ResolveLastRev(string reference) => Resolve(reference);

    /// <summary>
    /// Resolves a reference into a revision, but only if the provided descendantRev is an ancestor of
    /// ancestorRef.
    /// </summary>
    R ResolveAncestorRef(string ancestorRef, R descendantRev) =>
        throw new ValidationException("Pinning to an ancestor ref is unsupported by this origin.");

    /// <summary>
    /// Show different changes between two references. Returns null if the origin doesn't support
    /// generating differences.
    /// </summary>
    string? ShowDiff(R revisionFrom, R revisionTo) => null;

    /// <summary>
    /// Get an approvals provider that is able to find for each change its approval status.
    ///
    /// <para>By default we return all the changes without any approval.</para>
    /// </summary>
    IApprovalsProvider GetApprovalsProvider() => new NoneApprovedProvider();

    /// <summary>Creates a new reader of this origin.</summary>
    IReader<R> NewReader(Glob originFiles, Copybara.Authoring.Authoring authoring);

    /// <summary>
    /// Label name to be used when creating a commit message in the destination to refer to a
    /// revision. For example "Git-RevId".
    /// </summary>
    string GetLabelName();

    /// <summary>
    /// An object which is capable of checking out code from the origin at particular paths. This can
    /// also enumerate changes in the history and transform authorship information.
    /// </summary>
    /// <typeparam name="TR">the revision type.</typeparam>
    interface IReader<TR> : IChangeVisitable<TR>
        where TR : class, IRevision
    {
        /// <summary>
        /// Checks out the revision <paramref name="reference"/> from the repository into the
        /// <paramref name="checkoutDir"/> directory.
        /// </summary>
        void Checkout(TR reference, string checkoutDir);

        /// <summary>Returns the list of versions from the origin repository.</summary>
        IReadOnlyList<Change<TR>> GetVersions() => ImmutableArray<Change<TR>>.Empty;

        /// <summary>Returns the changes that happen in the interval (fromRef, toRef].</summary>
        Origin.ChangesResponse<TR> Changes(TR? fromRef, TR toRef);

        /// <summary>
        /// Returns true if the origin repository supports maintaining a history of changes.
        /// Generally this should be true.
        /// </summary>
        bool SupportsHistory() => true;

        /// <summary>Returns a change identified by <paramref name="reference"/>.</summary>
        Change<TR> Change(TR reference);

        /// <summary>Finds the baseline of startRevision.</summary>
        Origin.Baseline<TR>? FindBaseline(TR startRevision, string label)
        {
            var visitor = new Origin.FindLatestWithLabel<TR>(startRevision, label);
            VisitChanges(startRevision, visitor);
            return visitor.GetBaseline();
        }

        /// <summary>Find the baseline of the change without using a label.</summary>
        IReadOnlyList<TR> FindBaselinesWithoutLabel(TR startRevision, int limit) =>
            throw new ValidationException("Origin doesn't support this workflow mode");

        /// <summary>Utility endpoint for accessing and adding feedback data.</summary>
        IEndpoint GetFeedbackEndPoint(Console console) => IEndpoint.NoopEndpoint;
    }
}

/// <summary>
/// Non-generic holder for <see cref="IOrigin{R}"/>'s nested helper types (mirrors the types nested
/// inside the Java <c>Origin</c> / <c>Origin.Reader</c> interfaces).
/// </summary>
public static class Origin
{
    /// <summary>Represents a baseline pointer in the origin.</summary>
    public sealed class Baseline<R>
        where R : class, IRevision
    {
        private readonly string _baseline;
        private readonly R? _originRevision;

        public Baseline(string baseline, R? originRevision)
        {
            _baseline = Preconditions.CheckNotNull(baseline);
            _originRevision = originRevision;
        }

        /// <summary>The baseline reference that will be used in the destination.</summary>
        public string GetBaseline() => _baseline;

        /// <summary>A reference to the origin revision where the baseline was found.</summary>
        public R? GetOriginRevision() => _originRevision;

        public override string ToString() =>
            $"Baseline{{baseline={_baseline}, originRevision={_originRevision}}}";
    }

    /// <summary>Reason why <c>Origin.Reader.Changes</c> didn't return any change.</summary>
    public enum EmptyReason
    {
        /// <summary>'from' is ancestor of 'to' but all changes are for irrelevant files.</summary>
        NoChanges,

        /// <summary>There is no parent/child relationship between 'from' and 'to'.</summary>
        UnrelatedRevisions,

        /// <summary>'to' is equal or ancestor of 'from'.</summary>
        ToIsAncestor,
    }

    public sealed class ChangesResponse<R>
        where R : class, IRevision
    {
        private readonly ImmutableArray<Change<R>> _changes;
        private readonly EmptyReason? _emptyReason;

        /// <summary>
        /// Changes in key will only be included if the value is included. The usage is for non-linear
        /// histories like git where including a change depends if we end up including the merge commit.
        /// </summary>
        private readonly ImmutableDictionary<Change<R>, Change<R>> _conditionalChanges;

        private ChangesResponse(
            ImmutableArray<Change<R>> changes,
            ImmutableDictionary<Change<R>, Change<R>> conditionalChanges,
            EmptyReason? emptyReason)
        {
            _changes = changes;
            _conditionalChanges = Preconditions.CheckNotNull(conditionalChanges);
            _emptyReason = emptyReason;
            Preconditions.CheckArgument(
                _changes.IsEmpty ^ (emptyReason == null),
                "Either we have changes or we have an empty reason");
        }

        public static ChangesResponse<R> ForChanges(IEnumerable<Change<R>> changes)
        {
            var arr = changes.ToImmutableArray();
            Preconditions.CheckArgument(!arr.IsEmpty, "Empty changes not allowed");
            return new ChangesResponse<R>(
                arr, ImmutableDictionary<Change<R>, Change<R>>.Empty, emptyReason: null);
        }

        /// <summary>
        /// Build a ChangesResponse object with changes where some of them are conditional to their
        /// closest first-parent root being included (merge commit).
        /// </summary>
        public static ChangesResponse<R> ForChangesWithMerges(IEnumerable<Change<R>> changes)
        {
            var all = changes.ToList();
            Preconditions.CheckArgument(all.Count != 0, "Shouldn't be called for empty changes");

            var byRevision = new Dictionary<R, Change<R>>();
            foreach (var e in all)
            {
                byRevision[e.GetRevision()] = e;
            }

            var firstParents = new List<Change<R>>();
            var toSkip = new HashSet<R>();
            var latest = all[^1];

            // Compute first parents and add them to toSkip so they are not counted as conditional
            // changes later.
            while (true)
            {
                firstParents.Add(latest);
                toSkip.Add(latest.GetRevision());
                var parents = Parents(latest);
                if (parents.Length == 0)
                {
                    break;
                }
                R firstParent = parents[0];
                if (!byRevision.TryGetValue(firstParent, out var firstParentChange))
                {
                    break;
                }
                latest = firstParentChange;
            }

            var conditionalChanges = ImmutableDictionary.CreateBuilder<Change<R>, Change<R>>();

            // Traverse from old to new so we use oldest first-parent as the conditional change.
            for (int i = firstParents.Count - 1; i >= 0; i--)
            {
                var firstParent = firstParents[i];
                var fpParents = Parents(firstParent);
                if (fpParents.Length < 2)
                {
                    continue;
                }
                var toVisit = new Queue<R>();
                for (int j = 1; j < fpParents.Length; j++)
                {
                    toVisit.Enqueue(fpParents[j]);
                }
                while (toVisit.Count != 0)
                {
                    R revision = toVisit.Dequeue();
                    if (!toSkip.Add(revision))
                    {
                        continue;
                    }
                    if (!byRevision.TryGetValue(revision, out var change))
                    {
                        continue;
                    }
                    conditionalChanges[change] = firstParent;
                    foreach (var p in Parents(change))
                    {
                        toVisit.Enqueue(p);
                    }
                }
            }
            return new ChangesResponse<R>(
                all.ToImmutableArray(), conditionalChanges.ToImmutable(), emptyReason: null);
        }

        private static ImmutableArray<R> Parents(Change<R> change) =>
            Preconditions.CheckNotNull(
                change.GetParents(),
                "Don't use forChangesWithParents for changes that don't support parents: {0}",
                change);

        /// <summary>Create a ChangesResponse that doesn't contain any change.</summary>
        public static ChangesResponse<R> NoChanges(EmptyReason emptyReason) =>
            new(
                ImmutableArray<Change<R>>.Empty,
                ImmutableDictionary<Change<R>, Change<R>>.Empty,
                emptyReason);

        /// <summary>Returns true if there are no changes.</summary>
        public bool IsEmpty() => _changes.IsEmpty;

        public EmptyReason GetEmptyReason() =>
            _emptyReason ?? throw new InvalidOperationException("Use isEmpty() first");

        /// <summary>The changes that happen in the interval (fromRef, toRef].</summary>
        public IReadOnlyList<Change<R>> GetChanges() => _changes;

        /// <summary>Changes that should only be included if the change in the value is also included.</summary>
        public IReadOnlyDictionary<Change<R>, Change<R>> GetConditionalChanges() =>
            _conditionalChanges;
    }

    public sealed class FindLatestWithLabel<R> : IChangesVisitor
        where R : class, IRevision
    {
        private readonly R _startRevision;
        private readonly string _label;
        private Baseline<R>? _baseline;

        public FindLatestWithLabel(R startRevision, string label)
        {
            _startRevision = Preconditions.CheckNotNull(startRevision);
            _label = Preconditions.CheckNotNull(label);
        }

        public Baseline<R>? GetBaseline() => _baseline;

        public VisitResult Visit(Change<IRevision> input)
        {
            if (input.GetRevision().AsString().Equals(_startRevision.AsString()))
            {
                return VisitResult.Continue;
            }
            var labels = input.GetLabels();
            if (!labels.ContainsKey(_label))
            {
                return VisitResult.Continue;
            }
            var values = labels.Get(_label);
            _baseline = new Baseline<R>(values[values.Length - 1], (R)input.GetRevision());
            return VisitResult.Terminate;
        }
    }
}
