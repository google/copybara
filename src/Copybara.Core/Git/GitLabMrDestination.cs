/*
 * Copyright (C) 2025 Google LLC
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
using Copybara.Checks;
using Copybara.Common;
using Copybara.Config;
using Copybara.Credentials;
using Copybara.Exceptions;
using Copybara.Git.GitLab;
using Copybara.Git.GitLab.Api;
using Copybara.Git.GitLab.Api.Entities;
using Copybara.Http.Auth;
using Copybara.Revision;
using Copybara.TemplateToken;
using Copybara.Util;

namespace Copybara.Git;

/// <summary>
/// A destination for creating/updating GitLab Merge Requests. Port of
/// <c>com.google.copybara.git.GitLabMrDestination</c>.
///
/// <para>It will either create new merge requests or update existing ones based on the source branch
/// name provided.</para>
/// </summary>
public sealed class GitLabMrDestination : IDestination<GitRevision>
{
    private readonly GitLabMrDestinationParams _params;
    private readonly CredentialFileHandler _credentialFileHandler;
    private readonly LazyResourceLoader<GitRepository> _localRepo;

    public GitLabMrDestination(GitLabMrDestinationParams @params)
    {
        _params = @params;
        _credentialFileHandler =
            @params.GitLabOptions.GetCredentialFileHandler(
                @params.RepoUrl, @params.UsernamePasswordIssuer);
        _localRepo = LazyResourceLoader.Memoized<GitRepository>(
            _ => @params.DestinationOptions.LocalGitRepo(
                @params.RepoUrl.ToString(), _credentialFileHandler));
    }

    public string GetType() => "git.gitlab_mr_destination";

    private IGitLabApiTransport GetGitLabApiTransport() =>
        GitLabOptions.GetApiTransport(
            _params.RepoUrl.ToString(),
            _params.GitLabOptions.GetHttpTransportSupplier()(),
            _params.GeneralOptions.GetConsole(),
            new BearerInterceptor(_params.UsernamePasswordIssuer.Password));

    public IDestination<GitRevision>.IWriter<GitRevision> NewWriter(WriterContext writerContext)
    {
        GitLabApi gitLabApi = _params.GitLabOptions.GetGitLabApi(GetGitLabApiTransport());
        string mrBranch =
            GetMergeRequestBranchName(
                writerContext.GetOriginalRevision(),
                writerContext.GetWorkflowName(),
                writerContext.GetWorkflowIdentityUser());

        GitLabMrWriteHook writeHook =
            new GitLabMrWriteHook.GitLabMrWriteHookParams(
                    _params.AllowEmptyDiff,
                    gitLabApi,
                    _params.RepoUrl,
                    mrBranch,
                    _params.GeneralOptions,
                    _params.PartialFetch,
                    _params.AllowEmptyDiffMergeStatuses)
                .CreateWriteHook();
        var state =
            new GitLabWriterState(
                _localRepo,
                $"copybara/push-{Guid.NewGuid()}{(writerContext.IsDryRun() ? "-dryrun" : "")}");

        Project project;
        try
        {
            project =
                gitLabApi.GetProject(GitLabUtil.GetUrlEncodedProjectPath(_params.RepoUrl))
                    ?? throw new ValidationException(
                        "GitLab API did not return a Project response for " + _params.RepoUrl);
        }
        catch (GitLabApiException e)
        {
            throw new ValidationException(
                $"Failed to query for GitLab Project status. Cause: {e.Message}", e);
        }

        return new GitLabMrWriter.GitLabMrWriterParams(
                gitLabApi,
                _params.TitleTemplate,
                _params.BodyTemplate,
                _params.AssigneeTemplates,
                project,
                writerContext,
                writerContext.IsDryRun(),
                _params.RepoUrl,
                mrBranch,
                _params.TargetBranch,
                _params.PartialFetch,
                _params.GeneralOptions,
                _params.GitOptions,
                writeHook,
                state,
                _params.Integrates,
                _params.Checker,
                _params.DestinationOptions,
                _credentialFileHandler)
            .CreateWriter();
    }

    public string GetLabelNameWhenOrigin() => GitRepository.GitOriginRevId;

    public ImmutableListMultimap<string, string> Describe(Glob? destinationFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", GetType());
        builder.Put("url", _params.RepoUrl.ToString());
        builder.Put("title_template", _params.TitleTemplate ?? "");
        builder.Put("source_branch_template", _params.SourceBranchTemplate ?? "");
        builder.Put("target_branch", _params.TargetBranch);
        builder.Put("allow_empty_diff", _params.AllowEmptyDiff.ToString());
        builder.Put("partial_fetch", _params.PartialFetch.ToString());

        if (_params.Checker != null)
        {
            builder.Put("checker", _params.Checker.GetType().FullName ?? _params.Checker.GetType().Name);
        }
        if (destinationFiles != null
            && !destinationFiles.Roots().IsEmpty
            && !destinationFiles.Roots().Contains(""))
        {
            builder.PutAll("root", destinationFiles.Roots());
        }
        return builder.Build();
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials() =>
        GitDescribeCredentials.Convert(_params.UsernamePasswordIssuer.DescribeCredentials());

    private string GetMergeRequestBranchName(
        IRevision? revision, string workflowName, string workflowIdentityUser)
    {
        string contextReference =
            revision?.ContextReference()
                ?? throw new ValidationException(
                    GetType()
                        + " is incompatible with the current origin. Origin has to be able to provide"
                        + " the context reference.");

        if (_params.SourceBranchTemplate != null)
        {
            return GetCustomMrBranchName(_params.SourceBranchTemplate, contextReference);
        }

        return Identity.ComputeIdentity(
            "OriginGroupIdentity",
            contextReference,
            workflowName,
            _params.ConfigFile.GetIdentifier(),
            workflowIdentityUser);
    }

    private string GetCustomMrBranchName(string template, string contextReference)
    {
        var supportedLabels = new Dictionary<string, string>
        {
            ["CONTEXT_REFERENCE"] = contextReference,
        };

        try
        {
            return new LabelTemplate(template).Resolve(
                label => supportedLabels.TryGetValue(label, out var v) ? v : null);
        }
        catch (LabelTemplate.LabelNotFoundException e)
        {
            throw new ValidationException(
                "Can not resolve labels in the GitHub MR branch name template: " + e.Message, e);
        }
    }

    /// <summary>Writer state that also tracks the merge request number for the operation.</summary>
    public sealed class GitLabWriterState : GitDestination.WriterState
    {
        private long? _mergeRequestNumber;

        internal GitLabWriterState(
            LazyResourceLoader<GitRepository> localRepo, string localBranch)
            : base(localRepo, localBranch)
        {
        }

        internal void SetMrNumber(long mergeRequestNumber) =>
            _mergeRequestNumber = mergeRequestNumber;

        internal long? GetMergeRequestNumber() => _mergeRequestNumber;
    }

    /// <summary>Params for <see cref="GitLabMrDestination"/>.</summary>
    public sealed record GitLabMrDestinationParams(
        Uri RepoUrl,
        UsernamePasswordIssuer UsernamePasswordIssuer,
        string? TitleTemplate,
        string? BodyTemplate,
        IReadOnlyList<string> AssigneeTemplates,
        string? SourceBranchTemplate,
        string TargetBranch,
        ConfigFile ConfigFile,
        bool AllowEmptyDiff,
        IReadOnlySet<DetailedMergeStatus> AllowEmptyDiffMergeStatuses,
        GeneralOptions GeneralOptions,
        GitOptions GitOptions,
        GitLabOptions GitLabOptions,
        GitDestinationOptions DestinationOptions,
        bool PartialFetch,
        IEnumerable<GitIntegrateChanges> Integrates,
        IChecker? Checker)
    {
        /// <summary>Creates a new <see cref="GitLabMrDestination"/> using these parameters.</summary>
        public GitLabMrDestination CreateDestination() => new(this);
    }
}
