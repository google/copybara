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
using Copybara.Action;
using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Profiler;
using Copybara.Transform;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// Expose methods to <c>git.mirror</c> actions to perform operations over git repositories. Port of
/// <c>com.google.copybara.git.GitMirrorContext</c>.
/// </summary>
[StarlarkBuiltin(
    "git.mirrorContext",
    Doc = "Expose methods to `git.mirror` actions to perform operations over git repositories")]
public class GitMirrorContext : ActionContext<GitMirrorContext>, IStarlarkValue
{
    private readonly bool _force;
    private readonly GitRepository _repo;
    private readonly DirFactory _dirFactory;
    private readonly IReadOnlyList<string> _sourceRefs;
    private readonly IReadOnlyList<Refspec> _refspecs;
    private readonly string _originUrl;
    private readonly string _destinationUrl;
    private readonly GitOptions _gitOptions;
    private readonly Profiler.Profiler _profiler;
    private readonly LazyResourceLoader<IEndpointProvider>? _originApiEndpointProvider;
    private readonly LazyResourceLoader<IEndpointProvider>? _destinationApiEndpointProvider;

    internal GitMirrorContext(
        IAction currentAction,
        SkylarkConsole console,
        Profiler.Profiler profiler,
        IReadOnlyList<string> sourceRefs,
        IReadOnlyList<Refspec> refspecs,
        string originUrl,
        string destinationUrl,
        bool force,
        GitRepository repo,
        DirFactory dirFactory,
        Dict @params,
        GitOptions gitOptions,
        LazyResourceLoader<IEndpointProvider>? originApiEndpointProvider,
        LazyResourceLoader<IEndpointProvider>? destinationApiEndpointProvider)
        : base(currentAction, console, ImmutableDictionary<string, string>.Empty, @params)
    {
        _sourceRefs = sourceRefs;
        _refspecs = Preconditions.CheckNotNull(refspecs);
        _originUrl = originUrl;
        _destinationUrl = destinationUrl;
        _force = force;
        _repo = repo;
        _dirFactory = dirFactory;
        _gitOptions = gitOptions;
        _profiler = Preconditions.CheckNotNull(profiler);
        _originApiEndpointProvider = originApiEndpointProvider;
        _destinationApiEndpointProvider = destinationApiEndpointProvider;
    }

    public override GitMirrorContext WithParams(Dict @params) =>
        new(
            Action,
            (SkylarkConsole)Console,
            _profiler,
            _sourceRefs,
            _refspecs,
            _originUrl,
            _destinationUrl,
            _force,
            _repo,
            _dirFactory,
            @params,
            _gitOptions,
            _originApiEndpointProvider,
            _destinationApiEndpointProvider);

    [StarlarkMethod(
        "origin_api",
        Doc = "Returns a handle to platform specific api, inferred from the origin url when"
            + " possible.",
        StructField = true,
        AllowReturnNones = true)]
    public IEndpoint? GetOriginApiEndpointProvider() =>
        _originApiEndpointProvider?.Load(Console).GetEndpoint();

    [StarlarkMethod(
        "destination_api",
        Doc = "Returns a handle to platform specific api, inferred from the destination url when"
            + " possible.",
        StructField = true,
        AllowReturnNones = true)]
    public IEndpoint? GetDestinationApiEndPointProvider() =>
        _destinationApiEndpointProvider?.Load(Console).GetEndpoint();

    [StarlarkMethod(
        "console",
        Doc = "Get an instance of the console to report errors or warnings",
        StructField = true)]
    public Console GetConsoleField() => Console;

    [StarlarkMethod(
        "refs",
        Doc = "A list containing string representations of the entities that triggered the event",
        StructField = true)]
    public StarlarkList GetRefs() => StarlarkList.ImmutableCopyOf(_sourceRefs.Cast<object?>());

    [StarlarkMethod(
        "origin_fetch",
        Doc = "Fetch from the origin a list of refspecs. Note that fetch happens without pruning.")]
    public bool OriginFetch(
        [Param(Name = "refspec", Named = true)] object refspec,
        [Param(Name = "prune", Named = true, DefaultValue = "True")] bool prune,
        [Param(Name = "depth", Named = true, DefaultValue = "None")] object depth,
        [Param(Name = "partial_fetch", Named = true, DefaultValue = "False")] bool partialFetch)
    {
        var refspecsToFetch = ToRefSpec(SkylarkUtil.ConvertStringList(refspec, "refspec"));
        ValidateFetch(refspecsToFetch, _refspecs, "origin");
        int? depthOptional = ConvertDepth(depth);
        using (_profiler.Start("origin_fetch"))
        {
            try
            {
                _repo.Fetch(
                    _originUrl,
                    prune,
                    _force,
                    refspecsToFetch.Select(r => r.ToString()).ToList(),
                    partialFetch,
                    depthOptional,
                    tags: false);
            }
            catch (CannotResolveRevisionException e)
            {
                Console.WarnFmt("Failed to complete origin_fetch with error '{0}'", e.Message);
                return false;
            }
        }
        return true;
    }

    [StarlarkMethod(
        "destination_fetch",
        Doc = "Fetch from the destination a list of refspecs. Note that fetch happens without"
            + " pruning.")]
    public bool DestinationFetch(
        [Param(Name = "refspec", Named = true)] object refspec,
        [Param(Name = "prune", Named = true, DefaultValue = "True")] bool prune,
        [Param(Name = "depth", Named = true, DefaultValue = "None")] object depth,
        [Param(Name = "partial_fetch", Named = true, DefaultValue = "False")] bool partialFetch)
    {
        var refspecsToFetch = ToRefSpec(SkylarkUtil.ConvertStringList(refspec, "refspec"));
        ValidateFetch(
            refspecsToFetch, _refspecs.Select(r => r.Invert()).ToList(), "destination");
        int? depthOptional = ConvertDepth(depth);
        try
        {
            _repo.Fetch(
                _destinationUrl,
                prune,
                _force,
                refspecsToFetch.Select(r => r.ToString()).ToList(),
                partialFetch,
                depthOptional,
                tags: false);
        }
        catch (CannotResolveRevisionException)
        {
            return false;
        }
        return true;
    }

    [StarlarkMethod(
        "references",
        Doc = "Return a map of reference -> sha-1 for local references matching the refspec or all"
            + " if no refspec is passed.")]
    public Dict References(
        [Param(Name = "refspec", Named = true, DefaultValue = "[]")] object refspec)
    {
        var filter = RefspecFilter(SkylarkUtil.ConvertStringList(refspec, "refspec"));
        try
        {
            var builder = Dict.NewBuilder();
            foreach (var entry in _repo.ShowRef())
            {
                if (filter(entry.Key))
                {
                    builder.Put(entry.Key, entry.Value.GetHash());
                }
            }
            return builder.Build(null);
        }
        catch (RepoException e)
        {
            throw new ValidationException("Cannot list references in the local repository", e);
        }
    }

    private Func<string, bool> RefspecFilter(IReadOnlyCollection<string> refspec)
    {
        if (refspec.Count == 0)
        {
            return _ => true;
        }
        Func<string, bool>? filter = null;
        foreach (var r in refspec)
        {
            Refspec refSpec = _repo.CreateRefSpec(r);
            Func<string, bool> next = refSpec.MatchesOrigin;
            filter = filter == null ? next : Or(filter, next);
        }
        return filter!;
    }

    private static Func<string, bool> Or(Func<string, bool> a, Func<string, bool> b) =>
        s => a(s) || b(s);

    [StarlarkMethod(
        "destination_push",
        Doc = "Push to the destination a list of refspecs.")]
    public void DestinationPush(
        [Param(Name = "refspec", Named = true)] object refspec,
        [Param(Name = "prune", Named = true, DefaultValue = "False")] bool prune,
        [Param(Name = "push_options", Named = true, DefaultValue = "[]")] object pushOptions)
    {
        var refspecsToPush = ToRefSpec(SkylarkUtil.ConvertStringList(refspec, "refspec"));
        var resolvedPushOptions =
            SkylarkUtil.ConvertStringList(pushOptions, "push_options")
                .Concat(_gitOptions.GitPushOptions)
                .ToImmutableArray();
        ValidatePush(refspecsToPush, _refspecs, forPush: true);
        using (_profiler.Start("destination_push"))
        {
            _repo.Push()
                .WithPrune(prune)
                .WithForce(_force)
                .WithRefspecs(_destinationUrl, refspecsToPush)
                .WithPushOptions(resolvedPushOptions)
                .Run();
        }
    }

    private enum FastForwardMode
    {
        FF,
        FF_ONLY,
        NO_FF,
    }

    private static string ToGitFlag(FastForwardMode mode) =>
        mode switch
        {
            FastForwardMode.FF_ONLY => "--ff-only",
            FastForwardMode.NO_FF => "--no-ff",
            FastForwardMode.FF => "--ff",
            _ => "",
        };

    [StarlarkMethod(
        "merge",
        Doc = "Merge one or more commits into a local branch.")]
    public MergeResult Merge(
        [Param(Name = "branch", Named = true)] string branch,
        [Param(Name = "commits", Named = true)] object commits,
        [Param(Name = "msg", Named = true, DefaultValue = "None")] object msg,
        [Param(Name = "fast_forward", Named = true, DefaultValue = "\"FF\"")] string fastForwardOption)
    {
        var commitsSeq = SkylarkUtil.ConvertStringList(commits, "commits");
        ValidationException.CheckCondition(
            commitsSeq.Count != 0, "At least one commit should be passed to merge");
        GitRepository withWorktree =
            PrepareWorktreeForMerge(
                branch,
                $"Cannot merge commits {StringifyList(commitsSeq)} into branch {branch} because of"
                    + " failure during merge checkout");

        string? strMsg = SkylarkUtil.ConvertFromNoneable<string>(msg, null);

        FastForwardMode ffMode =
            SkylarkUtil.StringToEnum<FastForwardMode>("fast_forward", fastForwardOption);

        try
        {
            withWorktree
                .Merge(branch, commitsSeq)
                .WithFFMode(ToGitFlag(ffMode))
                .WithMessage(strMsg ?? "")
                .Run(_gitOptions.GitOptionsParams);
        }
        catch (RepoException e)
        {
            return MergeResult.Error(e.Message);
        }
        return MergeResult.Success();
    }

    [StarlarkMethod(
        "rebase",
        Doc = "Rebase one or more commits into a local branch.")]
    public MergeResult Rebase(
        [Param(Name = "upstream", Named = true, Doc = "upstream branch with new changes")]
        string upstream,
        [Param(Name = "branch", Named = true, Doc = "Current branch with specific commits that we"
            + " want to rebase in top of the new `upstream` changes")]
        string branch,
        [Param(Name = "newBase", Named = true, DefaultValue = "None",
            Doc = "Move the rebased changes to a new branch (--into parameter in git rebase)")]
        object newBase,
        [Param(Name = "conflict_advice", Named = true, DefaultValue = "None",
            Doc = "Additional information on how to solve the issue in case if conflict")]
        object conflictAdvice)
    {
        GitRepository withWorktree =
            PrepareWorktreeForMerge(
                branch,
                $"Cannot rebase {branch} from branch {upstream} because of failure during checkout");
        try
        {
            GitRepository.RebaseCmd rebaseCmd = withWorktree.RebaseCmdFor(upstream);
            rebaseCmd = rebaseCmd.Branch(branch);
            string? into = SkylarkUtil.ConvertFromNoneable<string>(newBase, null);
            if (into != null)
            {
                rebaseCmd = rebaseCmd.Into(into);
            }
            string? advice = SkylarkUtil.ConvertFromNoneable<string>(conflictAdvice, null);
            if (advice != null)
            {
                rebaseCmd = rebaseCmd.ErrorAdvice(advice);
            }
            rebaseCmd.Run();
        }
        catch (RebaseConflictException e)
        {
            return MergeResult.Error(e.Message);
        }
        return MergeResult.Success();
    }

    private GitRepository PrepareWorktreeForMerge(string branch, string errorMsg)
    {
        GitRepository withWorktree = _repo.WithWorkTree(_dirFactory.NewTempDir("mirror"));
        try
        {
            withWorktree.ForceCheckout(branch);
        }
        catch (RepoException e)
        {
            throw new ValidationException(errorMsg, e);
        }
        withWorktree.SimpleCommand("reset", "--hard");
        withWorktree.ForceClean();
        return withWorktree;
    }

    [StarlarkMethod(
        "cherry_pick",
        Doc = "Cherry-pick one or more commits to a branch")]
    public MergeResult CherryPick(
        [Param(Name = "branch", Named = true)] string branch,
        [Param(Name = "commits", Named = true)] object commits,
        [Param(Name = "add_commit_origin_info", Named = true, DefaultValue = "True")]
        bool addCommitOriginInfo,
        [Param(Name = "merge_parent_number", Named = true, DefaultValue = "None")]
        object mergeParentNumber,
        [Param(Name = "allow_empty", Named = true, DefaultValue = "False")] bool allowEmpty,
        [Param(Name = "fast_forward", Named = true, DefaultValue = "False")] bool fastForward)
    {
        var commitsSeq = SkylarkUtil.ConvertStringList(commits, "commits");
        ValidationException.CheckCondition(
            commitsSeq.Count != 0, "At least one commit should be passed to merge");
        GitRepository withWorktree =
            ForceCheckout(
                branch,
                $"Cannot cherry-pick commits {StringifyList(commitsSeq)} into branch {branch}"
                    + " because of failure during merge checkout");

        GitRepository.CherryPickCmd cmd = withWorktree.CherryPick(commitsSeq)
            .AddCommitOriginInfo(addCommitOriginInfo)
            .AllowEmpty(allowEmpty)
            .FastForward(fastForward);
        int? mergeParent = SkylarkUtil.ConvertFromNoneable<int?>(mergeParentNumber, null);
        if (mergeParent != null)
        {
            cmd = cmd.ParentNumber(mergeParent.Value);
        }
        try
        {
            cmd.Run();
        }
        catch (RepoException e)
        {
            try
            {
                _repo.AbortCherryPick();
            }
            catch (RepoException)
            {
                // cherry-pick --abort failed.
            }
            return MergeResult.Error(e.Message);
        }
        return MergeResult.Success();
    }

    private GitRepository ForceCheckout(string branch, string errorMsg)
    {
        GitRepository withWorktree = _repo.WithWorkTree(_dirFactory.NewTempDir("mirror"));
        try
        {
            withWorktree.ForceCheckout(branch);
        }
        catch (RepoException e)
        {
            throw new ValidationException(errorMsg, e);
        }
        withWorktree.SimpleCommand("reset", "--hard");
        withWorktree.ForceClean();
        return withWorktree;
    }

    [StarlarkMethod(
        "create_branch",
        Doc = "Merge one or more commits into a local branch.")]
    public void CreateBranch(
        [Param(Name = "name", Named = true)] string branch,
        [Param(Name = "starting_point", Named = true, DefaultValue = "None")] object startingPoint)
    {
        GitRepository.BranchCmd cmd = _repo.Branch(branch);
        string? starting = SkylarkUtil.ConvertFromNoneable<string>(startingPoint, null);
        if (starting != null)
        {
            cmd = cmd.WithStartPoint(starting);
        }
        cmd.Run();
    }

    private IReadOnlyList<Refspec> ToRefSpec(IEnumerable<string> strRefspecs)
    {
        var result = ImmutableArray.CreateBuilder<Refspec>();
        foreach (var s in strRefspecs)
        {
            result.Add(_repo.CreateRefSpec(s));
        }
        return result.ToImmutable();
    }

    private static int? ConvertDepth(object depth)
    {
        var depthConverted = SkylarkUtil.ConvertFromNoneable<StarlarkInt>(depth, null);
        return depthConverted?.ToInt("depth");
    }

    private void ValidateFetch(
        IReadOnlyList<Refspec> refspecs, IReadOnlyList<Refspec> allowedRefspecs, string where)
    {
        var notAllowed =
            refspecs
                .Where(r => !allowedRefspecs.Any(a => a.MatchesOrigin(r.GetOrigin())))
                .ToList();

        ValidationException.CheckCondition(
            notAllowed.Count == 0,
            "Action tried to fetch from {0} one or more refspec not covered by git.mirror refspec:"
                + " {1} ",
            where,
            StringifyRefspecs(notAllowed));
    }

    private void ValidatePush(
        IReadOnlyList<Refspec> refspecs, IReadOnlyList<Refspec> allowedRefspecs, bool forPush)
    {
        var notAllowed =
            refspecs
                .Where(r => !allowedRefspecs.Any(a => a.Invert().MatchesOrigin(r.GetDestination())))
                .ToList();

        ValidationException.CheckCondition(
            notAllowed.Count == 0,
            "Action tried to {0} destination one or more refspec not covered by git.mirror refspec:"
                + " {1} ",
            forPush ? "push to" : "fetch from",
            StringifyRefspecs(notAllowed));
    }

    private static string StringifyRefspecs(IEnumerable<Refspec> refspecs) =>
        "[" + string.Join(", ", refspecs.Select(r => r.ToString())) + "]";

    private static string StringifyList(IEnumerable<string> list) =>
        "[" + string.Join(", ", list) + "]";
}
