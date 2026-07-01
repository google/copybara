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

using Copybara.Common;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Action;

/// <summary>
/// Actions are Starlark functions that receive a context object (that is different depending on where
/// it is used) that expose an API to implement custom logic in Starlark. Port of
/// <c>com.google.copybara.action.Action</c>.
/// </summary>
[StarlarkBuiltin(
    "dynamic.action",
    Doc =
        "An action is an Starlark piece of code that does part of a migration. It is used"
        + "to define the logic of migration for feedback workflow, on_finish hooks, git.mirror,"
        + " etc.",
    Documented = false)]
public interface IAction : IStarlarkValue
{
    /// <summary>
    /// Runs the action against the given context.
    /// </summary>
    /// <exception cref="Copybara.Exceptions.ValidationException">if failure is attributable to user setup.</exception>
    /// <exception cref="Copybara.Exceptions.RepoException">if access to the origin/destination fails.</exception>
    void Run<T>(ActionContext<T> context)
        where T : ISkylarkContext<T>;

    string GetName();

    /// <summary>Returns a key-value list of the options the action was instantiated with.</summary>
    ImmutableListMultimap<string, string> Describe();
}
