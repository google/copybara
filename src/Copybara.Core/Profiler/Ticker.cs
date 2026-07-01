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

using System.Diagnostics;

namespace Copybara.Profiler;

/// <summary>
/// A time source that returns a monotonic reading in nanoseconds. Port of Guava's
/// <c>com.google.common.base.Ticker</c> restricted to the surface the <see cref="Profiler"/> uses.
/// </summary>
public abstract class Ticker
{
    /// <summary>Returns the number of nanoseconds elapsed since this ticker's fixed point.</summary>
    public abstract long Read();

    /// <summary>
    /// A ticker backed by <see cref="Stopwatch.GetTimestamp"/>, converted to nanoseconds. This is the
    /// .NET equivalent of Guava's <c>Ticker.systemTicker()</c> / <c>System.nanoTime()</c>.
    /// </summary>
    public static Ticker SystemTicker { get; } = new SystemTickerImpl();

    private sealed class SystemTickerImpl : Ticker
    {
        private static readonly double NanosPerTick = 1_000_000_000.0 / Stopwatch.Frequency;

        public override long Read() => (long)(Stopwatch.GetTimestamp() * NanosPerTick);
    }
}
