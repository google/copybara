/*
 * Copyright (C) 2022 Google Inc.
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

using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;
using Starlark.Syntax;

namespace Copybara;

/// <summary>Starlark wrapper for zoned datetimes.</summary>
[StarlarkBuiltin("datetime", Doc = "Module for datetime manipulation.")]
public sealed class StarlarkDateTimeModule : IStarlarkValue
{
    [StarlarkMethod("now",
        Doc = "Returns a starlark_datetime object. The object is timezone aware.")]
    public StarlarkDateTime CreateFromNow(
        [Param(Name = "tz", Named = true, DefaultValue = "'America/Los_Angeles'",
            Doc = "The timezone. E.g. America/New_York, Asia/Tokyo, Europe/Rome",
            AllowedTypes = new[] { typeof(string) })]
        string zoneIdString) =>
        new(zoneIdString);

    [StarlarkMethod("fromtimestamp",
        Doc =
            "Returns a starlark_datetime object representation of the epoch time. The object is"
            + " timezone aware.")]
    public StarlarkDateTime CreateFromEpochSeconds(
        [Param(Name = "timestamp", Named = true, DefaultValue = "0",
            Doc = "Epoch time in seconds.",
            AllowedTypes = new[] { typeof(StarlarkInt) })]
        StarlarkInt timeInEpochSeconds,
        [Param(Name = "tz", Named = true, DefaultValue = "'America/Los_Angeles'",
            Doc = "The timezone. E.g. America/New_York, Asia/Tokyo, Europe/Rome, etc.",
            AllowedTypes = new[] { typeof(string) })]
        string zoneId) =>
        new(timeInEpochSeconds.ToLong("timestamp"), zoneId);

    /// <summary>The Starlark-facing wrapper for a zoned datetime.</summary>
    [StarlarkBuiltin("StarlarkDateTime", Doc = "Starlark datetime object")]
    public sealed class StarlarkDateTime : IStarlarkValue, IHasBinary
    {
        private readonly DateTimeOffset _dateTime;
        private readonly TimeZoneInfo _zone;

        public StarlarkDateTime(string zoneIdString)
        {
            _zone = ConvertStringToZone(zoneIdString);
            _dateTime = TimeZoneInfo.ConvertTime(DateTimeOffset.UtcNow, _zone);
        }

        public StarlarkDateTime(long timeInEpochSeconds, string zoneIdString)
        {
            _zone = ConvertStringToZone(zoneIdString);
            _dateTime = TimeZoneInfo.ConvertTime(
                DateTimeOffset.FromUnixTimeSeconds(timeInEpochSeconds), _zone);
        }

        public object? BinaryOp(TokenKind op, object that, bool thisLeft)
        {
            if (that is not StarlarkDateTime otherDateTime)
            {
                throw new EvalException(
                    "Binary operators are supported between StarkDateTime objects only.");
            }

            switch (op)
            {
                case TokenKind.MINUS:
                    // TODO(port): PLUS between StarlarkDatetime and StarlarkTimeDelta in the future.
                    long seconds = (long)(_dateTime - otherDateTime._dateTime).TotalSeconds;
                    return new StarlarkTimeDelta(seconds);
                default:
                    throw new EvalException($"Glob does not support {op} operator");
            }
        }

        private static TimeZoneInfo ConvertStringToZone(string zoneIdString)
        {
            try
            {
                return string.IsNullOrEmpty(zoneIdString)
                    ? TimeZoneInfo.Local
                    : TimeZoneInfo.FindSystemTimeZoneById(zoneIdString);
            }
            catch (Exception e) when (e is TimeZoneNotFoundException or InvalidTimeZoneException)
            {
                throw new ValidationException(
                    "An error was thrown creating StarlarkDateTime from zone id. Make sure your"
                        + " timezone is available in the system tz database",
                    e);
            }
        }

        [StarlarkMethod("in_epoch_seconds",
            Doc = "Returns the time in epoch seconds for the starlark_datetime instance")]
        public long GetTimeInEpochSeconds() => _dateTime.ToUnixTimeSeconds();

        [StarlarkMethod("strftime",
            Doc =
                "Returns a string representation of the StarlarkDateTime object with your chosen"
                + " formatting")]
        public string FormatToString(
            [Param(Name = "format", Named = true,
                Doc =
                    "Format string used to present StarlarkDateTime object. See"
                    + " https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html"
                    + " for patterns.",
                AllowedTypes = new[] { typeof(string) })]
            string format)
        {
            // NOTE(port): upstream uses Java's DateTimeFormatter patterns. This port uses .NET custom
            // format strings; the majority of simple patterns behave similarly but complex Java
            // patterns may diverge. This is an accepted deviation for the .NET port.
            try
            {
                return _dateTime.ToString(format, System.Globalization.CultureInfo.InvariantCulture);
            }
            catch (FormatException e)
            {
                throw new ValidationException(
                    $"The StarlarkDateTime object '{this}' could not be formatted using format"
                        + $" string '{format}':",
                    e);
            }
        }

        public override string ToString() =>
            _dateTime.ToString("yyyy-MM-ddTHH:mm:sszzz",
                System.Globalization.CultureInfo.InvariantCulture);

        public override bool Equals(object? obj) =>
            obj is StarlarkDateTime other && _dateTime.Equals(other._dateTime);

        public override int GetHashCode() => _dateTime.GetHashCode();
    }

    /// <summary>Time delta, used to do binary operations with Starlark Datetime.</summary>
    [StarlarkBuiltin("time_delta", Doc = "A time delta.")]
    public sealed class StarlarkTimeDelta : IStarlarkValue
    {
        private readonly TimeSpan _duration;

        public StarlarkTimeDelta(long seconds)
        {
            _duration = TimeSpan.FromSeconds(seconds);
        }

        [StarlarkMethod("total_seconds",
            Doc = "Total number of seconds in a timedelta object.")]
        public long TotalSeconds() => (long)_duration.TotalSeconds;

        // TODO(port): implement timedelta + StarlarkDatetime.
    }
}
