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

package com.google.copybara.feedback;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.DestinationEffect;
import com.google.copybara.Endpoint;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.Revision;
import com.google.copybara.SkylarkContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.SkylarkConsole;
import com.google.devtools.build.lib.syntax.Dict;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Sequence;
import com.google.devtools.build.lib.syntax.Starlark;
import com.google.devtools.build.lib.syntax.StarlarkList;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import java.util.Map;
import java.util.stream.Collectors;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkDocumentationCategory;
import net.starlark.java.annot.StarlarkMethod;

/** Skylark context for 'after migration' hooks. */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "feedback.finish_hook_context",
    category = StarlarkDocumentationCategory.BUILTIN,
    doc =
        "Gives access to the feedback migration information and utilities. This context is a "
            + "concrete implementation for 'after_migration' hooks.")
public class FinishHookContext extends FeedbackContext implements StarlarkValue {

  private final LazyResourceLoader<Endpoint> origin;
  private final LazyResourceLoader<Endpoint> destination;
  private final SkylarkRevision resolvedRevision;
  private final ImmutableList<DestinationEffect> destinationEffects;

  public FinishHookContext(
      Action action,
      LazyResourceLoader<Endpoint> origin,
      LazyResourceLoader<Endpoint> destination,
      ImmutableList<DestinationEffect> destinationEffects,
      Revision resolvedRevision,
      SkylarkConsole console) {
    this(
        action,
        origin,
        destination,
        destinationEffects,
        console,
        Dict.empty(),
        new SkylarkRevision(resolvedRevision));
  }

  private FinishHookContext(
      Action currentAction,
      LazyResourceLoader<Endpoint> origin,
      LazyResourceLoader<Endpoint> destination,
      ImmutableList<DestinationEffect> destinationEffects,
      SkylarkConsole console,
      Dict<?, ?> params,
      SkylarkRevision resolvedRevision) {
    super(currentAction, console, params);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.destinationEffects = Preconditions.checkNotNull(destinationEffects);
    this.resolvedRevision = resolvedRevision;
  }

  @Override
  public Endpoint getOrigin() throws EvalException {
    try {
      return origin.load(console);
    } catch (RepoException | ValidationException e) {
      throw new EvalException(/*location=*/ null, e);
    }
  }

  @Override
  public Endpoint getDestination() throws EvalException {
    try {
      return destination.load(console);
    } catch (RepoException | ValidationException e) {
      throw new EvalException(/*location=*/ null, e);
    }
  }

  @StarlarkMethod(
      name = "effects",
      doc = "The list of effects that happened in the destination",
      structField = true)
  public Sequence<DestinationEffect> getChanges() {
    return StarlarkList.immutableCopyOf(destinationEffects);
  }

  @StarlarkMethod(name = "revision", doc = "Get the requested/resolved revision",
      structField = true)
  public SkylarkRevision getRevision() {
    return resolvedRevision;
  }

  @Override
  public FinishHookContext withParams(Dict<?, ?> params) {
    return new FinishHookContext(
        currentAction, origin, destination, destinationEffects, console, params, resolvedRevision);
  }

  @Override
  public void onFinish(Object result, SkylarkContext<?> actionContext) throws ValidationException {
    checkCondition(
        result == null || result.equals(Starlark.NONE),
        "Finish hook '%s' cannot return any result but returned: %s",
        currentAction.getName(),
        result);
    // Populate effects registered in the action context. This is required because SkylarkAction
    // makes a copy of the context to inject the parameters, but that instance is not visible from
    // the caller
    this.newDestinationEffects.addAll(
        ((FinishHookContext) actionContext).getNewDestinationEffects());
  }

  @StarlarkBuiltin(
      name = "feedback.revision_context",
      category = StarlarkDocumentationCategory.BUILTIN,
      doc = "Information about the revision request/resolved for the migration")
  private static class SkylarkRevision implements StarlarkValue {

    private final Revision revision;

    SkylarkRevision(Revision revision) {
      this.revision = Preconditions.checkNotNull(revision);
    }

    @StarlarkMethod(
        name = "labels",
        doc = "A dictionary with the labels detected for the" + " requested/resolved revision.",
        structField = true)
    public Dict<String, Sequence<String>> getLabels() {
      return Dict.copyOf(
          /*mu=*/ null,
          revision.associatedLabels().asMap().entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey, e -> StarlarkList.immutableCopyOf(e.getValue()))));
    }
  }
}
