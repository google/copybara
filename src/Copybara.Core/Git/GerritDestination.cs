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

using System.Collections.Immutable;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using Copybara.Authoring;
using Copybara.Checks;
using Copybara.Common;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Git.GerritApi;
using Copybara.Revision;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;
using GerritApiClient = Copybara.Git.GerritApi.GerritApi;

namespace Copybara.Git;

/// <summary>
/// Gerrit repository destination. Port of <c>com.google.copybara.git.GerritDestination</c>.
/// </summary>
public sealed class GerritDestination : IDestination<GitRevision>
{
    internal const int MaxFindAttempts = 150;
    private const int SubmitMaxRetryDelayMs = 30000;

    internal const string ChangeIdLabel = "Change-Id";

    private readonly GitDestination _gitDestination;
    private readonly bool _submit;

    private GerritDestination(GitDestination gitDestination, bool submit)
    {
        _gitDestination = Preconditions.CheckNotNull(gitDestination);
        _submit = submit;
    }

    public override string ToString() => $"GerritDestination{{gitDestination={_gitDestination}}}";

    /// <summary>What to do in the presence or absent of Change-Id in message.</summary>
    public enum ChangeIdPolicy
    {
        /// <summary>Require that the change_id is present in the message as a valid label.</summary>
        Require,

        /// <summary>Fail if found in message.</summary>
        FailIfPresent,

        /// <summary>Reuse if present. Otherwise generate a new one.</summary>
        Reuse,

        /// <summary>Replace with a new one if found.</summary>
        Replace,
    }

    /// <summary>
    /// Notify Gerrit push option:
    /// https://gerrit-review.googlesource.com/Documentation/user-upload.html#notify
    /// </summary>
    public enum NotifyOption
    {
        None,
        Owner,
        OwnerReviewers,
        All,
    }

    /// <summary>A message info that contains also information if the change is a new review.</summary>
    internal sealed class GerritMessageInfo : GitDestination.MessageInfo
    {
        internal readonly bool NewReview;
        internal readonly string ChangeId;

        internal GerritMessageInfo(
            IReadOnlyList<LabelFinder> labelsToAdd, bool newReview, string changeId)
            : base(labelsToAdd)
        {
            NewReview = newReview;
            ChangeId = Preconditions.CheckNotNull(changeId);
        }
    }

    internal sealed class GerritWriteHook : GitDestination.IWriteHook
    {
        private static readonly Regex GerritUrlLine =
            new(@".*: *(http(s)?://[^ ]+)( .*)?", RegexOptions.Compiled);

        private static readonly Regex UserErrorRegexPattern =
            new(@"(2 is restricted)|(submit requirement[\w-,.:!' ]*is unsatisfied)",
                RegexOptions.Compiled);

        private readonly GerritOptions _gerritOptions;
        private readonly string _repoUrl;
        private readonly Author _committer;
        private readonly IReadOnlyList<string> _reviewersTemplate;
        private readonly IChecker? _endpointChecker;
        private readonly NotifyOption? _notifyOption;
        private readonly Console _console;
        private readonly ChangeIdPolicy _changeIdPolicy;
        private readonly bool _allowEmptyDiffPatchSet;
        private readonly GeneralOptions _generalOptions;
        private readonly IReadOnlyList<string> _ccTemplate;
        private readonly IReadOnlyList<string> _labelsTemplate;
        private readonly string? _topicTemplate;
        private readonly bool _partialFetch;
        private readonly bool _gerritSubmit;
        private readonly bool _primaryBranchMigrationMode;

        internal GerritWriteHook(
            GeneralOptions generalOptions,
            GerritOptions gerritOptions,
            string repoUrl,
            Author committer,
            IReadOnlyList<string> reviewersTemplate,
            IReadOnlyList<string> ccTemplate,
            ChangeIdPolicy changeIdPolicy,
            bool allowEmptyDiffPatchSet,
            IReadOnlyList<string> labelsTemplate,
            IChecker? endpointChecker,
            NotifyOption? notifyOption,
            string? topicTemplate,
            bool partialFetch,
            bool gerritSubmit,
            bool primaryBranchMigrationMode)
        {
            _generalOptions = Preconditions.CheckNotNull(generalOptions);
            _gerritOptions = Preconditions.CheckNotNull(gerritOptions);
            _repoUrl = Preconditions.CheckNotNull(repoUrl);
            _committer = Preconditions.CheckNotNull(committer);
            _console = Preconditions.CheckNotNull(generalOptions.GetConsole());
            _changeIdPolicy = changeIdPolicy;
            _allowEmptyDiffPatchSet = allowEmptyDiffPatchSet;
            _reviewersTemplate = Preconditions.CheckNotNull(reviewersTemplate);
            _ccTemplate = Preconditions.CheckNotNull(ccTemplate);
            _endpointChecker = endpointChecker;
            _notifyOption = notifyOption;
            _labelsTemplate = labelsTemplate;
            _topicTemplate = topicTemplate;
            _partialFetch = partialFetch;
            _gerritSubmit = gerritSubmit;
            _primaryBranchMigrationMode = primaryBranchMigrationMode;
        }

        public GitDestination.MessageInfo GenerateMessageInfo(TransformResult result)
        {
            if (!string.IsNullOrEmpty(_gerritOptions.GerritChangeId))
            {
                // CLI flag always wins.
                return CreateMessageInfo(
                    result, newReview: false, _gerritOptions.GerritChangeId, ChangeIdPolicy.Replace);
            }

            string hashTag = ComputeInternalHashTag(result);
            ChangeInfo? activeChange = FindActiveChange(hashTag);
            if (activeChange != null)
            {
                return CreateMessageInfo(
                    result, newReview: false, activeChange.GetChangeId()!, _changeIdPolicy);
            }

            // If no change is found, create a random change-id. Change-Ids can be reused later by
            // looking by hashtag in the code above.
            return CreateMessageInfo(
                result,
                newReview: true,
                "I" + Sha1Hex(Guid.NewGuid().ToString()),
                _changeIdPolicy);
        }

        private static string Sha1Hex(string input)
        {
            byte[] hash = SHA1.HashData(Encoding.UTF8.GetBytes(input));
            return Convert.ToHexStringLower(hash);
        }

        private string ComputeInternalHashTag(TransformResult result) =>
            "copybara_id_" + result.GetChangeIdentity()
                + "_" + Regex.Replace(_committer.Email, "[ ,]", "_");

        private ChangeInfo? FindActiveChange(string hashTag)
        {
            _console.ProgressFmt(
                "Querying Gerrit ('{0}') for active changes with hashtag '{1}'", _repoUrl, hashTag);
            IReadOnlyList<ChangeInfo> changes;
            try
            {
                changes = _gerritOptions.NewGerritApi(_repoUrl).GetChangesAsync(new ChangesQuery(
                        $"hashtag:\"{hashTag}\" AND project:{_gerritOptions.GetProject(_repoUrl)} AND"
                            + " status:NEW"))
                    .GetAwaiter().GetResult();
            }
            catch (Exception e) when (e is RepoException or ValidationException)
            {
                const string errMsgFmt = "Failed querying the hash tag from gerrit changes. Reason: {0}";
                if (_generalOptions.DryRunMode)
                {
                    _console.WarnFmt(errMsgFmt, e.Message);
                    return null;
                }
                throw;
            }
            ChangeInfo? maxChangeNumber = changes
                .OrderByDescending(c => c.GetNumber())
                .FirstOrDefault();
            if (changes.Count > 1)
            {
                _console.WarnFmt(
                    "Multiple changes found for the same internal copybara tag: {0}. Reusing {1}",
                    string.Join(", ", changes.Select(c => c.GetNumber())),
                    maxChangeNumber!.GetNumber());
            }
            return maxChangeNumber;
        }

        private IReadOnlyList<ChangeInfo> FindChanges(
            string changeId, IEnumerable<IncludeResult> includes)
        {
            _console.ProgressFmt("Querying Gerrit ('{0}') for change '{1}'", _repoUrl, changeId);
            return _gerritOptions.NewGerritApi(_repoUrl).GetChangesAsync(new ChangesQuery(
                    "change: " + changeId + " AND project:" + _gerritOptions.GetProject(_repoUrl))
                .WithInclude(includes))
                .GetAwaiter().GetResult();
        }

        public void BeforePush(
            GitRepository repo,
            GitDestination.MessageInfo messageInfo,
            bool skipPush,
            IReadOnlyList<IIntegrateLabel> integrateLabels,
            IReadOnlyList<object> originChanges)
        {
            var gerritMessageInfo = (GerritMessageInfo)messageInfo;
            if (_generalOptions.AllowEmptyDiffValue(_allowEmptyDiffPatchSet) || gerritMessageInfo.NewReview)
            {
                return;
            }
            using (_generalOptions.Profiler().Start("previous_patchset_check"))
            {
                ChangeInfo? changeInfo = FindChange(gerritMessageInfo.ChangeId);
                if (changeInfo == null)
                {
                    return;
                }
                var sameGitTree = new SameGitTree(repo, _repoUrl, _generalOptions, _partialFetch);
                if (changeInfo.GetCurrentRevision() != null
                    && sameGitTree.HasSameTree(changeInfo.GetCurrentRevision()!))
                {
                    throw new RedundantChangeException(
                        $"Skipping creating a new Gerrit PatchSet for change {_repoUrl}/q/"
                            + $"{changeInfo.GetNumber()} since the diff is the same from the previous"
                            + $" PatchSet ({changeInfo.GetCurrentRevision()})",
                        changeInfo.GetCurrentRevision()!);
                }
            }
        }

        private ChangeInfo? FindChange(string changeId) => FindChange(changeId, 0);

        private ChangeInfo? FindChange(string changeId, int maxDelay)
        {
            int currentAttempt = 0;
            long delayMs;
            do
            {
                var changes = FindChanges(
                    changeId,
                    new[] { IncludeResult.CURRENT_REVISION, IncludeResult.SUBMITTABLE });
                if (changes.Count != 0)
                {
                    return changes[0];
                }

                delayMs = 1000 * (long)Math.Pow(2, currentAttempt);
                if (delayMs <= maxDelay)
                {
                    currentAttempt++;
                    _console.WarnFmt(
                        "Gerrit change {0} not found (attempt {1}). Retrying in {2} ms...",
                        changeId, currentAttempt, delayMs);
                    _gerritOptions.GetSleeper().Sleep(delayMs);
                }
            }
            while (delayMs <= maxDelay);

            return null;
        }

        private void SubmitChange(string changeId)
        {
            using (_generalOptions.Profiler().Start("submit_gerrit_change"))
            {
                try
                {
                    ChangeInfo? changeInfo = FindChange(changeId, SubmitMaxRetryDelayMs);
                    if (changeInfo == null)
                    {
                        _console.WarnFmt(
                            "Gerrit change {0} still not found after waiting {1} milliseconds."
                                + " Skipping submit.",
                            changeId, SubmitMaxRetryDelayMs);
                        return;
                    }
                    GerritApiClient gerritApi = _gerritOptions.NewGerritApi(_repoUrl);
                    // If the change isn't yet submittable, try voting Code-Review+2 to approve it.
                    if (!changeInfo.IsSubmittable())
                    {
                        try
                        {
                            gerritApi.SetReviewAsync(
                                changeInfo.GetChangeId()!,
                                changeInfo.GetCurrentRevision()!,
                                new SetReviewInput(
                                    "", new Dictionary<string, int> { ["Code-Review"] = 2 }))
                                .GetAwaiter().GetResult();
                        }
                        catch (Exception e) when (e is RepoException or ValidationException)
                        {
                            _console.WarnFmt(
                                "Failed voting Code-Review + 2 to make change submittable:\n{0}",
                                e.Message);
                        }
                    }
                    string tripletId = changeInfo.GetTripletId()!;
                    gerritApi.SubmitChangeAsync(tripletId, new SubmitInput(null))
                        .GetAwaiter().GetResult();
                    _console.InfoFmt("Submitted change : {0}/changes/{1}", _repoUrl, tripletId);
                }
                catch (RepoException e)
                {
                    if (UserErrorRegexPattern.IsMatch(e.Message))
                    {
                        throw new ValidationException(e.Message, e);
                    }
                    throw;
                }
            }
        }

        public string GetPushReference(
            GitRepository repo, string pushToRefsFor, TransformResult transformResult)
        {
            string[] components = pushToRefsFor.Split('%', 2);

            var options = new List<KeyValuePair<string, string?>>();
            if (components.Length > 1)
            {
                foreach (var entry in components[1].Split(','))
                {
                    string[] strings = entry.Split('=', 2);
                    options.Add(strings.Length > 1
                        ? new KeyValuePair<string, string?>(strings[0], strings[1])
                        : new KeyValuePair<string, string?>(entry, null));
                }
            }

            if (_notifyOption != null)
            {
                options.Add(new KeyValuePair<string, string?>("notify", _notifyOption.ToString()));
            }

            string? topic = null;
            if (_topicTemplate != null)
            {
                topic = LabelFinder.MapLabels(
                    transformResult.GetLabelFinder(), _topicTemplate, "topic");
            }
            if (!string.IsNullOrEmpty(_gerritOptions.GerritTopic))
            {
                if (topic != null)
                {
                    _console.WarnFmt("Overriding topic {0} with {1}", topic, _gerritOptions.GerritTopic);
                }
                topic = _gerritOptions.GerritTopic;
            }

            if (topic != null)
            {
                options.Add(new KeyValuePair<string, string?>("topic", topic));
            }

            if (pushToRefsFor.StartsWith("refs/for/", StringComparison.Ordinal))
            {
                // Set an internal hashtag so that we can reuse changes in future snapshots.
                options.Add(new KeyValuePair<string, string?>(
                    "hashtag", ComputeInternalHashTag(transformResult)));
            }

            foreach (var r in LabelFinder.MapLabels(transformResult.GetLabelFinder(), _reviewersTemplate))
            {
                options.Add(new KeyValuePair<string, string?>("r", r));
            }
            foreach (var cc in LabelFinder.MapLabels(transformResult.GetLabelFinder(), _ccTemplate))
            {
                options.Add(new KeyValuePair<string, string?>("cc", cc));
            }
            foreach (var label in LabelFinder.MapLabels(transformResult.GetLabelFinder(), _labelsTemplate))
            {
                options.Add(new KeyValuePair<string, string?>("label", label));
            }

            string result = components[0];
            if (result.StartsWith("refs/for/", StringComparison.Ordinal))
            {
                string pushRef = result.Substring("refs/for/".Length);
                if (_primaryBranchMigrationMode && (pushRef == "master" || pushRef == "main"))
                {
                    string? primaryBranch = null;
                    try
                    {
                        primaryBranch = repo.GetPrimaryBranch(_repoUrl);
                    }
                    catch (RepoException e)
                    {
                        _console.WarnFmt("Unable to detect primary branch: {0}", e);
                    }
                    if (primaryBranch != null)
                    {
                        result = "refs/for/" + primaryBranch;
                    }
                }
            }
            if (options.Count != 0)
            {
                result += "%" + string.Join(",", options.Select(
                    e => e.Key + (e.Value != null ? "=" + e.Value : "")));
            }
            return result;
        }

        public IReadOnlyList<DestinationEffect> AfterPush(
            string serverResponse,
            GitDestination.MessageInfo messageInfo,
            GitRevision pushedRevision,
            IReadOnlyList<object> originChanges)
        {
            // Should be the message info returned by generateMessageInfo.
            var gerritMessageInfo = (GerritMessageInfo)messageInfo;
            if (_gerritSubmit)
            {
                SubmitChange(gerritMessageInfo.ChangeId);
            }
            var originRefs = originChanges.Cast<OriginRef>().ToList();
            var result = new List<DestinationEffect>
            {
                new(
                    DestinationEffect.EffectType.CREATED,
                    $"Created revision {pushedRevision.GetHash()}",
                    originRefs,
                    new DestinationEffect.DestinationRef(pushedRevision.GetHash(), "commit", url: null)),
            };

            var lines = serverResponse.Split('\n');
            Match? gerritUrlMatcher = TryFindGerritUrl(lines);
            if (gerritUrlMatcher == null || !gerritUrlMatcher.Success)
            {
                gerritUrlMatcher = TryFindGerritUrlOldFormat(lines);
            }
            if (gerritUrlMatcher != null && gerritUrlMatcher.Success)
            {
                string message = gerritMessageInfo.NewReview
                    ? "New Gerrit review created at "
                    : "Updated existing Gerrit review at ";
                string url = gerritUrlMatcher.Groups[1].Value;
                string changeNum = url.Substring(url.LastIndexOf('/') + 1);
                message += url;
                _console.Info(message);
                if (_gerritSubmit)
                {
                    message += ".\n Submited the change through API.";
                }
                result.Add(
                    new DestinationEffect(
                        gerritMessageInfo.NewReview
                            ? DestinationEffect.EffectType.CREATED
                            : DestinationEffect.EffectType.UPDATED,
                        message,
                        originRefs,
                        new DestinationEffect.DestinationRef(changeNum, "gerrit_review", url)));
            }

            return result;
        }

        private static Match? TryFindGerritUrl(IReadOnlyList<string> lines)
        {
            bool successFound = false;
            foreach (var line in lines)
            {
                if (line.Contains("SUCCESS"))
                {
                    successFound = true;
                }
                if (successFound)
                {
                    // Usually next line is empty, but best effort to find the URL after "SUCCESS".
                    Match urlMatcher = GerritUrlLine.Match(line);
                    if (urlMatcher.Success)
                    {
                        return urlMatcher;
                    }
                }
            }
            return null;
        }

        private static Match? TryFindGerritUrlOldFormat(IReadOnlyList<string> lines)
        {
            for (int i = 0; i < lines.Count; i++)
            {
                string line = lines[i];
                if ((line.Contains("New Changes") || line.Contains("Updated Changes"))
                    && i + 1 < lines.Count)
                {
                    Match urlMatcher = GerritUrlLine.Match(lines[i + 1]);
                    if (urlMatcher.Success)
                    {
                        return urlMatcher;
                    }
                }
            }
            return null;
        }

        private static string? GetExistingChangeId(string msg)
        {
            ChangeMessage changeMessage = ChangeMessage.ParseMessage(msg);
            var labels = changeMessage.LabelsAsMultimap();
            if (labels.ContainsKey(ChangeIdLabel))
            {
                var values = labels.Get(ChangeIdLabel);
                return values[^1];
            }
            return null;
        }

        private GerritMessageInfo CreateMessageInfo(
            TransformResult result, bool newReview, string gerritChangeId, ChangeIdPolicy changeIdPolicy)
        {
            IRevision rev = result.GetCurrentRevision();
            var labels = new List<LabelFinder>();
            if (result.IsSetRevId())
            {
                labels.Add(new LabelFinder(result.GetRevIdLabel() + ": " + rev.AsString()));
            }
            string? existingChangeId = GetExistingChangeId(result.GetSummary());
            string? effectiveChangeId = existingChangeId;
            switch (changeIdPolicy)
            {
                case ChangeIdPolicy.Require:
                    ValidationException.CheckCondition(
                        existingChangeId != null,
                        "{0} label not found in message:\n{1}", ChangeIdLabel, result.GetSummary());
                    break;
                case ChangeIdPolicy.FailIfPresent:
                    ValidationException.CheckCondition(
                        existingChangeId == null,
                        "{0} label found in message:\n{1}. You can use"
                            + " git.gerrit_destination(change_id_policy = ...) to change this behavior",
                        ChangeIdLabel, result.GetSummary());
                    labels.Add(new LabelFinder(ChangeIdLabel + ": " + gerritChangeId));
                    effectiveChangeId = gerritChangeId;
                    break;
                case ChangeIdPolicy.Reuse:
                    if (existingChangeId == null)
                    {
                        labels.Add(new LabelFinder(ChangeIdLabel + ": " + gerritChangeId));
                        effectiveChangeId = gerritChangeId;
                    }
                    break;
                case ChangeIdPolicy.Replace:
                    labels.Add(new LabelFinder(ChangeIdLabel + ": " + gerritChangeId));
                    effectiveChangeId = gerritChangeId;
                    break;
                default:
                    throw new NotSupportedException("Unsupported policy: " + changeIdPolicy);
            }

            return new GerritMessageInfo(labels, newReview, effectiveChangeId!);
        }

        public IEndpoint GetFeedbackEndPoint(Console console)
        {
            _gerritOptions.ValidateEndpointChecker(_endpointChecker, _repoUrl);
            return new GerritEndpoint(
                _gerritOptions.NewGerritApiSupplier(_repoUrl, _endpointChecker),
                _repoUrl,
                console,
                _gerritSubmit);
        }
    }

    public IDestination<GitRevision>.IWriter<GitRevision> NewWriter(WriterContext writerContext) =>
        _gitDestination.NewWriter(writerContext);

    public string GetLabelNameWhenOrigin() => GitRepository.GitOriginRevId;

    internal static GerritDestination NewGerritDestination(
        GeneralOptions generalOptions,
        GerritOptions gerritOptions,
        GitOptions gitOptions,
        GitDestinationOptions destinationOptions,
        string url,
        string fetch,
        string pushToRefsFor,
        bool submit,
        bool partialFetch,
        NotifyOption? notifyOption,
        ChangeIdPolicy changeIdPolicy,
        bool allowEmptyPatchSet,
        IReadOnlyList<string> reviewers,
        IReadOnlyList<string> cc,
        IReadOnlyList<string> labels,
        IChecker? endpointChecker,
        IEnumerable<GitIntegrateChanges> integrates,
        string? topicTemplate,
        bool gerritSubmit,
        bool primaryBranchMigrationMode,
        IChecker? checker,
        CredentialFileHandler? credentials)
    {
        gerritSubmit = gerritOptions.ForceGerritSubmit ?? gerritSubmit;
        submit = gerritOptions.ForceGerritSubmit ?? submit;
        string push = submit && !gerritSubmit
            ? pushToRefsFor
            : $"refs/for/{pushToRefsFor}";
        return new GerritDestination(
            new GitDestination(
                url,
                fetch,
                push,
                partialFetch,
                primaryBranchMigrationMode,
                tagName: null,
                tagMsg: null,
                destinationOptions,
                gitOptions,
                generalOptions,
                new GerritWriteHook(
                    generalOptions,
                    gerritOptions,
                    url,
                    destinationOptions.GetCommitter(),
                    reviewers,
                    cc,
                    changeIdPolicy,
                    allowEmptyPatchSet,
                    labels,
                    endpointChecker,
                    notifyOption,
                    topicTemplate,
                    partialFetch,
                    gerritSubmit,
                    primaryBranchMigrationMode),
                integrates,
                checker,
                credentials),
            submit);
    }

    public string GetType() => _submit ? _gitDestination.GetType() : "gerrit.destination";

    public ImmutableListMultimap<string, string> Describe(Glob? originFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("gerritSubmit", _submit.ToString());
        if (_submit)
        {
            builder.PutAll(_gitDestination.Describe(originFiles));
            return builder.Build();
        }
        foreach (var entry in _gitDestination.Describe(originFiles))
        {
            if (entry.Key.Equals("type"))
            {
                continue;
            }
            builder.Put(entry.Key, entry.Value);
        }
        builder.Put("type", GetType());
        return builder.Build();
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials() =>
        _gitDestination.DescribeCredentials();
}
