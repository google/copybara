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

using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Revision;

namespace Copybara.Git;

/// <summary>A Git repository reference. Port of <c>com.google.copybara.git.GitRevision</c>.</summary>
public sealed class GitRevision : IRevision
{
    public static readonly Regex CompleteGitHashPattern =
        new("^(?:[a-f0-9]{40}|[a-f0-9]{64})$", RegexOptions.Compiled);

    private readonly GitRepository _repository;
    private readonly GitHashAlgorithm _hashAlgorithm;
    private readonly string _hash;
    private readonly string? _reference;
    private readonly ImmutableListMultimap<string, string> _associatedLabels;
    private readonly string? _reviewReference;
    private readonly string? _url;
    private string? _describe;
    private string? _describeAbbrev;
    private string? _revisionNumber;
    private string? _fullReferenceCache;
    private bool _fullReferenceComputed;

    private readonly object _lock = new();

    /// <summary>
    /// Create a git revision from a complete (40 or 64 characters) git hash string.
    /// </summary>
    public GitRevision(GitRepository repository, string hash)
        : this(repository, hash, null, null, ImmutableListMultimap<string, string>.Empty, null)
    {
    }

    /// <summary>
    /// Create a git revision from a complete git hash string with a url.
    /// </summary>
    public GitRevision(GitRepository repository, string hash, string? url)
        : this(repository, hash, null, null, ImmutableListMultimap<string, string>.Empty, url)
    {
    }

    /// <summary>
    /// Create a git revision from a complete (40 or 64 characters) git hash string.
    /// </summary>
    /// <param name="repository">git repository that should contain the hash</param>
    /// <param name="hash">the commit hash</param>
    /// <param name="reviewReference">an arbitrary string that allows to keep track of the revision of
    ///     the code review being migrated.</param>
    /// <param name="reference">a stable name that describes where this is coming from. Could be a git
    ///     reference like 'master'.</param>
    /// <param name="associatedLabels">labels associated with this reference</param>
    /// <param name="url">if present, the url of the repository that the revision comes from</param>
    public GitRevision(
        GitRepository repository,
        string hash,
        string? reviewReference,
        string? reference,
        ImmutableListMultimap<string, string> associatedLabels,
        string? url)
    {
        _reviewReference = reviewReference;
        Preconditions.CheckArgument(
            CompleteGitHashPattern.IsMatch(hash),
            "Reference '%s' is not a full git hash (40 characters SHA-1 or 64 characters SHA-256)",
            hash);

        _repository = Preconditions.CheckNotNull(repository);
        _hash = hash;
        _hashAlgorithm = GitHashAlgorithmMethods.From(hash);
        _reference = reference;

        var labels = ImmutableListMultimap<string, string>.CreateBuilder();
        var existing = new HashSet<string>();
        foreach (var key in associatedLabels.Keys)
        {
            existing.Add(key);
            labels.PutAll(key, associatedLabels.Get(key));
        }
        string shortHash = hash.Substring(0, 7);
        // TODO: Remove GIT_SHA1 and GIT_SHORT_SHA1 labels once all instances are updated.
        if (_hashAlgorithm == GitHashAlgorithm.Sha1)
        {
            PutIfMissing(labels, existing, "GIT_SHA1", hash);
            PutIfMissing(labels, existing, "GIT_SHORT_SHA1", shortHash);
        }
        PutIfMissing(labels, existing, "GIT_HASH", hash);
        PutIfMissing(labels, existing, "GIT_SHORT_HASH", shortHash);
        _associatedLabels = labels.Build();
        _url = url;
    }

    private static void PutIfMissing(
        ImmutableListMultimap<string, string>.Builder labels,
        HashSet<string> existing,
        string key,
        string value)
    {
        if (existing.Add(key))
        {
            labels.Put(key, value);
        }
    }

    public string? ContextReference() => _reference;

    public string? FixedReference() => _hash;

    public string? FullReference()
    {
        lock (_lock)
        {
            if (_fullReferenceComputed)
            {
                return _fullReferenceCache;
            }

            if (_reference == null || _reference.StartsWith("refs/", StringComparison.Ordinal))
            {
                _fullReferenceCache = _reference;
                _fullReferenceComputed = true;
                return _fullReferenceCache;
            }

            try
            {
                var matchingRefs = new List<string>();
                var refs = _repository.ShowRef(
                    new[] { _reference, _reference + GitRepository.FullRefNamespace });
                foreach (var e in refs)
                {
                    if (!e.Key.StartsWith(
                            GitRepository.CopybaraFetchNamespace + "/refs/", StringComparison.Ordinal))
                    {
                        continue;
                    }
                    if (e.Value.GetHash() != _hash)
                    {
                        continue;
                    }
                    matchingRefs.Add(GetCleanedFullReference(e.Key));
                }

                if (matchingRefs.Count != 0)
                {
                    // Git allows branches and tags with the same name. Prioritize tags over branches.
                    string? tag = matchingRefs.FirstOrDefault(
                        e => e.StartsWith("refs/tags/", StringComparison.Ordinal));
                    _fullReferenceCache = tag ?? matchingRefs[0];
                }
            }
            catch (RepoException)
            {
                // Could not determine full reference; leave unset for this call.
                return null;
            }

            _fullReferenceComputed = true;
            return _fullReferenceCache;
        }
    }

    private static string GetCleanedFullReference(string originalRef)
    {
        string fullRef = originalRef;
        const string prefix = "refs/copybara_fetch/";
        if (fullRef.StartsWith(prefix, StringComparison.Ordinal))
        {
            fullRef = fullRef.Substring(prefix.Length);
        }
        if (fullRef.EndsWith(GitRepository.FullRefNamespace, StringComparison.Ordinal))
        {
            fullRef = fullRef.Substring(0, fullRef.Length - GitRepository.FullRefNamespace.Length);
        }
        return fullRef;
    }

    public DateTimeOffset? ReadTimestamp()
    {
        var entry = _repository.Log(_hash).WithLimit(1).Run();
        if (entry.Count == 0)
        {
            throw new RepoException($"Cannot find '{_hash}' in the git repository");
        }
        return entry[0].AuthorDate;
    }

    public string AsString() =>
        _hash + (_reviewReference == null ? "" : " " + _reviewReference);

    public string GetHash() => _hash;

    public GitHashAlgorithm GetHashAlgorithm() => _hashAlgorithm;

    public string? GetReviewReference() => _reviewReference;

    public override string ToString()
    {
        var sb = new System.Text.StringBuilder("GitRevision{");
        var parts = new List<string>();
        if (_url != null)
        {
            parts.Add($"url={_url}");
        }
        if (_reference != null)
        {
            parts.Add($"reference={_reference}");
        }
        parts.Add($"hash={_hash}");
        sb.Append(string.Join(", ", parts));
        sb.Append('}');
        return sb.ToString();
    }

    public string? GetUrl() => _url;

    public string? GetRevisionType() => "Git";

    public override bool Equals(object? o)
    {
        if (ReferenceEquals(this, o))
        {
            return true;
        }
        if (o is null || GetType() != o.GetType())
        {
            return false;
        }
        var that = (GitRevision)o;
        return _hash == that._hash;
    }

    public override int GetHashCode() => _hash.GetHashCode();

    public ImmutableListMultimap<string, string> AssociatedLabels() => _associatedLabels;

    public IReadOnlyList<string> AssociatedLabel(string label)
    {
        // We only return git describe if specifically asked for this label
        if (label == GitRepository.GitDescribeChangeVersion)
        {
            return PopulateDescribe();
        }
        if (label == GitRepository.GitSequentialRevisionNumber)
        {
            return PopulateRevisionNumber();
        }
        if (label == GitRepository.GitDescribeAbbrev)
        {
            return PopulateDescribeAbbrev();
        }
        if (label == GitRepository.GitTagPointsAt)
        {
            return PopulateTagPointsAt();
        }
        return _associatedLabels.Get(label);
    }

    /// <summary>Lazily compute describe.</summary>
    private IReadOnlyList<string> PopulateDescribe()
    {
        lock (_lock)
        {
            if (_describe == null)
            {
                try
                {
                    _describe = _repository.Describe(this, false);
                }
                catch (RepoException)
                {
                    _describe = _hash.Substring(0, 7);
                }
            }
            return new[] { _describe! };
        }
    }

    private IReadOnlyList<string> PopulateTagPointsAt()
    {
        lock (_lock)
        {
            if (_associatedLabels.ContainsKey(GitRepository.GitTagPointsAt))
            {
                return _associatedLabels.Get(GitRepository.GitTagPointsAt);
            }
            try
            {
                return _repository.TagPointsAt(this);
            }
            catch (RepoException)
            {
                // Cannot get 'tag --points-at' output.
            }
            return Array.Empty<string>();
        }
    }

    private IReadOnlyList<string> PopulateDescribeAbbrev()
    {
        lock (_lock)
        {
            if (_associatedLabels.ContainsKey(GitRepository.GitDescribeAbbrev))
            {
                return _associatedLabels.Get(GitRepository.GitDescribeAbbrev);
            }
            if (_describeAbbrev == null)
            {
                try
                {
                    _describeAbbrev = _repository.DescribeAbbrev(this);
                }
                catch (RepoException)
                {
                    // Cannot get closest tag.
                }
            }
            return new[] { _describeAbbrev ?? "" };
        }
    }

    private IReadOnlyList<string> PopulateRevisionNumber()
    {
        lock (_lock)
        {
            if (_revisionNumber == null)
            {
                try
                {
                    var cmdout = _repository.SimpleCommand("rev-list", "--count", _hash);
                    _revisionNumber = cmdout.GetStdout().Trim();
                }
                catch (RepoException)
                {
                    _revisionNumber = "";
                }
            }
            return new[] { _revisionNumber! };
        }
    }

    internal GitRevision WithUrl(string url) =>
        new(_repository, _hash, _reviewReference, _reference, _associatedLabels, url);

    internal GitRevision WithContextReference(string tag) =>
        new(_repository, _hash, _reviewReference, tag, _associatedLabels, _url);

    internal GitRevision WithLabels(ImmutableListMultimap<string, string> labels) =>
        new(
            _repository,
            _hash,
            _reviewReference,
            _reference,
            IRevision.AddNewLabels(_associatedLabels, labels),
            _url);
}

/// <summary>Supported Git hash algorithms.</summary>
public enum GitHashAlgorithm
{
    /// <summary>SHA-1 hash algorithm (40 characters).</summary>
    Sha1,

    /// <summary>SHA-256 hash algorithm (64 characters).</summary>
    Sha256,
}

/// <summary>Helpers for <see cref="GitHashAlgorithm"/>.</summary>
public static class GitHashAlgorithmMethods
{
    public static int GetLength(this GitHashAlgorithm algorithm) =>
        algorithm == GitHashAlgorithm.Sha1 ? 40 : 64;

    /// <summary>Returns the algorithm corresponding to the length of the given hash.</summary>
    public static GitHashAlgorithm From(string hash) =>
        hash.Length switch
        {
            40 => GitHashAlgorithm.Sha1,
            64 => GitHashAlgorithm.Sha256,
            _ => throw new ArgumentException(
                $"Invalid hash length {hash.Length}: '{hash}'"),
        };
}
