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
using Copybara.Common;
using Copybara.Util;

namespace Copybara;

/// <summary>
/// Interface for self-description. The information returned should be sufficient to create a new
/// instance with identical migration behavior (but potentially different side effects). This is
/// intended for discovering changes in a config.
/// </summary>
public interface IConfigItemDescription
{
    string GetTypeName() => GetType().FullName ?? GetType().Name;

    /// <summary>Returns a key-value list of the options the endpoint was instantiated with.</summary>
    ImmutableListMultimap<string, string> Describe(Glob? originFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", GetTypeName());
        return builder.Build();
    }

    /// <summary>Returns a key-value list describing the credentials the endpoint was instantiated with.</summary>
    IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials() =>
        ImmutableArray<ImmutableListMultimap<string, string>>.Empty;

    /// <summary>Returns a key-value list describing the credentials the endpoint was instantiated with.</summary>
    IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials(string endpoint)
    {
        var creds = DescribeCredentials();
        if (creds.Count == 0)
        {
            return creds;
        }
        var builder = ImmutableArray.CreateBuilder<ImmutableListMultimap<string, string>>();
        foreach (var cred in creds)
        {
            var credBuilder = ImmutableListMultimap<string, string>.CreateBuilder();
            credBuilder.PutAll(cred);
            credBuilder.Put("endpoint", endpoint);
            builder.Add(credBuilder.Build());
        }
        return builder.ToImmutable();
    }
}
