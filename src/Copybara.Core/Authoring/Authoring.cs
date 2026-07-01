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
using Copybara.Common;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Authoring;

/// <summary>
/// Represents the authors mapping between an origin and a destination.
///
/// <para>For a given author in the origin, always provides an author in the destination.</para>
/// </summary>
[StarlarkBuiltin(
    "authoring_class",
    Doc = "The authors mapping between an origin and a destination")]
public sealed class Authoring : IStarlarkValue
{
    private readonly Author _defaultAuthor;
    private readonly AuthoringMappingMode _mode;
    private readonly IEvalThrowingPredicate<string> _allowPredicate;

    public Authoring(
        Author defaultAuthor,
        AuthoringMappingMode mode,
        IEvalThrowingPredicate<string> allowPredicate)
    {
        _defaultAuthor = Preconditions.CheckNotNull(defaultAuthor);
        _mode = mode;
        _allowPredicate = Preconditions.CheckNotNull(allowPredicate);
    }

    public Authoring(Author defaultAuthor, AuthoringMappingMode mode, ImmutableHashSet<string> list)
        : this(defaultAuthor, mode, new AllowlistPredicate(Preconditions.CheckNotNull(list)))
    {
    }

    /// <summary>Returns the mapping mode.</summary>
    public AuthoringMappingMode GetMode() => _mode;

    /// <summary>
    /// Returns the default author, used for squash workflows,
    /// <see cref="AuthoringMappingMode.Overwrite"/> mode and for non-allowed authors.
    /// </summary>
    public Author GetDefaultAuthor() => _defaultAuthor;

    /// <summary>
    /// Returns a predicate over allowed author identifiers.
    ///
    /// <para>An identifier is typically an email but might have different representations depending
    /// on the origin.</para>
    /// </summary>
    public IEvalThrowingPredicate<string> GetAllowPredicate() => _allowPredicate;

    /// <summary>Returns true if the user can be safely used.</summary>
    public bool UseAuthor(string userId) =>
        _mode switch
        {
            AuthoringMappingMode.PassThru => true,
            AuthoringMappingMode.Overwrite => false,
            AuthoringMappingMode.Allowed => _allowPredicate.Test(userId),
            _ => throw new InvalidOperationException($"Unexpected mode: {_mode}"),
        };

    /// <summary>Starlark Module for authoring.</summary>
    [StarlarkBuiltin(
        "authoring",
        Doc = "The authors mapping between an origin and a destination")]
    public sealed class Module : IStarlarkValue
    {
        [StarlarkMethod(
            "overwrite",
            Doc =
                "Use the default author for all the submits in the destination. Note that some"
                + " destinations might choose to ignore this author and use the current user"
                + " running the tool (In other words they don't allow impersonation).")]
        public Authoring Overwrite(
            [Param(Name = "default", Named = true, Doc = "The default author for commits in the destination")]
            string defaultAuthor) =>
            new(Author.Parse(defaultAuthor), AuthoringMappingMode.Overwrite, new RejectAllPredicate());

        [StarlarkMethod(
            "pass_thru",
            Doc = "Use the origin author as the author in the destination, no filtering.")]
        public Authoring PassThru(
            [Param(
                Name = "default",
                Named = true,
                Doc =
                    "The default author for commits in the destination. This is used"
                    + " in squash mode workflows or if author cannot be determined.")]
            string defaultAuthor) =>
            new(Author.Parse(defaultAuthor), AuthoringMappingMode.PassThru, new RejectAllPredicate());

        [StarlarkMethod(
            "allowed",
            Doc = "Create a list for an individual or team contributing code.",
            UseStarlarkThread = true)]
        public Authoring Allowed(
            [Param(
                Name = "default",
                Named = true,
                Doc =
                    "The default author for commits in the destination. This is used"
                    + " in squash mode workflows or when users are not on the list.")]
            string defaultAuthor,
            [Param(
                Name = "allowlist",
                Named = true,
                DefaultValue = "None",
                Doc =
                    "List of  authors in the origin that are allowed to contribute code. The "
                    + "authors must be unique")]
            IEnumerable<string>? allowlist,
            [Param(
                Name = "allow_predicate",
                Named = true,
                DefaultValue = "None",
                Doc =
                    "Starlark function to use to check if an author is allowed to contribute code."
                    + " The function should take a single argument (the author) and return"
                    + " true if the author is allowed, false otherwise. Allowlist is ignored if"
                    + " this is set.")]
            IEvalThrowingPredicate<string>? allowPred)
        {
            IEvalThrowingPredicate<string> allowPredicate;
            if (allowPred is null && allowlist is null)
            {
                throw Starlark.Eval.Starlark.Errorf(
                    "'allowed' function requires either an 'allowlist' or an 'allow_predicate' parameter.");
            }
            if (allowPred is not null)
            {
                allowPredicate = allowPred;
            }
            else
            {
                ImmutableHashSet<string> allowedAuthors = CreateAllowlist(allowlist!);
                allowPredicate = new AllowlistPredicate(allowedAuthors);
            }
            return new Authoring(
                Author.Parse(defaultAuthor), AuthoringMappingMode.Allowed, allowPredicate);
        }

        private static ImmutableHashSet<string> CreateAllowlist(IEnumerable<string> list)
        {
            var items = list.ToList();
            if (items.Count == 0)
            {
                throw Starlark.Eval.Starlark.Errorf(
                    "'allowed' function requires a non-empty 'allowlist' field. For default mapping,"
                    + " use 'overwrite(...)' mode instead.");
            }
            var uniqueAuthors = new HashSet<string>();
            foreach (var author in items)
            {
                if (!uniqueAuthors.Add(author))
                {
                    throw Starlark.Eval.Starlark.Errorf("Duplicated allowlist entry '{0}'", author);
                }
            }
            return items.ToImmutableHashSet();
        }
    }

    /// <summary>
    /// Mode used for author mapping from origin to destination.
    ///
    /// <para>This enum is our internal representation for the different Skylark built-in
    /// functions.</para>
    /// </summary>
    public enum AuthoringMappingMode
    {
        /// <summary>Corresponds with <see cref="Module.Overwrite"/> built-in function.</summary>
        Overwrite,

        /// <summary>Corresponds with <see cref="Module.PassThru"/> built-in function.</summary>
        PassThru,

        /// <summary>Corresponds to <see cref="Module.Allowed"/> built-in function.</summary>
        Allowed,
    }

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
        var authoring = (Authoring)o;
        return Equals(_defaultAuthor, authoring._defaultAuthor)
            && _mode == authoring._mode
            && Equals(_allowPredicate, authoring._allowPredicate);
    }

    public override int GetHashCode() => HashCode.Combine(_defaultAuthor, _mode, _allowPredicate);

    public override string ToString() =>
        $"Authoring{{defaultAuthor={_defaultAuthor}, mode={_mode}, allowPredicate={_allowPredicate}}}";

    /// <summary>A predicate that can throw <see cref="EvalException"/>.</summary>
    public interface IEvalThrowingPredicate<in T>
    {
        bool Test(T input);
    }

    /// <summary>
    /// A predicate that uses a Starlark function to check if an author is allowed to be attributed
    /// for code.
    /// </summary>
    /// <remarks>
    /// Java wraps a <c>StarlarkCallable</c> invoked on a <c>StarlarkThread</c>. Until those types
    /// are ported, this holds a delegate that evaluates the author, preserving the type surface.
    /// </remarks>
    public sealed class AllowPredicate : IEvalThrowingPredicate<string>
    {
        private readonly Func<string, bool> _allowPred;

        public AllowPredicate(Func<string, bool> allowPred)
        {
            _allowPred = allowPred;
        }

        public bool Test(string author) => _allowPred(author);

        public override bool Equals(object? obj) =>
            obj is AllowPredicate other && Equals(_allowPred, other._allowPred);

        public override int GetHashCode() => _allowPred.GetHashCode();
    }

    /// <summary>
    /// A predicate that uses a list of allowed authors to check if an author is allowed to be
    /// attributed for code.
    /// </summary>
    public sealed class AllowlistPredicate : IEvalThrowingPredicate<string>
    {
        private readonly ImmutableHashSet<string> _allowedAuthors;

        public AllowlistPredicate(ImmutableHashSet<string> allowedAuthors)
        {
            _allowedAuthors = allowedAuthors;
        }

        public ImmutableHashSet<string> AllowedAuthors => _allowedAuthors;

        public bool Test(string author) => _allowedAuthors.Contains(author);

        public override bool Equals(object? obj) =>
            obj is AllowlistPredicate other && _allowedAuthors.SetEquals(other._allowedAuthors);

        public override int GetHashCode()
        {
            int hash = 0;
            foreach (var a in _allowedAuthors)
            {
                hash ^= a.GetHashCode();
            }
            return hash;
        }

        public override string ToString() =>
            $"AllowlistPredicate{{allowedAuthors=[{string.Join(", ", _allowedAuthors)}]}}";
    }

    /// <summary>A predicate that always returns false for attribution.</summary>
    public sealed class RejectAllPredicate : IEvalThrowingPredicate<string>
    {
        public bool Test(string author) => false;

        public override bool Equals(object? obj) => obj is RejectAllPredicate;

        public override int GetHashCode() => typeof(RejectAllPredicate).GetHashCode();

        public override string ToString() => "RejectAllPredicate{}";
    }
}
