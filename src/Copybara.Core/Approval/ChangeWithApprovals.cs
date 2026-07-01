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
using Copybara.Common;
using Copybara.Revision;

namespace Copybara.Approval;

/// <summary>
/// Approvals for a change reference. Port of
/// <c>com.google.copybara.approval.ChangeWithApprovals</c>.
/// TODO: Rename this to Statement
/// (https://github.com/in-toto/attestation/tree/v0.1.0/spec#predicate).
/// </summary>
public sealed class ChangeWithApprovals
{
    private readonly Change<IRevision> _change;
    private readonly ImmutableArray<StatementPredicate> _predicates;

    public ChangeWithApprovals(Change<IRevision> change)
        : this(change, ImmutableArray<StatementPredicate>.Empty)
    {
    }

    public ChangeWithApprovals(
        Change<IRevision> change, ImmutableArray<StatementPredicate> predicates)
    {
        _change = Preconditions.CheckNotNull(change);
        _predicates = predicates;
    }

    public Change<IRevision> GetChange() => _change;

    public IReadOnlyList<StatementPredicate> GetPredicates() => _predicates;

    public ChangeWithApprovals AddApprovals(IEnumerable<StatementPredicate> approvals) =>
        new(_change, _predicates.AddRange(approvals));

    public override string ToString() =>
        $"ChangeWithApprovals{{change={_change}, predicates=[{string.Join(", ", _predicates)}]}}";

    public override bool Equals(object? o)
    {
        if (ReferenceEquals(this, o))
        {
            return true;
        }
        if (o is not ChangeWithApprovals that)
        {
            return false;
        }
        return Equals(_change, that._change) && _predicates.SequenceEqual(that._predicates);
    }

    public override int GetHashCode()
    {
        var hash = new HashCode();
        hash.Add(_change);
        foreach (var p in _predicates)
        {
            hash.Add(p);
        }
        return hash.ToHashCode();
    }
}
