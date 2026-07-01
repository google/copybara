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

using Copybara.Authoring;
using Copybara.Common;

namespace Copybara;

/// <summary>
/// Metadata associated with a change: Change message, author, etc.
///
/// <para>Note: upstream uses <c>ImmutableSetMultimap</c> for hidden labels; this port uses
/// <see cref="ImmutableListMultimap{TKey,TValue}"/> but preserves the "no duplicates" semantics by
/// de-duplicating on merge.</para>
/// </summary>
public sealed class Metadata
{
    private readonly string _message;
    private readonly Author _author;
    private readonly ImmutableListMultimap<string, string> _hiddenLabels;

    public Metadata(string message, Author author, ImmutableListMultimap<string, string> hiddenLabels)
    {
        _message = Preconditions.CheckNotNull(message);
        _author = Preconditions.CheckNotNull(author);
        _hiddenLabels = Preconditions.CheckNotNull(hiddenLabels);
    }

    public Metadata WithAuthor(Author author) =>
        new(_message, Preconditions.CheckNotNull(author, "Author cannot be null"), _hiddenLabels);

    public Metadata WithMessage(string message) =>
        new(Preconditions.CheckNotNull(message, "Message cannot be null"), _author, _hiddenLabels);

    /// <summary>
    /// We never allow deleting hidden labels. Use a different name if you want to rename one.
    /// </summary>
    public Metadata WithHiddenLabels(ImmutableListMultimap<string, string> hiddenLabels)
    {
        Preconditions.CheckNotNull(hiddenLabels, "hidden labels cannot be null");
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        foreach (var key in _hiddenLabels.Keys)
        {
            foreach (var value in _hiddenLabels.Get(key))
            {
                builder.Put(key, value);
            }
        }
        foreach (var key in hiddenLabels.Keys)
        {
            foreach (var value in hiddenLabels.Get(key))
            {
                // Preserve set semantics: don't add duplicate entries.
                if (!_hiddenLabels.ContainsEntry(key, value))
                {
                    builder.Put(key, value);
                }
            }
        }
        return new Metadata(_message, _author, builder.Build());
    }

    /// <summary>Description to be used for the change.</summary>
    public string GetMessage() => _message;

    /// <summary>Author to be used for the change.</summary>
    public Author GetAuthor() => _author;

    /// <summary>
    /// Hidden labels are labels added by transformations during transformations but that are not
    /// visible in the message.
    /// </summary>
    public ImmutableListMultimap<string, string> GetHiddenLabels() => _hiddenLabels;

    public override string ToString() =>
        $"Metadata{{message={_message}, author={_author}, hiddenLabels={_hiddenLabels}}}";
}
