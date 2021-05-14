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

package com.google.copybara.git;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.action.Action;
import com.google.copybara.action.ActionContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.SkylarkConsole;
import com.google.copybara.util.console.Console;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class GitMirrorContext extends ActionContext<GitMirrorContext> implements StarlarkValue {

  private boolean force;
  private GitRepository repo;
  private List<String> sourceRefs;
  private List<Refspec> refspecs;
  private String originUrl;
  private String destinationUrl;

  public GitMirrorContext(Action currentAction, SkylarkConsole console, List<String> sourceRefs,
      List<Refspec> refspecs, String originUrl, String destinationUrl, boolean force,
      GitRepository repo, Dict<?, ?> params) {
    super(currentAction, console, ImmutableMap.of(), params);
    this.sourceRefs = sourceRefs;
    this.refspecs = checkNotNull(refspecs);
    this.originUrl = originUrl;
    this.destinationUrl = destinationUrl;
    this.force = force;
    this.repo = repo;
  }

  @Override
  public GitMirrorContext withParams(Dict<?, ?> params) {
    return new GitMirrorContext(
        action, console, sourceRefs, refspecs, originUrl, destinationUrl, force, repo, params);
  }

  @StarlarkMethod(name = "console", doc = "Get an instance of the console to report errors or"
      + " warnings", structField = true)
  public Console getConsole() {
    return console;
  }

  @StarlarkMethod(
      name = "refs",
      doc =
          "A list containing string representations of the entities " + "that triggered the event",
      structField = true)
  public Sequence<?> getRefs() {
    return StarlarkList.immutableCopyOf(sourceRefs);
  }

  @StarlarkMethod(
      name = "origin_fetch",
      doc = "Fetch from the origin a list of refspecs. Note that fetch happens without"
          + " pruning.",
      parameters = {
          @Param(name = "refspec", allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class)
          }, named = true),
          @Param(name = "prune", defaultValue = "True", named = true)
      })
  public void originFetch(Sequence<?> refspec, boolean prune)
      throws ValidationException, RepoException, EvalException {
    ImmutableList<Refspec> refspecsToFetch =
        toRefSpec(Sequence.cast(refspec, String.class, "refspec"));
    validateOriginFetch(refspecsToFetch, this.refspecs);

    repo.fetch(originUrl, prune, force,
            refspecsToFetch.stream()
                .map(Refspec::toString)
                .collect(Collectors.toList()),
            false);
  }

  @StarlarkMethod(
      name = "destination_fetch",
      doc = "Fetch from the destination a list of refspecs. Note that fetch happens without"
          + " pruning.",
      parameters = {
          @Param(name = "refspec", allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class)
          }, named = true),
          @Param(name = "prune", defaultValue = "True", named = true)
      })
  public void destinationFetch(Sequence<?> refspec, boolean prune)
      throws ValidationException, RepoException, EvalException {
    ImmutableList<Refspec> refspecsToFetch =
        toRefSpec(Sequence.cast(refspec, String.class, "refspec"));
    validateDestinationRefspec(refspecsToFetch, refspecs, false);

    repo.fetch(destinationUrl, prune, force,
        refspecsToFetch.stream()
            .map(Refspec::toString)
            .collect(Collectors.toList()),
        false);
  }

  @StarlarkMethod(
      name = "destination_push",
      doc = "Push to the destination a list of refspecs.",
      parameters = {
          @Param(name = "refspec", allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class)
          }, named = true),
          @Param(name = "prune", defaultValue = "False", named = true)
      })

  public void destinationPush(Sequence<?> refspec, boolean prune)
      throws ValidationException, RepoException, EvalException {
    ImmutableList<Refspec> refspecsToPush =
        toRefSpec(Sequence.cast(refspec, String.class, "refspec"));
    validateDestinationRefspec(refspecsToPush, refspecs, true);
    repo.runPush(repo.push().prune(prune).withRefspecs(destinationUrl, refspecsToPush));
  }

  private ImmutableList<Refspec> toRefSpec(Collection<String> strRefspecs)
      throws ValidationException {
    ImmutableList.Builder<Refspec> result = ImmutableList.builder();
    for (String s : strRefspecs) {
      result.add(repo.createRefSpec(s));
    }
    return result.build();
  }

  private void validateOriginFetch(List<Refspec> refspecs, List<Refspec> allowedRefspecs)
      throws ValidationException {
    List<Refspec> notAllowed = refspecs.stream()
        .filter(
            r -> allowedRefspecs.stream().noneMatch(a -> a.matchesOrigin(r.getOrigin())))
        .collect(Collectors.toList());

    checkCondition(notAllowed.isEmpty(),
        "Action tried to fetch from origin one or more refspec not covered by git.mirror"
            + " refspec: %s ", notAllowed);
  }

  private void validateDestinationRefspec(List<Refspec> refspecs, List<Refspec> allowedRefspecs,
      boolean forPush) throws ValidationException {
    List<Refspec> notAllowed = refspecs.stream()
        .filter(
            r -> allowedRefspecs.stream().noneMatch(a -> a.invert()
                .matchesOrigin(r.getDestination())))
        .collect(Collectors.toList());

    checkCondition(notAllowed.isEmpty(),
        "Action tried to %s destination one or more refspec not covered by git.mirror"
            + " refspec: %s ", (forPush ? "push to" : "fetch from"), notAllowed);
  }
}
