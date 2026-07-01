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

using Copybara.Common;
using Copybara.Exceptions;

namespace Copybara.Revision;

/// <summary>
/// A revision of <c>Copybara.Origin</c>.
///
/// <para>For example, in Git it would be a commit SHA-1.</para>
/// </summary>
public interface IRevision
{
    /// <summary>
    /// Reads the timestamp of this revision from the repository, or <c>null</c> if this repo type
    /// does not support it. This is the instant from the UNIX epoch when the revision was submitted
    /// to the source repository.
    /// </summary>
    DateTimeOffset? ReadTimestamp();

    /// <summary>
    /// String representation of the revision that can be parsed by
    /// <c>Copybara.Origin.Resolve(string)</c>.
    ///
    /// <para>Unlike the <see cref="object.ToString"/> method, this method is guaranteed to be
    /// stable.</para>
    /// </summary>
    string AsString();

    /// <summary>
    /// If not null, returns a stable name representing the reference from where this
    /// <see cref="IRevision"/> was created.
    ///
    /// <para>For example if the user passed 'master' in the command line, the <see cref="AsString"/>
    /// would return the SHA-1 and this method would return 'master'. Note that it is a valid
    /// response to return <see cref="AsString"/> here if the implementation chooses to.</para>
    /// </summary>
    string? ContextReference() => null;

    /// <summary>
    /// If not null, returns a fixed value identifying the repo state for this
    /// <see cref="IRevision"/>.
    ///
    /// <para>For example if the user passed 'main' in the command line, this would return the SHA-1
    /// of the current HEAD of main while <see cref="ContextReference"/> would return 'main'.</para>
    /// </summary>
    string? FixedReference() => null;

    /// <summary>
    /// Returns a stable name representing the reference from where this <see cref="IRevision"/> was
    /// created.
    ///
    /// <para>The difference between this and <see cref="ContextReference"/> is that this method
    /// returns a complete reference path, if possible. For example, if the user passed in `main`,
    /// this method would return `refs/heads/main`, while <see cref="ContextReference"/> would return
    /// `main`.</para>
    /// </summary>
    string? FullReference() => ContextReference();

    /// <summary>
    /// Return any associated label with the revision. Keys are the label name and values are the
    /// content of the label.
    ///
    /// <para>Labels should only be set when the origin knows for sure that the reference is in the
    /// context of the current migration.</para>
    /// </summary>
    ImmutableListMultimap<string, string> AssociatedLabels() =>
        ImmutableListMultimap<string, string>.Empty;

    /// <summary>Find a specific label.</summary>
    IReadOnlyList<string> AssociatedLabel(string label) => AssociatedLabels().Get(label);

    /// <summary>The url of the revision repository.</summary>
    string? GetUrl() => null;

    /// <summary>
    /// String that represents the revision type.
    ///
    /// <para>The purpose of this string is to be used as an identifier in other systems. Once it has
    /// been defined, it shouldn't be changed.</para>
    /// </summary>
    string? GetRevisionType() => null;

    /// <summary>Given a list of labels it adds new labels without repeating them.</summary>
    static ImmutableListMultimap<string, string> AddNewLabels(
        ImmutableListMultimap<string, string> existingLabels,
        ImmutableListMultimap<string, string> newLabels)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.PutAll(existingLabels);
        foreach (var key in newLabels.Keys)
        {
            foreach (var v in newLabels.Get(key))
            {
                if (existingLabels.ContainsEntry(key, v))
                {
                    continue;
                }
                builder.Put(key, v);
            }
        }
        return builder.Build();
    }
}
