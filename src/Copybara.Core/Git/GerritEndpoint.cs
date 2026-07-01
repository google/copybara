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

using System.Text;
using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Git.GerritApi;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;
using GerritApiClient = Copybara.Git.GerritApi.GerritApi;

namespace Copybara.Git;

/// <summary>
/// Gerrit endpoint implementation for feedback migrations. Port of
/// <c>com.google.copybara.git.GerritEndpoint</c>.
/// </summary>
[StarlarkBuiltin(
    "gerrit_api_obj",
    Doc = "Gerrit API endpoint implementation for feedback migrations and after migration hooks.")]
public sealed class GerritEndpoint : IEndpoint, IStarlarkValue
{
    private const int GerritMaxMessageBytes = 16 << 10;
    private const string TruncatedPrefix = "(truncated): ";
    private static readonly int TruncatedPrefixBytes = Encoding.UTF8.GetByteCount(TruncatedPrefix);
    private static readonly int TruncatedMessageMaxBytes =
        TruncatedPrefixBytes + GerritMaxMessageBytes;

    private readonly LazyResourceLoader<GerritApiClient> _apiSupplier;
    private readonly string _url;
    private readonly Console _console;
    private readonly bool _allowSubmitChange;

    internal GerritEndpoint(
        LazyResourceLoader<GerritApiClient> apiSupplier,
        string url,
        Console console,
        bool allowSubmitChange)
    {
        _apiSupplier = Preconditions.CheckNotNull(apiSupplier);
        _url = Preconditions.CheckNotNull(url);
        _console = Preconditions.CheckNotNull(console);
        _allowSubmitChange = allowSubmitChange;
    }

    [StarlarkMethod("get_change", Doc = "Retrieve a Gerrit change.")]
    public ChangeInfo GetChange(
        [Param(Name = "id", Named = true, Doc = "The change id or change number.")] string id,
        [Param(
            Name = "include_results",
            Named = true,
            Doc = "What to include in the response.",
            Positional = false,
            DefaultValue = "['LABELS']")]
        ISequence<object?> includeResults)
    {
        try
        {
            ChangeInfo changeInfo = DoGetChange(id, GetIncludeResults(includeResults));
            ValidationException.CheckCondition(
                !changeInfo.IsMoreChanges(), "Pagination is not supported yet.");
            return changeInfo;
        }
        catch (GerritApiException re)
        {
            throw HandleGerritApiException(re, "get_change");
        }
        catch (Exception e) when (e is RepoException or ValidationException)
        {
            throw new EvalException("Error getting change: " + e.Message, e);
        }
    }

    [StarlarkMethod("get_actions", Doc = "Retrieve the actions of a Gerrit change.")]
    public IReadOnlyDictionary<string, ActionInfo> GetActions(
        [Param(Name = "id", Named = true, Doc = "The change id or change number.")] string id,
        [Param(Name = "revision", Named = true, Doc = "The revision of the change.")] string revision)
    {
        try
        {
            GerritApiClient gerritApi = _apiSupplier.Load(_console);
            return gerritApi.GetActionsAsync(id, revision).GetAwaiter().GetResult();
        }
        catch (GerritApiException re)
        {
            throw HandleGerritApiException(re, "get_actions");
        }
        catch (Exception e) when (e is RepoException or ValidationException)
        {
            throw new EvalException("Error getting actions: " + e.Message, e);
        }
    }

    private static IReadOnlySet<IncludeResult> GetIncludeResults(ISequence<object?> includeResults)
    {
        var enumResults = new HashSet<IncludeResult>();
        foreach (var result in includeResults)
        {
            enumResults.Add(
                SkylarkUtil.StringToEnum<IncludeResult>("include_results", (string)result!));
        }
        return enumResults;
    }

    private ChangeInfo DoGetChange(string changeId, IReadOnlySet<IncludeResult> includeResults)
    {
        GerritApiClient gerritApi = _apiSupplier.Load(_console);
        return gerritApi.GetChangeAsync(changeId, new GetChangeInput(includeResults))
            .GetAwaiter().GetResult();
    }

    private static ValidationException HandleGerritApiException(
        GerritApiException re, string methodName)
    {
        int responseCode = (int)re.GetResponseCode();
        if (responseCode is >= 400 and < 500)
        {
            return new ValidationException(
                $"Request error calling {methodName}. Gerrit returned a request error while"
                    + $" attempting to post a review:\n{re.Message}",
                re);
        }
        return new ValidationException("Error calling " + methodName, re);
    }

    [StarlarkMethod(
        "post_review",
        Doc =
            "Post a review to a Gerrit change for a particular revision. The review will be authored "
                + "by the user running the tool, or the role account if running in the service.\n")]
    public ReviewResult PostReview(
        [Param(Name = "change_id", Named = true, Doc = "The Gerrit change id.")] string changeId,
        [Param(
            Name = "revision_id",
            Named = true,
            Doc = "The revision for which the comment will be posted.")]
        string revisionId,
        [Param(Name = "review_input", Named = true, Doc = "The review to post to Gerrit.")]
        SetReviewInput reviewInput)
    {
        SetReviewInput finalReviewInput = MaybeTruncateMessage(reviewInput);
        try
        {
            GerritApiClient gerritApi = _apiSupplier.Load(_console);
            return gerritApi.SetReviewAsync(changeId, revisionId, finalReviewInput)
                .GetAwaiter().GetResult();
        }
        catch (GerritApiException re)
        {
            throw HandleGerritApiException(re, "post_review");
        }
        catch (Exception e) when (e is RepoException or ValidationException)
        {
            throw new EvalException("Error calling post_review: " + e.Message, e);
        }
    }

    private static SetReviewInput MaybeTruncateMessage(SetReviewInput reviewInput)
    {
        if (reviewInput.GetMessage() != null
            && Encoding.UTF8.GetByteCount(reviewInput.GetMessage()!) > GerritMaxMessageBytes)
        {
            string nonTruncatedMessage = TruncatedPrefix + reviewInput.GetMessage();
            // Assume each char is largest case scenario of 4 bytes.
            string truncatedMessage = nonTruncatedMessage.Substring(0, TruncatedMessageMaxBytes / 4);
            return SetReviewInput.Create(
                truncatedMessage,
                reviewInput.GetLabels(),
                reviewInput.GetTag(),
                reviewInput.GetNotify() ?? NotifyType.ALL);
        }
        return reviewInput;
    }

    [StarlarkMethod(
        "delete_vote",
        Doc = "Delete a label vote from an account owner on a Gerrit change.\n")]
    public void DeleteVote(
        [Param(Name = "change_id", Named = true, Doc = "The Gerrit change id.")] string changeId,
        [Param(
            Name = "account_id",
            Named = true,
            Doc = "The account owner who votes on label_id. Use 'me' or 'self' if the account owner"
                + " makes this api call")]
        string accountId,
        [Param(Name = "label_id", Named = true, Doc = "The name of the label.")] string labelId)
    {
        try
        {
            GerritApiClient gerritApi = _apiSupplier.Load(_console);
            gerritApi.DeleteVoteAsync(changeId, accountId, labelId, new DeleteVoteInput(NotifyType.NONE))
                .GetAwaiter().GetResult();
        }
        catch (GerritApiException re)
        {
            throw HandleGerritApiException(re, "delete_vote");
        }
        catch (Exception e) when (e is RepoException or ValidationException)
        {
            throw new EvalException("Error calling delete_vote: " + e.Message, e);
        }
    }

    [StarlarkMethod("submit_change", Doc = "Submit a Gerrit change")]
    public ChangeInfo SubmitChange(
        [Param(Name = "change_id", Named = true, Doc = "The Gerrit change id.")] string changeId)
    {
        ValidationException.CheckCondition(
            _allowSubmitChange,
            "Gerrit submit_change is only allowed if it is is enabled on the endpoint");
        try
        {
            GerritApiClient gerritApi = _apiSupplier.Load(_console);
            return gerritApi.SubmitChangeAsync(changeId, new SubmitInput(NotifyType.NONE))
                .GetAwaiter().GetResult();
        }
        catch (GerritApiException re)
        {
            throw HandleGerritApiException(re, "submit_change");
        }
        catch (Exception e) when (e is RepoException or ValidationException)
        {
            throw new EvalException("Error calling submit_change: " + e.Message, e);
        }
    }

    [StarlarkMethod("abandon_change", Doc = "Abandon a Gerrit change.")]
    public ChangeInfo AbandonChange(
        [Param(Name = "change_id", Named = true, Doc = "The Gerrit change id.")] string changeId)
    {
        try
        {
            GerritApiClient gerritApi = _apiSupplier.Load(_console);
            return gerritApi.AbandonChangeAsync(changeId, AbandonInput.CreateWithoutComment())
                .GetAwaiter().GetResult();
        }
        catch (GerritApiException re)
        {
            throw HandleGerritApiException(re, "abandon_change");
        }
        catch (Exception e) when (e is RepoException or ValidationException)
        {
            throw new EvalException("Error getting change: " + e.Message, e);
        }
    }

    [StarlarkMethod(
        "list_changes",
        Doc = "Get changes from Gerrit based on a query.\n")]
    public StarlarkList ListChanges(
        [Param(Name = "query", Named = true, Doc = "The query string to list changes by.")]
        string queryString,
        [Param(
            Name = "include_results",
            Named = true,
            Doc = "What to include in the response.",
            Positional = false,
            DefaultValue = "[]")]
        ISequence<object?> includeResults)
    {
        GerritApiClient gerritApi = _apiSupplier.Load(_console);
        var changes = gerritApi.GetChangesAsync(
            new ChangesQuery(queryString).WithInclude(GetIncludeResults(includeResults)))
            .GetAwaiter().GetResult();
        return StarlarkList.ImmutableCopyOf(changes);
    }

    [StarlarkMethod("url", Doc = "Return the URL of this endpoint.", StructField = true)]
    public string GetUrl() => _url;

    public IEndpoint WithConsole(Console console) =>
        new GerritEndpoint(_apiSupplier, _url, console, _allowSubmitChange);

    public ImmutableListMultimap<string, string> Describe()
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", "gerrit_api");
        builder.Put("url", _url);
        builder.Put("gerritSubmit", _allowSubmitChange.ToString());
        return builder.Build();
    }

    public override string ToString() => $"GerritEndpoint{{url={_url}}}";
}
