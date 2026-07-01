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

using System.Globalization;
using Copybara.Exceptions;

namespace Copybara.Cli;

/// <summary>
/// Converts strings like <c>10s</c>/<c>10m</c>/<c>10h</c>/<c>10d</c> to a <see cref="TimeSpan"/>.
/// Port of <c>com.google.copybara.jcommander.DurationConverter</c>.
/// </summary>
public static class DurationConverter
{
    public static TimeSpan Convert(string value)
    {
        if (value.Length < 2)
        {
            throw DurationException(value);
        }

        if (!int.TryParse(
                value.AsSpan(0, value.Length - 1),
                NumberStyles.Integer,
                CultureInfo.InvariantCulture,
                out int num)
            || num < 0)
        {
            throw DurationException(value);
        }

        char unit = value[^1];
        return unit switch
        {
            's' => TimeSpan.FromSeconds(num),
            'm' => TimeSpan.FromMinutes(num),
            'h' => TimeSpan.FromHours(num),
            'd' => TimeSpan.FromDays(num),
            _ => throw DurationException(value),
        };
    }

    private static CommandLineException DurationException(string value) =>
        new(string.Format(
            "Invalid value for duration '{0}', valid value examples: 10s, 10m, 10h or 10d", value));
}
