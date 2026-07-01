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

namespace Copybara.Util;

/// <summary>
/// Represents the termination status of a subprocess. Port of the Bazel shell library's
/// <c>TerminationStatus</c>. Follows the Unix convention where a value of 128+signal indicates the
/// process was terminated by a signal.
/// </summary>
public sealed class TerminationStatus : IEquatable<TerminationStatus>
{
    // We rely on the convention that the raw wait result is the exit status when the process exits
    // normally, or 128+signalnumber when the process is terminated by a signal (signals in [1, 63]).
    private const int Signal1 = 128 + 1;
    private const int Signal63 = 128 + 63;

    private static readonly string[] SignalStrings =
    {
        "Signal 0",
        "Hangup",
        "Interrupt",
        "Quit",
        "Illegal instruction",
        "Trace/breakpoint trap",
        "Aborted",
        "Bus error",
        "Floating point exception",
        "Killed",
        "User defined signal 1",
        "Segmentation fault",
        "User defined signal 2",
        "Broken pipe",
        "Alarm clock",
        "Terminated",
    };

    private readonly int _waitResult;

    public TerminationStatus(int waitResult) => _waitResult = waitResult;

    private static string GetSignalString(int signum) =>
        signum > 0 && signum < SignalStrings.Length
            ? SignalStrings[signum]
            : "Signal " + signum;

    /// <summary>
    /// Returns the "raw" wait result. This value is not precisely defined; use
    /// <see cref="GetExitCode"/> instead where possible.
    /// </summary>
    internal int GetRawResult() => _waitResult;

    /// <summary>Returns true iff the process exited with code 0.</summary>
    public bool Success() => Exited() && GetExitCode() == 0;

    /// <summary>Returns true iff the process exited normally.</summary>
    public bool Exited() => _waitResult < Signal1 || _waitResult > Signal63;

    /// <summary>Returns the exit code of the subprocess. Undefined if <see cref="Exited"/> is false.</summary>
    public int GetExitCode()
    {
        if (!Exited())
        {
            throw new InvalidOperationException("GetExitCode() not defined");
        }
        return _waitResult;
    }

    /// <summary>
    /// Returns the number of the signal that terminated the process. Undefined if
    /// <see cref="Exited"/> returns true.
    /// </summary>
    public int GetTerminatingSignal()
    {
        if (Exited())
        {
            throw new InvalidOperationException("GetTerminatingSignal() not defined");
        }
        return _waitResult - Signal1 + 1;
    }

    /// <summary>A short string describing the termination status, e.g. "Exit 1" or "Hangup".</summary>
    public string ToShortString() =>
        Exited() ? "Exit " + GetExitCode() : GetSignalString(GetTerminatingSignal());

    public override string ToString() =>
        Exited()
            ? "Process exited with status " + GetExitCode()
            : "Process terminated by signal " + GetTerminatingSignal();

    public override int GetHashCode() => _waitResult;

    public bool Equals(TerminationStatus? other) => other is not null && other._waitResult == _waitResult;

    public override bool Equals(object? obj) => Equals(obj as TerminationStatus);
}
