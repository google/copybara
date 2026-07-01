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

using System.Collections.Concurrent;
using System.Text.RegularExpressions;
using Copybara.Common;

namespace Copybara.Authoring;

/// <summary>
/// A parser for the standard author format <c>"Name &lt;email&gt;"</c>.
///
/// <para>This is the format used by most VCS (Git, Mercurial) and also by the Copybara configuration
/// itself. The parser is lenient: <c>email</c> can be empty, and it doesn't validate that is an
/// actual email.</para>
/// </summary>
public class AuthorParser
{
    // Anchored to emulate Java Matcher.matches(), which requires the entire input to match.
    private static readonly Regex AuthorPattern =
        new(@"\A(?<name>[^<]+)<(?<email>[^>]*)>\z");

    // The cache mirrors Guava's LoadingCache used by Copybara for repeated author loads.
    private static readonly ConcurrentDictionary<string, Author> Cache = new();

    /// <summary>Parses a Git author <c>string</c> into an <see cref="Author"/>.</summary>
    public static Author Parse(string author)
    {
        Preconditions.CheckNotNull(author);
        // Use a cache since repetitive load (thru --read-config-from-change) configs that
        // define authors have a penalty because of the regex check/group.
        if (Cache.TryGetValue(author, out var cached))
        {
            return cached;
        }
        var parsed = InternalParse(author);
        Cache[author] = parsed;
        return parsed;
    }

    private static Author InternalParse(string author)
    {
        if (IsInQuotes(author))
        {
            author = author.Substring(1, author.Length - 2); // strip quotes
        }
        Match matcher = AuthorPattern.Match(author);
        if (matcher.Success)
        {
            return new Author(matcher.Groups[1].Value.Trim(), matcher.Groups[2].Value.Trim());
        }
        throw new InvalidAuthorException(
            $"Invalid author '{author}'. Must be in the form of 'Name <email>'");
    }

    private static bool IsInQuotes(string author)
    {
        // Equivalent to re2j pattern ("(\".+\")|(\'.+\')") with full-string match semantics:
        // either a double-quoted or single-quoted string whose contents are at least one char.
        if (author.Length >= 3 && author[0] == '"' && author[^1] == '"')
        {
            return true;
        }
        if (author.Length >= 3 && author[0] == '\'' && author[^1] == '\'')
        {
            return true;
        }
        return false;
    }
}
