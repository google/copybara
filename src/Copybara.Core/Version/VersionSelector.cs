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
using System.Linq;
using Copybara.TemplateToken;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Version;

/// <summary>
/// Select a version from a list of versions, using custom logic (For example, semantic versioning,
/// etc.).
/// </summary>
[StarlarkBuiltin("VersionSelector", Doc = "Select a version from a list of versions")]
public interface IVersionSelector : IStarlarkPrintableValue
{
    /// <summary>
    /// Selects a version from the given <paramref name="versionList"/>. Returns <c>null</c> if no
    /// version was selected.
    /// </summary>
    /// <exception cref="Copybara.Exceptions.ValidationException"/>
    /// <exception cref="Copybara.Exceptions.RepoException"/>
    string? Select(IVersionList versionList, string? requestedRef, Console console);

    /// <summary>
    /// Give a hint on what the version selector is interested in so that an <see cref="IVersionList"/>
    /// can be more efficient in listing valid versions.
    ///
    /// <para>A SearchPattern is composed of tokens that are either a literal or an interpolation.
    /// The interpolation name is not important and might be ignored by the <see cref="IVersionList"/>.
    /// But if present, it can be used to report debugging information about a particular part of a
    /// version found.</para>
    ///
    /// <para>Two edge cases:</para>
    /// <list type="bullet">
    /// <item>Empty list: Means that the selector doesn't use patterns (For example, because it uses
    /// the CLI reference).</item>
    /// <item>Single interpolation token: Means that it is interested in all the references.
    /// Equivalent to '*'.</item>
    /// </list>
    /// </summary>
    IReadOnlySet<SearchPattern> SearchPatterns() => SearchPattern.NONE;

    void IStarlarkPrintableValue.Repr(Printer printer, StarlarkSemantics semantics) =>
        printer.Append(ToString());
}

/// <summary>
/// A search pattern is a wrapper class of <see cref="Token"/> list that expresses the pattern of the
/// versions the <see cref="IVersionSelector"/> is interested in. This allows having literals mixed
/// with interpolations (e.g. foo.*bar -&gt; [foo, .*, bar]).
/// </summary>
public sealed class SearchPattern
{
    private readonly ImmutableArray<Token> _tokens;

    public static readonly IReadOnlySet<SearchPattern> ALL =
        ImmutableHashSet.Create(
            new SearchPattern(ImmutableArray.Create(Token.Interpolation("all"))));

    public static readonly IReadOnlySet<SearchPattern> NONE = ImmutableHashSet<SearchPattern>.Empty;

    public SearchPattern(IReadOnlyList<Token> tokens)
    {
        _tokens = tokens.ToImmutableArray();
    }

    public IReadOnlyList<Token> Tokens() => _tokens;

    /// <summary>Returns true if the search pattern is interested in all references.</summary>
    public bool IsAll() => _tokens.All(t => t.GetTokenType() == TokenType.Interpolation);

    /// <summary>
    /// Returns true if the search pattern doesn't use the references from the
    /// <see cref="IVersionList"/> as primary data for selecting the version.
    /// </summary>
    public bool IsNone() => _tokens.IsEmpty;

    public override bool Equals(object? o)
    {
        if (ReferenceEquals(this, o))
        {
            return true;
        }
        return o is SearchPattern that && _tokens.SequenceEqual(that._tokens);
    }

    public override int GetHashCode()
    {
        var hash = new HashCode();
        foreach (var token in _tokens)
        {
            hash.Add(token);
        }
        return hash.ToHashCode();
    }

    public override string ToString() =>
        $"SearchPattern{{tokens=[{string.Join(", ", _tokens)}]}}";
}
