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
using Copybara.Common;
using Copybara.Revision;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Effect;

/// <summary>An effect happening in the destination as a consequence of the migration.</summary>
[StarlarkBuiltin(
    "destination_effect",
    Doc = "Represents an effect that happened in the destination due to a single migration")]
public class DestinationEffect : IStarlarkPrintableValue
{
    private readonly EffectType _type;
    private readonly string _summary;
    private readonly ImmutableArray<OriginRef> _originRefs;
    private readonly DestinationRef? _destinationRef;
    private readonly ImmutableArray<string> _errors;

    public DestinationEffect(
        EffectType type,
        string summary,
        IEnumerable<OriginRef> originRefs,
        DestinationRef? destinationRef)
        : this(type, summary, originRefs, destinationRef, ImmutableArray<string>.Empty)
    {
    }

    public DestinationEffect(
        EffectType type,
        string summary,
        IEnumerable<OriginRef> originRefs,
        DestinationRef? destinationRef,
        IEnumerable<string> errors)
    {
        _type = type;
        _summary = Preconditions.CheckNotNull(summary);
        _originRefs = Preconditions.CheckNotNull(originRefs).ToImmutableArray();
        _destinationRef = destinationRef;
        _errors = Preconditions.CheckNotNull(errors).ToImmutableArray();
    }

    /// <summary>Returns the origin references included in this effect.</summary>
    public IReadOnlyList<OriginRef> OriginRefs => _originRefs;

    [StarlarkMethod(
        "origin_refs",
        Doc = "List of origin changes that were included in this migration",
        StructField = true)]
    public StarlarkList GetOriginRefsSkylark() => StarlarkList.ImmutableCopyOf(_originRefs);

    /// <summary>Return the type of effect that happened: Create, updated, noop or error.</summary>
    public EffectType Type => _type;

    [StarlarkMethod(
        "type",
        Doc =
            "Return the type of effect that happened: CREATED, UPDATED, NOOP,"
                + " INSUFFICIENT_APPROVALS or ERROR",
        StructField = true)]
    public string GetTypeSkylark() => _type.ToString();

    /// <summary>
    /// Textual summary of what happened. Users of this class should not try to parse this field.
    /// </summary>
    [StarlarkMethod(
        "summary",
        Doc =
            "Textual summary of what happened. Users of this class should not try to parse this"
                + " field.",
        StructField = true)]
    public string Summary => _summary;

    /// <summary>
    /// Destination reference updated/created. Might be null if there was no effect. Might be set even
    /// if the type is error (for example a synchronous presubmit test failed but a review was
    /// created).
    /// </summary>
    [StarlarkMethod(
        "destination_ref",
        Doc =
            "Destination reference updated/created. Might be null if there was no effect. Might be"
                + " set even if the type is error (For example a synchronous presubmit test failed"
                + " but a review was created).",
        StructField = true,
        AllowReturnNones = true)]
    public DestinationRef? GetDestinationRef() => _destinationRef;

    /// <summary>
    /// List of errors that happened during the write to the destination. This can be used for
    /// example for synchronous presubmit failures.
    /// </summary>
    public IReadOnlyList<string> Errors => _errors;

    [StarlarkMethod(
        "errors",
        Doc = "List of errors that happened during the migration",
        StructField = true)]
    public StarlarkList GetErrorsSkylark() => StarlarkList.ImmutableCopyOf(_errors.Cast<object?>());

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
        var that = (DestinationEffect)o;
        return _type == that._type
            && string.Equals(_summary, that._summary)
            && _originRefs.SequenceEqual(that._originRefs)
            && Equals(_destinationRef, that._destinationRef)
            && _errors.SequenceEqual(that._errors);
    }

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"DestinationEffect{{type={_type}, summary={_summary}, originRefs=[{string.Join(", ", _originRefs)}]"
            + $", destinationRef={_destinationRef?.ToString() ?? "null"}, errors=[{string.Join(", ", _errors)}]}}";

    public override int GetHashCode()
    {
        var hash = new HashCode();
        hash.Add(_type);
        hash.Add(_summary);
        foreach (var r in _originRefs)
        {
            hash.Add(r);
        }
        hash.Add(_destinationRef);
        foreach (var e in _errors)
        {
            hash.Add(e);
        }
        return hash.ToHashCode();
    }

    /// <summary>Type of effect on the destination.</summary>
    public enum EffectType
    {
        /// <summary>A new review or change was created.</summary>
        CREATED,

        /// <summary>An existing review or change was updated.</summary>
        UPDATED,

        /// <summary>The change was a noop, relative to the destination's baseline.</summary>
        NOOP,

        /// <summary>The change was a noop, relative to an existing pending change in the destination.</summary>
        NOOP_AGAINST_PENDING_CHANGE,

        /// <summary>The effect couldn't happen because the change doesn't have enough approvals.</summary>
        INSUFFICIENT_APPROVALS,

        /// <summary>
        /// A user attributable error happened that prevented the destination from creating/updating
        /// the change.
        /// </summary>
        ERROR,

        /// <summary>
        /// An error not attributable to the user that could be retried (RepoException, IOException...).
        /// </summary>
        TEMPORARY_ERROR,

        /// <summary>
        /// A starting effect of a migration that is eventually expected to trigger another migration
        /// asynchronously. This allows to have 'dependant' migrations defined by users. An example of
        /// this: a workflow migrates code from a Gerrit review to a GitHub PR, and a feedback
        /// migration migrates the test results from a CI in GitHub back to the Gerrit change. This
        /// effect would be created on the former one.
        /// </summary>
        STARTED,
    }

    /// <summary>Reference to the change/review created/updated on the destination.</summary>
    [StarlarkBuiltin(
        "destination_ref",
        Doc = "Reference to the change/review created/updated on the destination.")]
    public class DestinationRef : IStarlarkPrintableValue
    {
        private readonly string? _url;
        private readonly string _id;
        private readonly string _type;

        public DestinationRef(string id, string type, string? url)
        {
            _id = Preconditions.CheckNotNull(id);
            _type = Preconditions.CheckNotNull(type);
            _url = url;
        }

        /// <summary>Destination reference id.</summary>
        [StarlarkMethod("id", Doc = "Destination reference id", StructField = true)]
        public string Id => _id;

        /// <summary>
        /// Type of reference created. Each destination defines its own and guarantees to be more
        /// stable than urls/ids.
        /// </summary>
        [StarlarkMethod(
            "type",
            Doc =
                "Type of reference created. Each destination defines its own and guarantees to be"
                    + " more stable than urls/ids",
            StructField = true)]
        public string Type => _type;

        /// <summary>Url, if any, of the destination change.</summary>
        [StarlarkMethod(
            "url",
            Doc = "Url, if any, of the destination change",
            StructField = true,
            AllowReturnNones = true)]
        public string? Url => _url;

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
            var that = (DestinationRef)o;
            return string.Equals(_url, that._url)
                && string.Equals(_id, that._id)
                && string.Equals(_type, that._type);
        }

        public override int GetHashCode() => HashCode.Combine(_url, _id, _type);

        public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

        public override string ToString() =>
            $"DestinationRef{{url={_url ?? "null"}, id={_id}, type={_type}}}";
    }
}
