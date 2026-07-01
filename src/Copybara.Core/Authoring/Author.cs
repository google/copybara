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

using Copybara.Common;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Authoring;

/// <summary>
/// Represents the contributor of a change in the destination repository. A contributor can be either
/// an individual or a team.
///
/// <para>Author is lenient in name or email validation.</para>
/// </summary>
[StarlarkBuiltin("author", Doc = "Represents the author of a change")]
public sealed class Author : IStarlarkValue
{
    private readonly string _name;
    private readonly string _email;

    public Author(string name, string email)
    {
        _name = Preconditions.CheckNotNull(name);
        _email = Preconditions.CheckNotNull(email);
    }

    /// <summary>Returns the name of the author.</summary>
    [StarlarkMethod("name", Doc = "The name of the author", StructField = true)]
    public string Name => _name;

    /// <summary>Returns the email address of the author.</summary>
    [StarlarkMethod("email", Doc = "The email of the author", StructField = true)]
    public string Email => _email;

    /// <summary>
    /// Returns the string representation of an author, which is the standard format
    /// <c>Name &lt;email&gt;</c> used by most version control systems.
    /// </summary>
    public override string ToString() => $"{_name} <{_email}>";

    public override bool Equals(object? o)
    {
        if (o is Author that)
        {
            // Authors with the same non-empty email are the same author.
            return string.IsNullOrEmpty(_email) && string.IsNullOrEmpty(that._email)
                ? string.Equals(_name, that._name)
                : string.Equals(_email, that._email);
        }
        return false;
    }

    /// <summary>Parse author from a String in the format of: "name &lt;foo@bar.com&gt;".</summary>
    public static Author Parse(string authorStr)
    {
        try
        {
            return AuthorParser.Parse(authorStr);
        }
        catch (InvalidAuthorException e)
        {
            throw Starlark.Eval.Starlark.Errorf(
                "Author '{0}' doesn't match the expected format 'name <mail@example.com>: {1}",
                authorStr,
                e.Message);
        }
    }

    public override int GetHashCode() =>
        string.IsNullOrEmpty(_email) ? _name.GetHashCode() : _email.GetHashCode();
}
