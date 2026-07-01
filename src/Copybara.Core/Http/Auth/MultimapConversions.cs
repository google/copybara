/*
 * Copyright (C) 2023 Google LLC.
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

namespace Copybara.Http;

/// <summary>
/// Helpers to bridge the two multimap shapes used across the port. Credentials describe themselves
/// with <see cref="ImmutableSetMultimap{TKey,TValue}"/> (matching Guava's
/// <c>ImmutableSetMultimap</c>), while endpoints/config-item description use
/// <see cref="ImmutableListMultimap{TKey,TValue}"/>.
/// </summary>
internal static class MultimapConversions
{
    public static ImmutableListMultimap<string, string> ToListMultimap(
        ImmutableSetMultimap<string, string> setMultimap)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        foreach (var entry in setMultimap)
        {
            builder.Put(entry.Key, entry.Value);
        }

        return builder.Build();
    }
}
