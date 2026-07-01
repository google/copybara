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
using Copybara.Authoring;
using Copybara.Common;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Revision;

/// <summary>Represents a change in a Repository.</summary>
[StarlarkBuiltin(
    "change",
    Doc = "A change metadata. Contains information like author, change message or detected labels")]
public sealed class Change<R> : OriginRef, IStarlarkValue
    where R : class, IRevision
{
    private readonly R _revision;
    private readonly Author _author;
    private readonly string _message;
    private readonly DateTimeOffset _dateTime;
    private readonly ImmutableListMultimap<string, string> _labels;
    private Author? _mappedAuthor;
    private readonly bool _merge;
    private readonly ImmutableArray<R>? _parents;
    private readonly ImmutableHashSet<string>? _changeFiles;

    public Change(
        R revision,
        Author author,
        string message,
        DateTimeOffset dateTime,
        ImmutableListMultimap<string, string> labels)
        : this(revision, author, message, dateTime, labels, changeFiles: null)
    {
    }

    public Change(
        R revision,
        Author author,
        string message,
        DateTimeOffset dateTime,
        ImmutableListMultimap<string, string> labels,
        ISet<string>? changeFiles)
        : this(revision, author, message, dateTime, labels, changeFiles, merge: false, parents: null)
    {
    }

    public Change(
        R revision,
        Author author,
        string message,
        DateTimeOffset dateTime,
        ImmutableListMultimap<string, string> labels,
        ISet<string>? changeFiles,
        bool merge,
        ImmutableArray<R>? parents)
        : base(Preconditions.CheckNotNull(revision).AsString())
    {
        _revision = Preconditions.CheckNotNull(revision);
        _author = Preconditions.CheckNotNull(author);
        _message = Preconditions.CheckNotNull(message);
        _dateTime = dateTime;
        _labels = labels;
        _changeFiles = changeFiles is null ? null : changeFiles.ToImmutableHashSet();
        _merge = merge;
        _parents = parents;
    }

    /// <summary>Reference of the change. For example a SHA-1 reference in git.</summary>
    public R GetRevision() => _revision;

    /// <summary>
    /// Return the parent revisions if the origin provides that information. Currently only for Git
    /// and Hg. Otherwise null.
    /// </summary>
    public ImmutableArray<R>? GetParents() => _parents;

    [StarlarkMethod(
        "original_author",
        Doc = "The author of the change before any mapping",
        StructField = true)]
    public Author GetAuthor() => _author;

    /// <summary>The author of the change. Can already be mapped using metadata.map_author.</summary>
    [StarlarkMethod("author", Doc = "The author of the change", StructField = true)]
    public Author GetMappedAuthor() => Preconditions.CheckNotNull(_mappedAuthor ?? _author);

    public void SetMappedAuthor(Author mappedAuthor) => _mappedAuthor = mappedAuthor;

    [StarlarkMethod("message", Doc = "The message of the change", StructField = true)]
    public string GetMessage() => _message;

    [StarlarkMethod(
        "labels",
        Doc =
            "A dictionary with the labels detected for the change. If the label is present multiple"
            + " times it returns the last value. Note that this is a heuristic and it could"
            + " include things that are not labels.",
        StructField = true)]
    public IReadOnlyDictionary<string, string> GetLabelsForSkylark()
    {
        var builder = ImmutableDictionary.CreateBuilder<string, string>();
        foreach (var key in _labels.Keys)
        {
            var values = _labels.Get(key);
            builder[key] = values[values.Length - 1];
        }
        return builder.ToImmutable();
    }

    [StarlarkMethod(
        "labels_all_values",
        Doc =
            "A dictionary with the labels detected for the change. Note that the value is a"
            + " collection of the values for each time the label was found. Use 'labels' instead"
            + " if you are only interested in the last value. Note that this is a heuristic and"
            + " it could include things that are not labels.",
        StructField = true)]
    public IReadOnlyDictionary<string, IReadOnlyList<string>> GetLabelsAllForSkylark()
    {
        var builder = ImmutableDictionary.CreateBuilder<string, IReadOnlyList<string>>();
        foreach (var key in _labels.Keys)
        {
            builder[key] = _labels.Get(key);
        }
        return builder.ToImmutable();
    }

    /// <summary>If not null, the files that were affected in this change.</summary>
    public ImmutableHashSet<string>? GetChangeFiles() => _changeFiles;

    public DateTimeOffset GetDateTime() => _dateTime;

    [StarlarkMethod(
        "date_time_iso_offset",
        Doc = "Return a ISO offset date time. Example:  2011-12-03T10:15:30+01:00'",
        StructField = true)]
    public string DateTimeFmt() =>
        GetDateTime().ToString("yyyy-MM-ddTHH:mm:sszzz", System.Globalization.CultureInfo.InvariantCulture);

    public ImmutableListMultimap<string, string> GetLabels() => _labels;

    /// <summary>Returns the first line of the change. Usually a summary.</summary>
    [StarlarkMethod("first_line_message", Doc = "The message of the change", StructField = true)]
    public string FirstLineMessage() => ExtractFirstLine(_message);

    /// <summary>Get the first line of a message.</summary>
    public static string ExtractFirstLine(string message)
    {
        int idx = message.IndexOf('\n');
        return idx == -1 ? message : message.Substring(0, idx);
    }

    /// <summary>Returns true if the change represents a merge.</summary>
    [StarlarkMethod("merge", Doc = "Returns true if the change represents a merge", StructField = true)]
    public bool IsMerge() => _merge;

    public override string ToString() =>
        $"Change{{revision={_revision}, author={_author}, dateTime={_dateTime}, message={_message}, "
        + $"merge={_merge}, parents={(_parents is null ? "null" : string.Join(", ", _parents))}}}";

    public Change<R> WithLabels(ImmutableListMultimap<string, string> newLabels) =>
        new(
            _revision,
            _author,
            _message,
            _dateTime,
            IRevision.AddNewLabels(_labels, newLabels),
            _changeFiles,
            _merge,
            _parents);

    public Change<R> WithChangeFiles(ImmutableHashSet<string> newChangeFiles) =>
        new(_revision, _author, _message, _dateTime, _labels, newChangeFiles, _merge, _parents);

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
        var change = (Change<R>)o;
        return Equals(_revision, change._revision)
            && Equals(_author, change._author)
            && string.Equals(_message, change._message)
            && Equals(_dateTime, change._dateTime)
            && Equals(_labels, change._labels);
    }

    public override int GetHashCode() =>
        HashCode.Combine(_revision, _author, _message, _dateTime, _labels);
}
