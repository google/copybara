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

using Copybara.Authoring;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;

using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara;

/// <summary>
/// A module to expose Starlark <c>glob()</c>, <c>parse_message()</c>, etc. functions.
///
/// <para>Don't add functions here and prefer the "core" namespace unless it is something really
/// general.</para>
/// </summary>
public sealed class CoreGlobal : IStarlarkValue
{
    [StarlarkMethod("glob",
        Doc =
            "Returns an object which matches every file in the workdir that matches at least one"
            + " pattern in include and does not match any of the patterns in exclude.")]
    public Glob Glob(
        [Param(Name = "include", Named = true,
            Doc = "The list of glob patterns to include",
            AllowedTypes = new[] { typeof(StarlarkList) })]
        StarlarkList include,
        [Param(Name = "exclude", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "The list of glob patterns to exclude",
            AllowedTypes = new[] { typeof(StarlarkList) })]
        StarlarkList exclude)
    {
        var includeStrings = ConvertStringList(include, "include");
        var excludeStrings = ConvertStringList(exclude, "exclude");
        try
        {
            return Copybara.Util.Glob.CreateGlob(includeStrings, excludeStrings);
        }
        catch (ArgumentException e)
        {
            throw StarlarkRt.Errorf(
                "Cannot create a glob from: include='{0}' and exclude='{1}': {2}",
                string.Join(", ", includeStrings), string.Join(", ", excludeStrings), e.Message);
        }
    }

    [StarlarkMethod("parse_message",
        Doc = "Returns a ChangeMessage parsed from a well formed string.")]
    public ChangeMessage ParseMessage(
        [Param(Name = "message", Named = true, Doc = "The contents of the change message")]
        string changeMessage)
    {
        try
        {
            return ChangeMessage.ParseMessage(changeMessage);
        }
        catch (Exception e)
        {
            throw StarlarkRt.Errorf(
                "Cannot parse change message '{0}': {1}", changeMessage, e.Message);
        }
    }

    [StarlarkMethod("new_author",
        Doc = "Create a new author from a string with the form 'name <foo@bar.com>'")]
    public Author NewAuthor(
        [Param(Name = "author_string", Named = true,
            Doc = "A string representation of the author with the form 'name <foo@bar.com>'")]
        string authorString) =>
        Author.Parse(authorString);

    // Port of SkylarkUtil.convertStringList (that helper is being ported concurrently in
    // Copybara.Config). Inlined here to keep CoreGlobal self-contained.
    internal static List<string> ConvertStringList(IEnumerable<object?> list, string name)
    {
        var result = new List<string>();
        foreach (var o in list)
        {
            if (o is not string s)
            {
                throw StarlarkRt.Errorf(
                    "Expected a string for element of '{0}', but got {1}", name, StarlarkRt.Type(o));
            }

            result.Add(s);
        }

        return result;
    }
}
