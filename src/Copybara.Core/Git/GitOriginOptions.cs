/*
 * Copyright (C) 2016 Google Inc.
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

using Copybara.Approval;
using Copybara.Exceptions;
using Copybara.Util;

namespace Copybara.Git;

/// <summary>Options for <see cref="GitOrigin"/>. Port of <c>GitOriginOptions</c>.</summary>
public class GitOriginOptions : IOption
{
    [Flag(
        "--git-origin-checkout-hook",
        "A command to be executed when a checkout happens for a git origin. DON'T USE IT. The only"
            + " intention is to run tools that gather dependencies after the checkout.",
        Hidden = true)]
    internal string? OriginCheckoutHook { get; set; }

    [Flag(
        "--git-origin-rebase-ref",
        "When importing a change from a Git origin ref, it will be rebased to this ref, if set. A"
            + " common use case: importing a Github PR, rebase it to the main branch (usually"
            + " 'master'). Note that, if the repo uses submodules, they won't be rebased.")]
    internal string? OriginRebaseRef { get; set; }

    [Flag(
        "--git-origin-describe-default",
        "The default for git describe in git.*origin.",
        Arity = 1,
        Hidden = true)]
    internal bool GitDescribeDefault { get; set; } = true;

    [Flag(
        "--nogit-origin-version-selector",
        "Disable the version selector for the migration. Only useful for forcing a migration to the"
            + " passed version in the CLI")]
    internal bool NoGitVersionSelector { get; set; }

    [Flag(
        "--git-origin-log-batch",
        "Read the origin git log in batches of n commits. Might be needed for large migrations"
            + " resulting in git logs of more than 1 GB.")]
    internal int GitOriginLogBatchSize { get; set; }

    [Flag(
        "--git-origin-non-linear-history",
        "Read the full git log and skip changes before the from ref rather than using a log path.",
        Arity = 1)]
    internal bool HistoryIsNonLinear { get; set; }

    [Flag(
        "--git-fuzzy-last-rev",
        "By default Copybara will try to migrate the revision listed as the version in the metadata"
            + " file from github. This flag tells Copybara to first find the git tag which most"
            + " closely matches the metadata version, and use that for the migration.",
        Arity = 1)]
    internal bool GitFuzzyLastRev { get; set; }

    public bool UseGitVersionSelector() => !NoGitVersionSelector;

    public bool UseGitFuzzyLastRev() => GitFuzzyLastRev;

    public IApprovalsProvider ApprovalsProvider { get; set; } = new NoneApprovedProvider();

    internal void MaybeRunCheckoutHook(string checkoutDir, GeneralOptions generalOptions)
    {
        if (string.IsNullOrEmpty(OriginCheckoutHook))
        {
            return;
        }
        var cmd = new Command(
            new[] { OriginCheckoutHook },
            generalOptions.GetEnvironment(),
            checkoutDir);
        try
        {
            new CommandRunner(cmd)
                .WithVerbose(generalOptions.IsVerbose())
                .Execute();
        }
        catch (CommandException e)
        {
            throw new RepoException(
                $"Error executing the git checkout hook: {OriginCheckoutHook}", e);
        }
    }
}
