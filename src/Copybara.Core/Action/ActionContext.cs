/*
 * Copyright (C) 2021 Google Inc.
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
using Copybara.Config;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Transform;
using Starlark.Annot;
using Starlark.Eval;
using ConsoleT = Copybara.Util.Console.Console;
using StarlarkRt = Starlark.Eval.Starlark;
using Sequence = Starlark.Eval.Sequence;

namespace Copybara.Action;

/// <summary>
/// A StarlarkContext for running Actions. Port of <c>com.google.copybara.action.ActionContext</c>.
/// </summary>
public abstract class ActionContext<T> : ISkylarkContext<T>, IStarlarkValue
    where T : ISkylarkContext<T>
{
    protected readonly List<DestinationEffect> NewDestinationEffects = new();
    private ActionResult? _actionResult;

    protected ActionContext(
        IAction action,
        SkylarkConsole console,
        IReadOnlyDictionary<string, string> labels,
        Dict @params)
    {
        Action = Preconditions.CheckNotNull(action);
        Console = Preconditions.CheckNotNull(console);
        Labels = Preconditions.CheckNotNull(labels);
        Params = Preconditions.CheckNotNull(@params);
    }

    /// <summary>The action this context runs.</summary>
    protected IAction Action { get; }

    /// <summary>The console used to report errors or warnings.</summary>
    protected SkylarkConsole Console { get; }

    /// <summary>The CLI labels passed to the migration.</summary>
    protected IReadOnlyDictionary<string, string> Labels { get; }

    private Dict Params { get; }

    [StarlarkMethod(
        "action_name",
        Doc = "The name of the current action.",
        StructField = true)]
    public string GetActionName() => Action.GetName();

    [StarlarkMethod(
        "console",
        Doc = "Get an instance of the console to report errors or warnings",
        StructField = true)]
    public ConsoleT GetConsole() => Console;

    [StarlarkMethod(
        "params",
        Doc = "Parameters for the function if created with core.action",
        StructField = true)]
    public Dict GetParams() => Params;

    [StarlarkMethod("success", Doc = "Returns a successful action result.")]
    public ActionResult Success() => ActionResult.SuccessResult();

    [StarlarkMethod(
        "noop",
        Doc = "Returns a no op action result with an optional message.")]
    public ActionResult Noop(
        [Param(
            Name = "msg",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Doc = "The no op message",
            DefaultValue = "None")]
        object? noopMsg) =>
        ActionResult.NoopResult(SkylarkUtil.ConvertFromNoneable<string>(noopMsg, null));

    [StarlarkMethod(
        "error",
        Doc = "Returns an error action result.")]
    public ActionResult Error(
        [Param(Name = "msg", Doc = "The error message")] string errorMsg) =>
        ActionResult.ErrorResult(errorMsg);

    /// <summary>Return the new <see cref="DestinationEffect"/>s created by this context.</summary>
    public IReadOnlyList<DestinationEffect> GetNewDestinationEffects() =>
        NewDestinationEffects.ToImmutableArray();

    [StarlarkMethod(
        "cli_labels",
        Doc = "Access labels that a user passes through flag '--labels'. "
            + "For example: --labels=foo:value1,bar:value2. Then it can access in this way:"
            + "cli_labels['foo'].",
        StructField = true)]
    public Dict GetCliLabels() =>
        Dict.ImmutableCopyOf(
            Labels.Select(kv => new KeyValuePair<object?, object?>(kv.Key, kv.Value)));

    [StarlarkMethod(
        "record_effect",
        Doc = "Records an effect of the current action.")]
    public void RecordEffect(
        [Param(Name = "summary", Doc = "The summary of this effect", Named = true)]
        string summary,
        [Param(
            Name = "origin_refs",
            AllowedTypes = new[] { typeof(ISequence<object>) },
            Doc = "The origin refs",
            Named = true)]
        ISequence<object?> originRefs,
        [Param(Name = "destination_ref", Doc = "The destination ref", Named = true)]
        DestinationEffect.DestinationRef destinationRef,
        [Param(
            Name = "errors",
            AllowedTypes = new[] { typeof(ISequence<object>) },
            DefaultValue = "[]",
            Doc = "An optional list of errors",
            Named = true)]
        ISequence<object?> errors,
        [Param(
            Name = "type",
            Doc = "The type of migration effect (CREATED, UPDATED, NOOP,"
                + " NOOP_AGAINST_PENDING_CHANGE, INSUFFICIENT_APPROVALS, ERROR, STARTED).",
            DefaultValue = "\"UPDATED\"",
            Named = true)]
        string typeStr)
    {
        var type = SkylarkUtil.StringToEnum<DestinationEffect.EffectType>("type", typeStr);
        NewDestinationEffects.Add(
            new DestinationEffect(
                type,
                summary,
                Sequence.Cast<OriginRef>(originRefs, "origin_refs"),
                destinationRef,
                Sequence.Cast<string>(errors, "errors")));
    }

    public abstract T WithParams(Dict @params);

    public void OnFinish(object? result, object context)
    {
        ValidationException.CheckCondition(
            result != null,
            "Actions must return a result via built-in functions: success(), "
                + "error(), noop() return, but '{0}' returned: None", Action.GetName());
        ValidationException.CheckCondition(
            result is ActionResult,
            "Actions must return a result via built-in functions: success(), "
                + "error(), noop() return, but '{0}' returned: {1}", Action.GetName(), result!);
        _actionResult = (ActionResult)result!;
        switch (_actionResult.GetResult())
        {
            case ActionResult.Result.Error:
                Console.ErrorFmt(
                    "Action '{0}' returned error: {1}", Action.GetName(), _actionResult.GetMsg()!);
                break;
            case ActionResult.Result.NoOp:
                Console.InfoFmt(
                    "Action '{0}' returned noop: {1}", Action.GetName(), _actionResult.GetMsg()!);
                break;
            case ActionResult.Result.Success:
                Console.InfoFmt("Action '{0}' returned success", Action.GetName());
                break;
        }

        // Populate effects registered in the action context. This is required because StarlarkAction
        // makes a copy of the context to inject the parameters, but that instance is not visible from
        // the caller.
        if (context is ActionContext<T> other)
        {
            NewDestinationEffects.AddRange(other.NewDestinationEffects);
        }
    }

    public ActionResult? GetActionResult() => _actionResult;
}
