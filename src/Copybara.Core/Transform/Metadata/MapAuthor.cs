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
using Copybara.Authoring;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Transform;
using Starlark.Eval;
using Starlark.Syntax;

namespace Copybara.Transform.Metadata;

/// <summary>Map authors between revision systems.</summary>
public class MapAuthor : ITransformation
{
    // Author only uses mail for comparison.
    private readonly ImmutableDictionary<string, string> _authorToAuthor;
    private readonly ImmutableDictionary<string, Author> _mailToAuthor;
    private readonly ImmutableDictionary<string, Author> _nameToAuthor;
    private readonly bool _reversible;
    private readonly bool _noopReverse;
    private readonly bool _failIfNotFound;
    private readonly bool _failIfNotFoundInReverse;
    private readonly bool _mapAll;
    private readonly Location _location;

    private MapAuthor(
        Location location,
        ImmutableDictionary<string, string> authorToAuthor,
        ImmutableDictionary<string, Author> mailToAuthor,
        ImmutableDictionary<string, Author> nameToAuthor,
        bool reversible,
        bool noopReverse,
        bool failIfNotFound,
        bool failIfNotFoundInReverse,
        bool mapAll)
    {
        _location = Preconditions.CheckNotNull(location);
        _authorToAuthor = Preconditions.CheckNotNull(authorToAuthor);
        _mailToAuthor = Preconditions.CheckNotNull(mailToAuthor);
        _nameToAuthor = Preconditions.CheckNotNull(nameToAuthor);
        _reversible = reversible;
        _noopReverse = noopReverse;
        _failIfNotFound = failIfNotFound;
        _failIfNotFoundInReverse = failIfNotFoundInReverse;
        _mapAll = mapAll;
    }

    public static MapAuthor Create(
        Location location,
        IReadOnlyDictionary<string, string> authorMap,
        bool reversible,
        bool noopReverse,
        bool failIfNotFound,
        bool failIfNotFoundInReverse,
        bool mapAll)
    {
        var authorToAuthor = ImmutableDictionary.CreateBuilder<string, string>();
        var mailToAuthor = ImmutableDictionary.CreateBuilder<string, Author>();
        var nameToAuthor = ImmutableDictionary.CreateBuilder<string, Author>();

        foreach (var e in authorMap)
        {
            Author to = Author.Parse(e.Value);
            try
            {
                authorToAuthor.Add(AuthorParser.Parse(e.Key).ToString(), to.ToString());
            }
            catch (InvalidAuthorException)
            {
                if (e.Key.Contains('@'))
                {
                    mailToAuthor.Add(e.Key, to);
                }
                else
                {
                    nameToAuthor.Add(e.Key, to);
                }
            }
        }

        return new MapAuthor(
            location,
            authorToAuthor.ToImmutable(),
            mailToAuthor.ToImmutable(),
            nameToAuthor.ToImmutable(),
            reversible,
            noopReverse,
            failIfNotFound,
            failIfNotFoundInReverse,
            mapAll);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        work.SetAuthor(GetMappedAuthor(work.GetAuthor()));

        if (_mapAll)
        {
            foreach (var changeObj in work.GetChanges().GetCurrent())
            {
                var current = (Change<IRevision>)changeObj;
                current.SetMappedAuthor(GetMappedAuthor(current.GetAuthor()));
            }
        }

        return TransformationStatus.Success();
    }

    private Author GetMappedAuthor(Author originalAuthor)
    {
        if (_authorToAuthor.TryGetValue(originalAuthor.ToString(), out var newAuthor))
        {
            try
            {
                return AuthorParser.Parse(newAuthor);
            }
            catch (InvalidAuthorException e)
            {
                throw new InvalidOperationException("Shouldn't happen. We validate before", e);
            }
        }
        if (_mailToAuthor.TryGetValue(originalAuthor.Email, out var byMail))
        {
            return byMail;
        }
        if (_nameToAuthor.TryGetValue(originalAuthor.Name, out var byName))
        {
            return byName;
        }
        ValidationException.CheckCondition(
            !_failIfNotFound, "Cannot find a mapping for author '{0}'", originalAuthor);
        return originalAuthor;
    }

    public ITransformation Reverse()
    {
        if (_noopReverse)
        {
            return new ExplicitReversal(IntentionalNoop.Instance, this);
        }
        if (!_reversible)
        {
            throw new NonReversibleValidationException(
                "Author mapping doesn't have reversible enabled");
        }
        if (!_mailToAuthor.IsEmpty)
        {
            throw new NonReversibleValidationException(
                "author mapping is not reversible because it contains mail -> author mappings."
                + " Only author -> author is reversible: "
                + FormatMap(_nameToAuthor));
        }
        if (!_nameToAuthor.IsEmpty)
        {
            throw new NonReversibleValidationException(
                "author mapping is not reversible because it contains name -> author mappings."
                + " Only author -> author is reversible: "
                + FormatMap(_nameToAuthor));
        }

        var reverse = ImmutableDictionary.CreateBuilder<string, string>();
        foreach (var kv in _authorToAuthor)
        {
            if (reverse.ContainsKey(kv.Value))
            {
                throw new NonReversibleValidationException(
                    "non-reversible author map: value '" + kv.Value + "' mapped more than once");
            }
            reverse.Add(kv.Value, kv.Key);
        }
        return new MapAuthor(
            _location,
            reverse.ToImmutable(),
            ImmutableDictionary<string, Author>.Empty,
            ImmutableDictionary<string, Author>.Empty,
            _reversible,
            _noopReverse,
            _failIfNotFoundInReverse,
            _failIfNotFound,
            _mapAll);
    }

    private static string FormatMap<TValue>(ImmutableDictionary<string, TValue> map) =>
        "{" + string.Join(", ", map.Select(kv => $"{kv.Key}={kv.Value}")) + "}";

    public string Describe() => "Mapping authors";

    public Location Location() => _location;

    public override string ToString() =>
        $"MapAuthor{{authorToAuthor={FormatMap(_authorToAuthor)}, mailToAuthor="
        + $"{FormatMap(_mailToAuthor)}, nameToAuthor={FormatMap(_nameToAuthor)}, "
        + $"reversible={_reversible}, failIfNotFound={_failIfNotFound}, "
        + $"failIfNotFoundInReverse={_failIfNotFoundInReverse}, location={_location}}}";
}
