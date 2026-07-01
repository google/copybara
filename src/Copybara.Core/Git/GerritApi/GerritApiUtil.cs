/*
 * Copyright (C) 2017 Google Inc.
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

using System.Globalization;

namespace Copybara.Git.GerritApi;

/// <summary>Utilities for dealing with Gerrit API.</summary>
public static class GerritApiUtil
{
    /// <summary>
    /// Parses dates like "2014-12-21 17:30:08.000000000".
    /// </summary>
    /// <remarks>
    /// NOTE(port): Java uses <c>DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.n")</c> where
    /// <c>n</c> is nano-of-second (up to 9 digits) and the zone is fixed to UTC. .NET's custom format
    /// uses <c>fffffffff</c> for 9 fractional-second digits; parsing yields a UTC
    /// <see cref="DateTimeOffset"/>.
    /// </remarks>
    private const string TimestampFormat = "yyyy-MM-dd HH:mm:ss.fffffffff";

    /// <summary>Parses a Gerrit timestamp into a UTC <see cref="DateTimeOffset"/>.</summary>
    public static DateTimeOffset ParseTimestamp(string date)
    {
        // AssumeUniversal + AdjustToUniversal mirrors Java's .withZone(ZoneOffset.UTC).
        var dt = DateTime.ParseExact(
            date,
            TimestampFormat,
            CultureInfo.InvariantCulture,
            DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal);
        return new DateTimeOffset(dt, TimeSpan.Zero);
    }
}
