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
/// The result of executing a <see cref="Command"/>. Port of the Bazel shell library's
/// <c>CommandResult</c> subset used by Copybara.
/// </summary>
public sealed class CommandResult
{
    private readonly byte[] _stdout;
    private readonly byte[] _stderr;

    public CommandResult(byte[] stdout, byte[] stderr, TerminationStatus terminationStatus)
    {
        _stdout = stdout;
        _stderr = stderr;
        TerminationStatus = terminationStatus;
    }

    public byte[] GetStdout() => _stdout;

    public byte[] GetStderr() => _stderr;

    public TerminationStatus TerminationStatus { get; }
}
