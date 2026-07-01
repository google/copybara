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

namespace Copybara.Approval;

/// <summary>
/// A predicate represents a statement over a change. This is an approximation to
/// <see href="https://github.com/in-toto/attestation/tree/v0.1.0/spec#predicate"/> predicates.
/// Port of <c>com.google.copybara.approval.StatementPredicate</c>.
/// </summary>
public class StatementPredicate
{
    private readonly string _type;
    private readonly string _description;
    private readonly string _url;

    public StatementPredicate(string type, string description, string url)
    {
        _type = type;
        _description = description;
        _url = url;
    }

    /// <summary>
    /// Utility method that filters out elements of <paramref name="list"/> that are an instance of
    /// <typeparamref name="T"/>.
    /// </summary>
    public static ImmutableArray<T> FilterByClass<T>(
        IEnumerable<StatementPredicate> list)
        where T : StatementPredicate =>
        list.OfType<T>().ToImmutableArray();

    /// <summary>Predicate type: Approval, ownership, etc.</summary>
    public string Type() => _type;

    /// <summary>Text representation of the predicate for human consumption.</summary>
    public string Description() => _description;

    /// <summary>
    /// Returns where the predicate happened (E.g. an approval in GitHub would have
    /// https://github.com/example/project/pull/123 as the url).
    /// </summary>
    public string Url() => _url;

    public override bool Equals(object? o)
    {
        if (ReferenceEquals(this, o))
        {
            return true;
        }
        if (o is not StatementPredicate that)
        {
            return false;
        }
        return string.Equals(_type, that._type)
            && string.Equals(_description, that._description)
            && string.Equals(_url, that._url);
    }

    public override int GetHashCode() => HashCode.Combine(_type, _description, _url);

    public sealed override string ToString() => ToStringDescription();

    protected virtual string ToStringDescription() =>
        $"StatementPredicate{{type={_type}, description={_description}, url={_url}}}";
}
