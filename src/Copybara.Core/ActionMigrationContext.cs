/*
 * Copyright (C) 2023 Google Inc.
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
using Copybara.Action;
using Copybara.Common;
using Copybara.Transform;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara;

/// <summary>Skylark context for migrations that can do arbitrary endpoint calls and file manipulations.</summary>
[StarlarkBuiltin(
    // TODO(b/269526710): Rename this. Update docs
    "feedback.context",
    Doc =
        "Gives access to the feedback migration information and utilities. This context is a "
        + "concrete implementation for feedback migrations.")]
public class ActionMigrationContext : ActionContext<ActionMigrationContext>
{
    private readonly ActionMigration _actionMigration;
    private readonly ImmutableArray<string> _refs;
    private readonly ActionFileSystem? _fs;

    internal ActionMigrationContext(
        ActionMigration actionMigration,
        IAction currentAction,
        IReadOnlyDictionary<string, string> labels,
        IReadOnlyList<string> refs,
        SkylarkConsole console)
        : this(actionMigration, currentAction, labels, refs, console, Dict.Empty(), fs: null)
    {
    }

    private ActionMigrationContext(
        ActionMigration actionMigration,
        IAction currentAction,
        IReadOnlyDictionary<string, string> labels,
        IReadOnlyList<string> refs,
        SkylarkConsole console,
        Dict @params,
        ActionFileSystem? fs)
        : base(currentAction, console, labels, @params)
    {
        _actionMigration = Preconditions.CheckNotNull(actionMigration);
        _refs = refs.ToImmutableArray();
        _fs = fs;
    }

    [StarlarkMethod(
        "origin",
        Doc = "An object representing the origin. Can be used to query about the ref or modifying"
            + " the origin state",
        StructField = true)]
    public IEndpoint GetOrigin() =>
        _actionMigration.GetTrigger().GetEndpoint().WithConsole(GetConsole());

    // TODO(b/269526710): Deprecate this function and use endpoints instead.
    [StarlarkMethod(
        "destination",
        Doc = "An object representing the destination. Can be used to query or modify the"
            + " destination state",
        StructField = true)]
    public IEndpoint GetDestination()
    {
        if (_actionMigration.GetEndpoints().GetValue("destination") is IEndpoint e)
        {
            return e;
        }
        throw new InvalidOperationException("Expected an endpoint called destination");
    }

    [StarlarkMethod(
        "endpoints",
        Doc = "An object that gives access to the API of the configured endpoints",
        StructField = true,
        Documented = false)]
    public IStructure GetEndpoints() => _actionMigration.GetEndpoints();

    // TODO(b/269526710): Deprecate this
    [StarlarkMethod(
        "feedback_name",
        Doc = "DEPRECATED: The name of the Feedback migration calling this action."
            + " Use migration_name instead.",
        StructField = true)]
    public string GetFeedbackName() => _actionMigration.GetName();

    [StarlarkMethod(
        "migration_name",
        Doc = "The name of the migration calling this action.",
        Documented = false,
        StructField = true)]
    public string GetMigrationName() => _actionMigration.GetName();

    [StarlarkMethod(
        "refs",
        Doc = "A list containing string representations of the entities that triggered the event",
        StructField = true)]
    public StarlarkList GetRefs() => StarlarkList.ImmutableCopyOf(_refs.Cast<object?>());

    [StarlarkMethod(
        "fs",
        Doc = "If a migration of type `core.action_migration` sets `filesystem = True`, it gives"
            + " access to the underlying migration filesystem to manipulate files.",
        Documented = false,
        StructField = true)]
    public ActionFileSystem GetFs()
    {
        if (_fs == null)
        {
            throw StarlarkRt.Errorf(
                "Migration '{0}' doesn't have access to the filesystem. Use filesystem = True to"
                + " enable it",
                GetMigrationName());
        }
        return _fs;
    }

    public override ActionMigrationContext WithParams(Dict @params) =>
        new(_actionMigration, Action, Labels, _refs, Console, @params, _fs);

    public ActionMigrationContext WithFileSystem(string checkoutDir) =>
        new(_actionMigration, Action, Labels, _refs, Console, GetParams(),
            new ActionFileSystem(checkoutDir));

    [StarlarkBuiltin(
        "action.filesystem",
        Doc = "This object gives access to actions to the filesystem for manipulating files.",
        Documented = false)]
    public sealed class ActionFileSystem : CheckoutFileSystem
    {
        public ActionFileSystem(string checkoutDir)
            : base(checkoutDir)
        {
        }
    }
}
