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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static com.google.copybara.config.SkylarkUtil.convertStringList;
import static com.google.copybara.config.SkylarkUtil.stringToEnum;
import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.action.Action;
import com.google.copybara.action.ActionContext;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitRepository.BranchCmd;
import com.google.copybara.git.GitRepository.CherryPickCmd;
import com.google.copybara.git.GitRepository.RebaseCmd;
import com.google.copybara.transform.SkylarkConsole;
import com.google.copybara.util.DirFactory;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/**
 * Expose methods to `git.mirror` actions to perform operations over git repositories
 */
@StarlarkBuiltin(
    name = "git.mirrorContext",
    doc = "Expose methods to `git.mirror` actions to perform operations over git repositories")
public class GitMirrorContext extends ActionContext<GitMirrorContext> implements StarlarkValue {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private boolean force;
  private GitRepository repo;
  private DirFactory dirFactory;
  private List<String> sourceRefs;
  private List<Refspec> refspecs;
  private String originUrl;
  private String destinationUrl;
  private final GitOptions gitOptions;

  GitMirrorContext(Action currentAction, SkylarkConsole console, List<String> sourceRefs,
      List<Refspec> refspecs, String originUrl, String destinationUrl, boolean force,
      GitRepository repo, DirFactory dirFactory, Dict<?, ?> params, GitOptions gitOptions) {
    super(currentAction, console, ImmutableMap.of(), params);
    this.sourceRefs = sourceRefs;
    this.refspecs = checkNotNull(refspecs);
    this.originUrl = originUrl;
    this.destinationUrl = destinationUrl;
    this.force = force;
    this.repo = repo;
    this.dirFactory = dirFactory;
    this.gitOptions = gitOptions;
  }

  @Override
  public GitMirrorContext withParams(Dict<?, ?> params) {
    return new GitMirrorContext(
        action, console, sourceRefs, refspecs, originUrl, destinationUrl, force, repo,
        dirFactory, params, gitOptions);
  }

  @StarlarkMethod(name = "console", doc = "Get an instance of the console to report errors or"
      + " warnings", structField = true)
  @Override
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
      doc =
          "Fetch from the origin a list of refspecs. Note that fetch happens without" + " pruning.",
      parameters = {
        @Param(
            name = "refspec",
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            named = true),
        @Param(name = "prune", defaultValue = "True", named = true),
        @Param(
            name = "depth",
            defaultValue = "None",
            doc =
                "Sets number of commits to fetch. Setting to None (the default) means no limit to"
                    + " that number.",
            allowedTypes = {
              @ParamType(type = StarlarkInt.class),
              @ParamType(type = NoneType.class),
            },
            named = true),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            doc =
                "If true, partially fetch only the minimum needed (e.g. don't fetch blobs if not"
                    + " used)"),
      })
  public boolean originFetch(Sequence<?> refspec, boolean prune, Object depth, boolean partialFetch)
      throws ValidationException, RepoException, EvalException {
    ImmutableList<Refspec> refspecsToFetch =
        toRefSpec(Sequence.cast(refspec, String.class, "refspec"));
    validateFetch(refspecsToFetch, this.refspecs, "origin");
    StarlarkInt depthConverted = convertFromNoneable(depth, null);
    Optional<Integer> depthOptional =
        (depthConverted == null) ? Optional.empty() : Optional.of(depthConverted.toInt("depth"));
    try {
      repo.fetch(
          originUrl,
          prune,
          force,
          refspecsToFetch.stream().map(Refspec::toString).collect(toImmutableList()),
          partialFetch,
          depthOptional);
    } catch (CannotResolveRevisionException e) {
      return false;
    }
    return true;
  }

  @StarlarkMethod(
      name = "destination_fetch",
      doc =
          "Fetch from the destination a list of refspecs. Note that fetch happens without"
              + " pruning.",
      parameters = {
        @Param(
            name = "refspec",
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            named = true),
        @Param(name = "prune", defaultValue = "True", named = true),
        @Param(
            name = "depth",
            defaultValue = "None",
            doc =
                "Sets number of commits to fetch. Setting to None (the default) means no limit to"
                    + " that number.",
            allowedTypes = {
              @ParamType(type = StarlarkInt.class),
              @ParamType(type = NoneType.class),
            },
            named = true),
        @Param(
            name = "partial_fetch",
            defaultValue = "False",
            named = true,
            doc =
                "If true, partially fetch only the minimum needed (e.g. don't fetch blobs if not"
                    + " used)"),
      })
  public boolean destinationFetch(
      Sequence<?> refspec, boolean prune, Object depth, boolean partialFetch)
      throws ValidationException, RepoException, EvalException {
    ImmutableList<Refspec> refspecsToFetch =
        toRefSpec(Sequence.cast(refspec, String.class, "refspec"));
    validateFetch(
        refspecsToFetch,
        refspecs.stream().map(Refspec::invert).collect(toImmutableList()),
        "destination");
    StarlarkInt depthConverted = convertFromNoneable(depth, null);
    Optional<Integer> depthOptional =
        (depthConverted == null) ? Optional.empty() : Optional.of(depthConverted.toInt(""));
    try {
      repo.fetch(
          destinationUrl,
          prune,
          force,
          refspecsToFetch.stream().map(Refspec::toString).collect(toImmutableList()),
          partialFetch,
          depthOptional);
    } catch (CannotResolveRevisionException e) {
      return false;
    }
    return true;
  }

  @StarlarkMethod(
      name = "references",
      doc = "Return a map of reference -> sha-1 for local references matching the refspec or"
          + " all if no refspec is passed.",
      parameters = {
          @Param(name = "refspec", allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class)
          }, named = true, defaultValue = "[]")
      })
  public Dict<String, String> references(Sequence<?> refspec)
      throws ValidationException, EvalException {
    Predicate<String> filter = refspecFilter(convertStringList(refspec, "refspec"));
    try {
      return Dict.immutableCopyOf(
          repo.showRef().entrySet().stream()
              .filter(e -> filter.test(e.getKey()))
              .collect(ImmutableMap.toImmutableMap(Entry::getKey, v -> v.getValue().getSha1())));
    } catch (RepoException e) {
      throw new ValidationException("Cannot list references in the local repository", e);
    }
  }

  private Predicate<String> refspecFilter(Collection<String> refspec)
      throws ValidationException {
    if (refspec.isEmpty()) {
      return s -> true;
    }
    Predicate<String> filter = null;
    for (String r : refspec) {
      Refspec refSpec = repo.createRefSpec(r);
      filter = filter == null ? refSpec::matchesOrigin : filter.or(refSpec::matchesOrigin);
    }
    return filter;
  }

  @StarlarkMethod(
      name = "destination_push",
      doc = "Push to the destination a list of refspecs.",
      parameters = {
        @Param(
            name = "refspec",
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            named = true),
        @Param(name = "prune", defaultValue = "False", named = true),
        @Param(
            name = "push_options",
            doc = "Additional push options to use with destination push",
            defaultValue = "[]",
            named = true,
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            })
      })
  public void destinationPush(Sequence<?> refspec, boolean prune, Sequence<?> pushOptions)
      throws ValidationException, RepoException, EvalException {
    ImmutableList<Refspec> refspecsToPush =
        toRefSpec(Sequence.cast(refspec, String.class, "refspec"));
    ImmutableList<String> resolvedPushOptions =
        ImmutableList.<String>builder()
            .addAll(convertStringList(pushOptions, "push_options"))
            .addAll(gitOptions.gitPushOptions)
            .build();

    validatePush(refspecsToPush, refspecs, true);
    repo.runPush(
        repo.push()
            .prune(prune)
            .withRefspecs(destinationUrl, refspecsToPush)
            .withPushOptions(resolvedPushOptions));
  }

  private enum FastForwardMode {
    FF,
    FF_ONLY,
    NO_FF;

    public String toGitFlag() {
      switch (this) {
        case FF_ONLY:
          return "--ff-only";
        case NO_FF:
          return "--no-ff";
        case FF:
          return "--ff";
      }
      return "";
    }
  }

  @StarlarkMethod(
      name = "merge",
      doc = "Merge one or more commits into a local branch.",
      parameters = {
        @Param(name = "branch", named = true),
        @Param(
            name = "commits",
            allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
            named = true),
        @Param(name = "msg", named = true, defaultValue = "None"),
        @Param(
            name = "fast_forward",
            defaultValue = "\"FF\"",
            doc = "Valid values are FF (default), NO_FF, FF_ONLY.",
            named = true,
            allowedTypes = {@ParamType(type = String.class)})
      })
  public MergeResult merge(String branch, Sequence<?> commits, Object msg, String fastForwardOption)
      throws RepoException, ValidationException, EvalException, IOException {
    checkCondition(!commits.isEmpty(), "At least one commit should be passed to merge");
    GitRepository withWorktree =
        prepareWorktreeForMerge(
            branch,
            String.format(
                "Cannot merge commits %s into"
                    + " branch %s because of failure during merge checkout",
                commits, branch));

    String strMsg = SkylarkUtil.convertFromNoneable(msg, null);

    FastForwardMode ffMode = stringToEnum("fast_forward", fastForwardOption, FastForwardMode.class);

    List<String> commitsList = convertStringList(commits, "commits");

    try {
      withWorktree
          .merge(branch, commitsList)
          .withFFMode(ffMode.toGitFlag())
          .withMessage(strMsg)
          .run(gitOptions.gitOptionsParams);
    } catch (RepoException e) {
      logger.atWarning().withCause(e).log(
          "Error running merge in action %s for branch %s and commits %s",
          getActionName(), branch, commits);
      return MergeResult.error(e.getMessage());
    }
    return MergeResult.success();
  }

  @StarlarkMethod(
      name = "rebase",
      doc = "Rebase one or more commits into a local branch.",
      parameters = {
          @Param(name = "upstream", named = true, doc = "upstream branch with new changes"),
          @Param(name = "branch", named = true, doc = "Current branch with specific commits that"
              + " we want to rebase in top of the new `upstream` changes"),
          @Param(name = "newBase", named = true, defaultValue = "None",
              doc = "Move the rebased changes to a new branch (--into parameter in git rebase)"),
          @Param(name = "conflict_advice", named = true, defaultValue = "None",
          doc = "Additional information on how to solve the issue in case if conflict")})
  public MergeResult rebase(String upstream, String branch, Object newBase, Object conflictAdvice)
      throws RepoException, ValidationException, EvalException, IOException {

    GitRepository withWorktree = prepareWorktreeForMerge(branch,
        String.format("Cannot rebase %s from branch %s because of failure during checkout",
            branch, upstream));
    try {
      RebaseCmd rebaseCmd = withWorktree.rebaseCmd(upstream);
      rebaseCmd
          .branch(branch)
          .into(SkylarkUtil.convertFromNoneable(newBase, null))
          .errorAdvice(SkylarkUtil.convertFromNoneable(conflictAdvice, null))
          .run();
    } catch (RebaseConflictException e) {
      logger.atWarning().withCause(e)
          .log("Error running merge in action %s for branch %s and upstream %s",
              getActionName(), branch, upstream);
      return MergeResult.error(e.getMessage());
    }
    return MergeResult.success();
  }

  private GitRepository prepareWorktreeForMerge(String branch, String errorMsg)
      throws IOException, ValidationException, RepoException {
    GitRepository withWorktree = this.repo.withWorkTree(dirFactory.newTempDir("mirror"));
    try {
      withWorktree.forceCheckout(branch);
    } catch (RepoException e) {
      throw new ValidationException(errorMsg, e);
    }
    // Clean everything before the merge
    withWorktree.simpleCommand("reset", "--hard");
    withWorktree.forceClean();
    return withWorktree;
  }

  @StarlarkMethod(
      name = "cherry_pick",
      doc = "Cherry-pick one or more commits to a branch",
      parameters = {
          @Param(name = "branch", named = true),
          @Param(name = "commits", allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class)
          }, named = true, doc = "Commits to cherry-pick. An expression like foo..bar can be"
              + " used to cherry-pick several commits. Note that 'HEAD' will refer to the"
              + " `branch` HEAD, since cherry-pick requires a checkout of the branch before"
              + " cherry-picking."),
          @Param(name = "add_commit_origin_info", named = true, defaultValue = "True",
              doc =
                  "Add information about the origin of the commit (sha-1) to the message of the new"
                      + "commit"),
          @Param(name = "merge_parent_number", named = true, defaultValue = "None",
              doc = "Specify the parent number for cherry-picking merge commits"),
          @Param(name = "allow_empty", named = true, defaultValue = "False",
              doc = "Allow empty commits (noop commits)"),
          @Param(name = "fast_forward", named = true, defaultValue = "False",
              doc = "Fast-forward commits if possible"),
      })
  public MergeResult cherryPick(String branch, Sequence<?> commits, Boolean addCommitOriginInfo,
      Object mergeParentNumber, Boolean allowEmpty, Boolean fastForward)
      throws RepoException, ValidationException, EvalException, IOException {
    checkCondition(!commits.isEmpty(), "At least one commit should be passed to merge");
    GitRepository withWorktree = forceCheckout(branch,
        String.format(
            "Cannot cherry-pick commits %s into branch %s because of failure during merge checkout",
            commits, branch));

    CherryPickCmd cmd = withWorktree.cherryPick(convertStringList(commits, "commits"))
        .addCommitOriginInfo(addCommitOriginInfo)
        .allowEmpty(allowEmpty)
        .fastForward(fastForward);
    Integer mergeParent = SkylarkUtil.convertFromNoneable(mergeParentNumber, null);
    if (mergeParent != null) {
      cmd = cmd.parentNumber(mergeParent);
    }
    try {
      cmd.run();
    } catch (RepoException e) {
      logger.atWarning().withCause(e)
          .log("Error running cherry-pick in action %s for branch %s and commits %s",
              getActionName(), branch, commits);
      try {
        repo.abortCherryPick();
      } catch (RepoException ex) {
        logger.atWarning().withCause(ex).log("cherry-pick --abort failed.");
      }

      return MergeResult.error(e.getMessage());
    }
    return MergeResult.success();
  }

  private GitRepository forceCheckout(String branch, String errorMsg)
      throws IOException, ValidationException, RepoException {
    GitRepository withWorktree = this.repo.withWorkTree(dirFactory.newTempDir("mirror"));
    try {
      withWorktree.forceCheckout(branch);
    } catch (RepoException e) {
      throw new ValidationException(errorMsg, e);
    }
    // Clean everything before the merge
    withWorktree.simpleCommand("reset", "--hard");
    withWorktree.forceClean();
    return withWorktree;
  }

  @StarlarkMethod(
      name = "create_branch",
      doc = "Merge one or more commits into a local branch.",
      parameters = {
          @Param(name = "name", named = true),
          @Param(name = "starting_point", named = true, defaultValue = "None"),
      })
  public void createBranch(String branch, Object startingPoint)
      throws RepoException {
    BranchCmd cmd = repo.branch(branch);
    String starting = SkylarkUtil.convertFromNoneable(startingPoint, null);
    if (starting != null) {
      cmd = cmd.withStartPoint(starting);
    }
    cmd.run();
  }

  private ImmutableList<Refspec> toRefSpec(Collection<String> strRefspecs)
      throws ValidationException {
    ImmutableList.Builder<Refspec> result = ImmutableList.builder();
    for (String s : strRefspecs) {
      result.add(repo.createRefSpec(s));
    }
    return result.build();
  }

  private void validateFetch(List<Refspec> refspecs, List<Refspec> allowedRefspecs,
      String where)
      throws ValidationException {
    ImmutableList<Refspec> notAllowed =
        refspecs.stream()
            .filter(r -> allowedRefspecs.stream().noneMatch(a -> a.matchesOrigin(r.getOrigin())))
            .collect(toImmutableList());

    checkCondition(notAllowed.isEmpty(),
        "Action tried to fetch from %s one or more refspec not covered by git.mirror"
            + " refspec: %s ", where, notAllowed);
  }

  private void validatePush(List<Refspec> refspecs, List<Refspec> allowedRefspecs,
      boolean forPush) throws ValidationException {
    ImmutableList<Refspec> notAllowed =
        refspecs.stream()
            .filter(
                r ->
                    allowedRefspecs.stream()
                        .noneMatch(a -> a.invert().matchesOrigin(r.getDestination())))
            .collect(toImmutableList());

    checkCondition(notAllowed.isEmpty(),
        "Action tried to %s destination one or more refspec not covered by git.mirror"
            + " refspec: %s ", (forPush ? "push to" : "fetch from"), notAllowed);
  }

}
