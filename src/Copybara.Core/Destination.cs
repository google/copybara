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
using System.Security.Cryptography;
using Copybara.Common;
using Copybara.Effect;
using Copybara.Revision;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara;

/// <summary>A repository which a source of truth can be copied to.</summary>
/// <typeparam name="R">the revision type this destination handles.</typeparam>
[StarlarkBuiltin("destination", Doc = "A repository which a source of truth can be copied to")]
public interface IDestination<R> : IConfigItemDescription, IStarlarkValue
    where R : class, IRevision
{
    /// <summary>
    /// Creates a writer which is capable of writing to this destination. This writer may maintain
    /// state between writing of revisions.
    /// </summary>
    /// <exception cref="Copybara.Exceptions.ValidationException">
    /// if the writer could not be created because of a user error.
    /// </exception>
    IWriter<R> NewWriter(WriterContext writerContext);

    /// <summary>
    /// Given a reverse workflow with an <c>Origin</c> that is of the same type as this destination,
    /// the label that <c>IOrigin.GetLabelName()</c> would return.
    /// </summary>
    string GetLabelNameWhenOrigin();

    /// <summary>
    /// A hash function that is preferred by the Destination for use cases where hashing is involved.
    /// </summary>
    HashAlgorithmName GetHashFunction() => HashAlgorithmName.SHA256;

    /// <summary>
    /// An object which is capable of writing multiple revisions to the destination. This object is
    /// allowed to maintain state between the writing of revisions if applicable.
    /// </summary>
    /// <typeparam name="TR">the revision type.</typeparam>
    interface IWriter<TR> : IChangeVisitable<TR>
        where TR : class, IRevision
    {
        /// <summary>Returns the status of the import at the destination.</summary>
        DestinationStatus? GetDestinationStatus(Glob destinationFiles, string labelName);

        /// <summary>
        /// Returns true if this destination stores revisions in the repository so that
        /// <see cref="GetDestinationStatus"/> can be used for discovering the state of the
        /// destination and we can use the methods in <see cref="IChangeVisitable{TR}"/>.
        /// </summary>
        bool SupportsHistory();

        /// <summary>Writes the fully-transformed repository stored at workdir to this destination.</summary>
        IReadOnlyList<DestinationEffect> Write(
            TransformResult transformResult, Glob destinationFiles, Console console);

        /// <summary>Utility endpoint for accessing and adding feedback data.</summary>
        IEndpoint GetFeedbackEndPoint(Console console) => IEndpoint.NoopEndpoint;

        DestinationReader GetDestinationReader(
            Console console, Origin.Baseline<IRevision>? baseline, string workdir) =>
            DestinationReader.NotImplemented;

        DestinationReader GetDestinationReader(Console console, string? baseline, string workdir) =>
            DestinationReader.NotImplemented;

        IPatchRegenerator? GetPatchRegenerator(Console console) => null;

        /// <summary>Returns the <see cref="IDestinationInfo"/> object for this destination.</summary>
        IDestinationInfo? GetDestinationInfo() => null;
    }

    /// <summary>Writers that implement PatchRegenerator can be used with RegenerateCmd.</summary>
    interface IPatchRegenerator
    {
        /// <summary>
        /// Write the files in the workdir to an already-existing change created by Copybara. This is
        /// used to update a pending change with new patch files.
        /// </summary>
        void UpdateChange(
            string workflowName, string workdir, Glob destinationFiles, string changeToUpdate) =>
            throw new Exceptions.ValidationException(
                "update change not implemented for this destination");

        /// <summary>Detect regen baseline when not supplied by CLI.</summary>
        string? InferRegenBaseline() => null;

        /// <summary>Detect regen target when not supplied by CLI.</summary>
        string? InferRegenTarget() => null;

        /// <summary>Detect import baseline when not supplied by CLI.</summary>
        string? InferImportBaseline(string regenTarget, string workdir) => null;
    }
}

/// <summary>
/// This class represents the status of the destination. It includes the baseline revision and, if
/// it is a code review destination, the list of pending changes that have been already migrated.
/// In order: first change is the oldest one.
///
/// <para>Port of the nested <c>Destination.DestinationStatus</c> Java type (kept at top level here
/// as it does not depend on the destination's revision type).</para>
/// </summary>
public sealed class DestinationStatus
{
    private readonly string _baseline;
    private readonly ImmutableArray<string> _pendingChanges;

    public DestinationStatus(string baseline, IReadOnlyList<string> pendingChanges)
    {
        _baseline = Preconditions.CheckNotNull(baseline);
        _pendingChanges = Preconditions.CheckNotNull(pendingChanges).ToImmutableArray();
    }

    /// <summary>String representation of the latest migrated revision in the baseline.</summary>
    public string GetBaseline() =>
        Preconditions.CheckNotNull(_baseline, "Trying to get baseline for NO_STATUS");

    /// <summary>
    /// String representation of the migrated revisions that are in pending state in the destination.
    /// First element is the oldest one. Last element the newest one.
    /// </summary>
    public IReadOnlyList<string> GetPendingChanges() => _pendingChanges;

    public override bool Equals(object? o)
    {
        if (ReferenceEquals(this, o))
        {
            return true;
        }
        if (o is null || GetType() != o.GetType())
        {
            return false;
        }
        var that = (DestinationStatus)o;
        return string.Equals(_baseline, that._baseline)
            && _pendingChanges.SequenceEqual(that._pendingChanges);
    }

    public override int GetHashCode()
    {
        var hash = new HashCode();
        hash.Add(_baseline);
        foreach (var c in _pendingChanges)
        {
            hash.Add(c);
        }
        return hash.ToHashCode();
    }

    public override string ToString() =>
        $"DestinationStatus{{baseline={_baseline}, pendingChanges=[{string.Join(", ", _pendingChanges)}]}}";
}
