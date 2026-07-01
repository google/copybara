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

using System.Text;
using Copybara.Common;

namespace Copybara.Util;

/// <summary>
/// Holds the <c>stdout</c> and <c>stderr</c> contents of a command execution. Port of
/// <c>com.google.copybara.util.CommandOutput</c>.
/// </summary>
public class CommandOutput
{
    private readonly byte[] _stdout;
    private readonly byte[] _stderr;

    public CommandOutput(byte[] stdout, byte[] stderr)
    {
        _stdout = Preconditions.CheckNotNull(stdout);
        _stderr = Preconditions.CheckNotNull(stderr);
    }

    public string GetStdout() => Encoding.UTF8.GetString(_stdout);

    public byte[] GetStdoutBytes() => _stdout;

    public string GetStderr() => Encoding.UTF8.GetString(_stderr);

    public override string ToString() =>
        $"CommandOutput{{stdout={GetStdout()}, stderr={GetStderr()}}}";
}
