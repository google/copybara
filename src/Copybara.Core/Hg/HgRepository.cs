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
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Util;

namespace Copybara.Hg;

/// <summary>A class for manipulating Hg (Mercurial) repositories.</summary>
public class HgRepository
{
    /// <summary>Label to mark the original revision id (Hg SHA-1) for migrated commits.</summary>
    internal const string HgOriginRevId = "HgOrigin-RevId";

    private static readonly Regex InvalidHgRepository =
        new("abort: repository .+ not found", RegexOptions.Compiled);

    private static readonly Regex UnknownRevision =
        new("abort: unknown revision '.+'", RegexOptions.Compiled);

    private static readonly Regex InvalidRefExpression =
        new("syntax error in revset '.+'", RegexOptions.Compiled);

    /// <summary>The location of the <c>.hg</c> directory.</summary>
    private readonly string _hgDir;
    private readonly bool _verbose;
    private readonly TimeSpan _repoTimeout;

    public HgRepository(string hgDir, bool verbose, TimeSpan repoTimeout)
    {
        _hgDir = Preconditions.CheckNotNull(hgDir);
        _verbose = verbose;
        _repoTimeout = repoTimeout;
    }

    /// <summary>Initializes a new hg repository.</summary>
    /// <returns>the new HgRepository</returns>
    /// <exception cref="RepoException">if the directory cannot be created</exception>
    public HgRepository Init()
    {
        try
        {
            Directory.CreateDirectory(_hgDir);
        }
        catch (IOException e)
        {
            throw new RepoException("Cannot create directory: " + e.Message, e);
        }

        Hg(_hgDir, new[] { "init" }, CommandRunner.DefaultTimeout);
        return this;
    }

    /// <summary>
    /// Finds all changes from a repository at <paramref name="url"/> and adds to the current
    /// repository. Defaults to forced pull.
    /// </summary>
    internal void PullAll(string url) => Pull(url, force: true, @ref: null);

    /// <summary>
    /// Finds a single reference from a repository at <paramref name="url"/> and adds to the current
    /// repository. Defaults to forced pull.
    /// </summary>
    internal void PullFromRef(string url, string? @ref) => Pull(url, force: true, @ref: @ref);

    public void Pull(string url, bool force, string? @ref)
    {
        var builder = ImmutableArray.CreateBuilder<string>();
        builder.Add("pull");
        builder.Add(ValidateNotHttp(url));

        if (force)
        {
            builder.Add("--force");
        }

        if (!string.IsNullOrEmpty(@ref))
        {
            builder.Add("--rev");
            builder.Add(@ref);
        }

        try
        {
            Hg(_hgDir, builder.ToImmutable(), _repoTimeout);
        }
        catch (RepoException e)
        {
            if (e.Message is not null && InvalidHgRepository.IsMatch(e.Message))
            {
                throw new ValidationException("Repository not found: " + e.Message);
            }

            if (e.Message is not null && UnknownRevision.IsMatch(e.Message))
            {
                throw new ValidationException("Unknown revision: " + e.Message);
            }

            throw;
        }
    }

    /// <summary>
    /// Updates the working directory to the revision given at <paramref name="ref"/> in the
    /// repository and discards local changes.
    /// </summary>
    internal CommandOutput CleanUpdate(string @ref) => Hg(_hgDir, "update", @ref, "--clean");

    /// <summary>Returns a revision object given a reference.</summary>
    public HgRevision Identify(string reference)
    {
        try
        {
            CommandOutput commandOutput =
                Hg(_hgDir, "identify", "--template", "{node}\n", "--id", "--rev", reference);

            string globalId = commandOutput.GetStdout().Trim();
            return new HgRevision(globalId, reference);
        }
        catch (RepoException e)
        {
            if (e.Message is not null && UnknownRevision.IsMatch(e.Message))
            {
                throw new CannotResolveRevisionException($"Unknown revision: {e.Message}");
            }

            throw;
        }
    }

    /// <summary>
    /// Creates an unversioned archive of the current working directory and subrepositories in the
    /// location <paramref name="archivePath"/>.
    /// </summary>
    internal void Archive(string archivePath) =>
        Hg(_hgDir, "archive", archivePath, "--type", "files", "--subrepos");

    /// <summary>Creates a log command.</summary>
    public LogCmd Log() => LogCmd.Create(this);

    /// <summary>
    /// Invokes <c>hg</c> in the directory given by <paramref name="cwd"/> against this repository and
    /// returns the <see cref="CommandOutput"/> if the command execution was successful.
    ///
    /// <para>Only to be used externally for testing.</para>
    /// </summary>
    public CommandOutput Hg(string cwd, params string[] @params) =>
        Hg(cwd, @params, CommandRunner.DefaultTimeout);

    private CommandOutput Hg(string cwd, IEnumerable<string> @params, TimeSpan timeout)
    {
        try
        {
            return ExecuteHg(cwd, @params, -1, timeout);
        }
        catch (BadExitStatusWithOutputException e)
        {
            throw new RepoException($"Error executing hg: {e.GetOutput().GetStderr()}");
        }
        catch (CommandException e)
        {
            throw new RepoException($"Error executing hg: {e.Message}");
        }
    }

    public string GetHgDir() => _hgDir;

    private CommandOutputWithStatus ExecuteHg(
        string cwd, IEnumerable<string> @params, int maxLogLines, TimeSpan timeout)
    {
        var allParams = new List<string> { "hg" }; // TODO(jlliu): resolve Hg binary here
        allParams.AddRange(@params);
        // TODO(jlliu): have environment vars
        var cmd = new Command(allParams.ToArray(), null, cwd);
        CommandRunner runner = new CommandRunner(cmd, timeout).WithVerbose(_verbose);
        return maxLogLines >= 0
            ? runner.WithMaxStdOutLogLines(maxLogLines).Execute()
            : runner.Execute();
    }

    // Port of RepositoryUtil.validateNotHttp.
    private static string ValidateNotHttp(string url)
    {
        ValidationException.CheckCondition(
            !url.StartsWith("http://", StringComparison.Ordinal),
            "URL '{0}' is not valid - should be using https.",
            url);
        return url;
    }

    /// <summary>
    /// An object that can perform a "hg log" operation on a repository and returns a list of
    /// <see cref="HgLogEntry"/>.
    /// </summary>
    public sealed class LogCmd
    {
        private readonly HgRepository _repo;
        private readonly int _limit;

        /// <summary>Branch to limit the query from. Defaults to all branches if null.</summary>
        private readonly string? _branch;

        private readonly string? _referenceExpression;
        private readonly string? _keyword;

        private LogCmd(
            HgRepository repo, int limit, string? branch, string? referenceExpression, string? keyword)
        {
            _repo = repo;
            _limit = limit;
            _branch = branch;
            _referenceExpression = referenceExpression;
            _keyword = keyword;
        }

        internal static LogCmd Create(HgRepository repo) =>
            new(Preconditions.CheckNotNull(repo), 0, null, null, null);

        /// <summary>Limit the query to references that fit the <paramref name="referenceExpression"/>.</summary>
        internal LogCmd WithReferenceExpression(string referenceExpression)
        {
            if (string.IsNullOrEmpty(referenceExpression.Trim()))
            {
                throw new RepoException("Cannot log null or empty reference");
            }

            return new LogCmd(_repo, _limit, _branch, referenceExpression.Trim(), _keyword);
        }

        /// <summary>Limit the query to <paramref name="limit"/> results. Should be &gt; 0.</summary>
        public LogCmd WithLimit(int limit)
        {
            Preconditions.CheckArgument(limit > 0);
            return new LogCmd(_repo, limit, _branch, _referenceExpression, _keyword);
        }

        /// <summary>Only query for revisions from the branch <paramref name="branch"/>.</summary>
        internal LogCmd WithBranch(string branch) =>
            new(_repo, _limit, branch, _referenceExpression, _keyword);

        /// <summary>Only query for revisions with the keyword <paramref name="keyword"/>.</summary>
        internal LogCmd WithKeyword(string keyword) =>
            new(_repo, _limit, _branch, _referenceExpression, keyword);

        /// <summary>Run "hg log" and return zero or more <see cref="HgLogEntry"/>.</summary>
        public IReadOnlyList<HgLogEntry> Run()
        {
            var builder = ImmutableArray.CreateBuilder<string>();
            builder.Add("log");
            builder.Add("--verbose"); // verbose flag shows files in output

            if (_branch != null)
            {
                builder.Add("--branch");
                builder.Add(_branch);
            }

            // hg requires limit to be a positive integer
            if (_limit > 0)
            {
                builder.Add("--limit");
                builder.Add(_limit.ToString());
            }

            if (_referenceExpression != null)
            {
                builder.Add("--rev");
                builder.Add(_referenceExpression);
            }

            if (_keyword != null)
            {
                builder.Add("--keyword");
                builder.Add(_keyword);
            }

            builder.Add("-Tjson");
            try
            {
                CommandOutput output = _repo.Hg(_repo.GetHgDir(), builder.ToImmutable(), CommandRunner.DefaultTimeout);
                return ParseLog(output.GetStdout());
            }
            catch (RepoException e)
            {
                if (e.Message is not null && UnknownRevision.IsMatch(e.Message))
                {
                    throw new ValidationException("Unknown revision: " + e.Message);
                }

                if (e.Message is not null && InvalidRefExpression.IsMatch(e.Message))
                {
                    throw new RepoException("Syntax error in reference expression: " + e.Message);
                }

                throw;
            }
        }

        private static IReadOnlyList<HgLogEntry> ParseLog(string log)
        {
            if (log.Length == 0)
            {
                return ImmutableArray<HgLogEntry>.Empty;
            }

            try
            {
                List<HgLogEntry>? logEntries =
                    JsonSerializer.Deserialize<List<HgLogEntry>>(log.Trim());
                return logEntries is null
                    ? ImmutableArray<HgLogEntry>.Empty
                    : logEntries.ToImmutableArray();
            }
            catch (JsonException e)
            {
                throw new RepoException($"Cannot parse log output: {e.Message}");
            }
        }
    }

    /// <summary>An object that represents a commit as returned by 'hg log'.</summary>
    public sealed class HgLogEntry
    {
        [JsonPropertyName("node")]
        public string GlobalId { get; set; } = string.Empty;

        [JsonPropertyName("parents")]
        public List<string> Parents { get; set; } = new();

        [JsonPropertyName("user")]
        public string? User { get; set; }

        [JsonPropertyName("date")]
        public List<JsonElement> CommitDate { get; set; } = new();

        [JsonPropertyName("branch")]
        public string? Branch { get; set; }

        [JsonPropertyName("desc")]
        public string? Description { get; set; }

        [JsonPropertyName("files")]
        public List<string> Files { get; set; } = new();

        public IReadOnlyList<string> GetParents() => Parents;

        public string? GetUser() => User;

        internal string GetGlobalId() => GlobalId;

        internal DateTimeOffset GetZonedDate()
        {
            // hg -Tjson emits date as [epochSeconds, tzOffsetSeconds]; the offset sign is inverted
            // relative to a standard UTC offset (mirrors the Java implementation).
            long epochSeconds = (long)ToDouble(CommitDate[0]);
            int tzOffset = (int)ToDouble(CommitDate[1]);
            var date = DateTimeOffset.FromUnixTimeSeconds(epochSeconds);
            var offset = TimeSpan.FromSeconds(-1 * tzOffset);
            return date.ToOffset(offset);
        }

        private static double ToDouble(JsonElement element) =>
            element.ValueKind == JsonValueKind.String
                ? double.Parse(element.GetString()!)
                : element.GetDouble();

        public string? GetBranch() => Branch;

        public string? GetDescription() => Description;

        public IReadOnlyList<string> GetFiles() => Files;
    }
}
