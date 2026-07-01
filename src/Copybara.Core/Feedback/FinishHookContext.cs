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

using System.Collections.Immutable;
using Copybara;
using Copybara.Action;
using Copybara.Common;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Transform;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Feedback;

/// <summary>Skylark context for 'after migration' hooks.</summary>
[StarlarkBuiltin(
    "feedback.finish_hook_context",
    Doc =
        "Gives access to the feedback migration information and utilities. This context is a "
        + "concrete implementation for 'after_migration' hooks.")]
public class FinishHookContext : ActionContext<FinishHookContext>
{
    private readonly LazyResourceLoader<IEndpoint> _origin;
    private readonly LazyResourceLoader<IEndpoint> _destination;
    private readonly SkylarkRevision _resolvedRevision;
    private readonly ImmutableArray<DestinationEffect> _destinationEffects;

    public FinishHookContext(
        IAction action,
        LazyResourceLoader<IEndpoint> origin,
        LazyResourceLoader<IEndpoint> destination,
        IReadOnlyList<DestinationEffect> destinationEffects,
        IReadOnlyDictionary<string, string> labels,
        IRevision resolvedRevision,
        SkylarkConsole console)
        : this(
            action,
            origin,
            destination,
            destinationEffects,
            labels,
            console,
            Dict.Empty(),
            new SkylarkRevision(resolvedRevision))
    {
    }

    private FinishHookContext(
        IAction currentAction,
        LazyResourceLoader<IEndpoint> origin,
        LazyResourceLoader<IEndpoint> destination,
        IReadOnlyList<DestinationEffect> destinationEffects,
        IReadOnlyDictionary<string, string> labels,
        SkylarkConsole console,
        Dict @params,
        SkylarkRevision resolvedRevision)
        : base(currentAction, console, labels, @params)
    {
        _origin = Preconditions.CheckNotNull(origin);
        _destination = Preconditions.CheckNotNull(destination);
        _destinationEffects = Preconditions.CheckNotNull(destinationEffects).ToImmutableArray();
        _resolvedRevision = resolvedRevision;
    }

    [StarlarkMethod(
        "origin",
        Doc = "An object representing the origin. Can be used to query about the ref or modifying"
            + " the origin state",
        StructField = true)]
    public IEndpoint GetOrigin()
    {
        try
        {
            return _origin.Load(Console);
        }
        catch (Exception e) when (e is RepoException or ValidationException)
        {
            throw new EvalException(e.Message, e);
        }
    }

    [StarlarkMethod(
        "destination",
        Doc = "An object representing the destination. Can be used to query or modify the"
            + " destination state",
        StructField = true)]
    public IEndpoint GetDestination()
    {
        try
        {
            return _destination.Load(Console);
        }
        catch (Exception e) when (e is RepoException or ValidationException)
        {
            throw new EvalException(e.Message, e);
        }
    }

    [StarlarkMethod(
        "effects",
        Doc = "The list of effects that happened in the destination",
        StructField = true)]
    public StarlarkList GetChanges() =>
        StarlarkList.ImmutableCopyOf(_destinationEffects.Cast<object?>());

    [StarlarkMethod(
        "revision",
        Doc = "Get the requested/resolved revision",
        StructField = true)]
    public SkylarkRevision GetRevision() => _resolvedRevision;

    public override FinishHookContext WithParams(Dict @params) =>
        new(
            Action, _origin, _destination, _destinationEffects, Labels, Console, @params,
            _resolvedRevision);

    // Java overrides ActionContext.onFinish to assert the finish hook returned NONE. The ported base
    // ActionContext.OnFinish is non-virtual, so this cannot override it and is hidden via `new`.
    // TODO(port): make ActionContext.OnFinish virtual so finish-hook dispatch reaches this method
    // through ISkylarkContext<FinishHookContext>; until then callers with a static FinishHookContext
    // reference get the NONE-result check, and the base still handles effect population.
    public new void OnFinish(object? result, object context)
    {
        ValidationException.CheckCondition(
            result == null || Equals(result, StarlarkRt.None),
            "Finish hook '{0}' cannot return any result but returned: {1}",
            Action.GetName(),
            result!);
        // Populate effects registered in the action context. This is required because StarlarkAction
        // makes a copy of the context to inject the parameters, but that instance is not visible from
        // the caller.
        if (context is FinishHookContext other)
        {
            NewDestinationEffects.AddRange(other.GetNewDestinationEffects());
        }
    }

    [StarlarkBuiltin(
        "feedback.revision_context",
        Doc = "Information about the revision request/resolved for the migration")]
    public sealed class SkylarkRevision : IStarlarkValue
    {
        private readonly IRevision _revision;

        internal SkylarkRevision(IRevision revision)
        {
            _revision = Preconditions.CheckNotNull(revision);
        }

        [StarlarkMethod(
            "labels",
            Doc = "A dictionary with the labels detected for the requested/resolved revision.",
            StructField = true)]
        public Dict GetLabels() =>
            Dict.ImmutableCopyOf(
                _revision.AssociatedLabels().AsMap().Select(
                    e => new KeyValuePair<object?, object?>(
                        e.Key, StarlarkList.ImmutableCopyOf(e.Value.Cast<object?>()))));

        [StarlarkMethod(
            "fill_template",
            Doc = "Replaces variables in templates with the values from this revision.")]
        public string FillTemplate(
            [Param(Name = "template", Doc = "The template to use", Named = true)] string template) =>
            LabelFinder.MapLabels(label => _revision.AssociatedLabel(label), template);
    }
}
