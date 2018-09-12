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
import com.google.copybara.DestinationEffect.DestinationRef;
import com.google.copybara.DestinationEffect.OriginRef;
import com.google.copybara.Endpoint;
import com.google.copybara.Revision;
import com.google.copybara.SkylarkContext;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.SkylarkConsole;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gives access to the feedback migration information and utilities.
 * TODO(danielromero): Consider merging this with FeedbackContext to avoid duplication
 */
@SkylarkModule(name = "feedback.finish_hook_context",
    category = SkylarkModuleCategory.BUILTIN,
    doc = "Gives access to the feedback migration information and utilities.")
public class FinishHookContext implements SkylarkContext<FinishHookContext> {

  private final Action action;
  private final Endpoint origin;
  private final Endpoint destination;
  private final SkylarkRevision resolvedRevision;
  private final SkylarkConsole console;
  private final SkylarkDict params;
  private final ImmutableList<DestinationEffect> destinationEffects;
  private final List<DestinationEffect> newDestinationEffects = new ArrayList<>();

  public FinishHookContext(Action action, Endpoint origin, Endpoint destination,
      ImmutableList<DestinationEffect> destinationEffects,
      Revision resolvedRevision, SkylarkConsole console) {
    this(action, origin, destination, destinationEffects, console, SkylarkDict.empty(),
        new SkylarkRevision(resolvedRevision));
  }

  private FinishHookContext(Action action, Endpoint origin,
      Endpoint destination,
      ImmutableList<DestinationEffect> destinationEffects,
      SkylarkConsole console, SkylarkDict params, SkylarkRevision resolvedRevision) {
    this.action = Preconditions.checkNotNull(action);
    this.origin = Preconditions.checkNotNull(origin);
    this.destination = Preconditions.checkNotNull(destination);
    this.destinationEffects = Preconditions.checkNotNull(destinationEffects);
    this.resolvedRevision = resolvedRevision;
    this.console = Preconditions.checkNotNull(console);
    this.params = Preconditions.checkNotNull(params);
  }

  @SkylarkCallable(name = "origin", doc = "An object representing the origin. Can be used to"
      + " query about the state", structField = true)
  public Endpoint getOrigin() {
    return origin;
  }

  @SkylarkCallable(name = "destination", doc = "An object representing the destination. Can be used"
      + " to query or modify the destination state", structField = true)
  public Endpoint getDestination() {
    return destination;
  }

  @SkylarkCallable(name = "effects",
      doc = "The list of effects that happened in the destination", structField = true)
  public SkylarkList<DestinationEffect> getChanges() {
    return SkylarkList.createImmutable(destinationEffects);
  }

  @SkylarkCallable(name = "revision", doc = "Get the requested/resolved revision",
      structField = true)
  public SkylarkRevision getRevision() {
    return resolvedRevision;
  }

  @SkylarkCallable(name = "console", doc = "Get an instance of the console to report errors or"
      + " warnings", structField = true)
  public SkylarkConsole getConsole() {
    return console;
  }


  @SkylarkCallable(name = "params", doc = "Parameters for the function if created with"
      + " core.dynamic_feedback", structField = true)
  public SkylarkDict getParams() {
    return params;
  }

  @Override
  public FinishHookContext withParams(SkylarkDict<?, ?> params) {
    return new FinishHookContext(
        action, origin, destination, destinationEffects, console, params, resolvedRevision);
  }

  @SkylarkCallable(
      name = "record_effect",
      doc = "Records an effect of the current action.",
      parameters = {
          @Param(name = "summary", type = String.class, doc = "The summary of this effect"),
          @Param(
              name = "origin_refs",
              type = SkylarkList.class,
              generic1 = OriginRef.class,
              doc = "The origin refs"),
          @Param(name = "destination_ref", type = DestinationRef.class, doc = "The destination ref"),
          @Param(
              name = "errors",
              type = SkylarkList.class,
              generic1 = String.class,
              defaultValue = "[]",
              doc = "An optional list of errors"),
          @Param(
              name = "type",
              type = String.class,
              doc =
                  "The type of migration effect:<br>"
                      + "<ul>"
                      + "<li><b>'CREATED'</b>: A new review or change was created.</li>"
                      + "<li><b>'UPDATED'</b>: An existing review or change was updated.</li>"
                      + "<li><b>'NOOP'</b>: The change was a noop.</li>"
                      + "<li><b>'INSUFFICIENT_APPROVALS'</b>: The effect couldn't happen because "
                      + "the change doesn't have enough approvals.</li>"
                      + "<li><b>'ERROR'</b>: A user attributable error happened that prevented "
                      + "the destination from creating/updating the change. "
                      + "</ul>",
              defaultValue = "\"UPDATED\""
          )
      })
  public void recordEffect(
      String summary, List<OriginRef> originRefs, DestinationRef destinationRef,
      List<String> errors, String typeStr) throws EvalException  {
    DestinationEffect.Type type =
        SkylarkUtil.stringToEnum(null, "type", typeStr, DestinationEffect.Type.class);
    newDestinationEffects.add(
        new DestinationEffect(type, summary, originRefs, destinationRef, errors));
  }

  /**
   * Return the new {@link DestinationEffect}s created by this context.
   */
  public List<DestinationEffect> getNewDestinationEffects() {
    return ImmutableList.copyOf(newDestinationEffects);
  }

  @Override
  public void onFinish(Object result, SkylarkContext actionContext) throws ValidationException {
    checkCondition(
        result == null || result.equals(Runtime.NONE),
        "Finish hook '%s' cannot return any result but returned: %s", action.getName(), result);
    // Populate effects registered in the action context. This is required because SkylarkAction
    // makes a copy of the context to inject the parameters, but that instance is not visible from
    // the caller
    this.newDestinationEffects.addAll(
        ((FinishHookContext) actionContext).getNewDestinationEffects());
  }

  @SkylarkModule(name = "feedback.revision_context",
      category = SkylarkModuleCategory.BUILTIN,
      doc = "Information about the revision request/resolved for the migration")
  private static class SkylarkRevision {

    private final Revision revision;

    SkylarkRevision(Revision revision) {
      this.revision = Preconditions.checkNotNull(revision);
    }

    @SkylarkCallable(name = "labels", doc = "A dictionary with the labels detected for the"
        + " requested/resolved revision.", structField = true)
    public SkylarkDict<String, SkylarkList<String>> getLabels() {
      return SkylarkDict.copyOf(/* env= */ null,
          revision.associatedLabels().asMap().entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey,
                  e -> SkylarkList.createImmutable(e.getValue()))));
    }
  }
}
