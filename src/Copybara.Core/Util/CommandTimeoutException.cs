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

using Copybara.Common;

namespace Copybara.Util;

/// <summary>
/// An exception that represents a program that timed out and was killed. Port of
/// <c>com.google.copybara.util.CommandTimeoutException</c>.
/// </summary>
public class CommandTimeoutException : AbnormalTerminationException
{
    public CommandTimeoutException(
        Command command,
        CommandResult result,
        string message,
        byte[] stdout,
        byte[] stderr,
        TimeSpan timeout)
        : base(Preconditions.CheckNotNull(command), result, Preconditions.CheckNotNull(message))
    {
        Timeout = timeout;
        Output = new CommandOutputWithStatus(
            result.TerminationStatus,
            Preconditions.CheckNotNull(stdout),
            Preconditions.CheckNotNull(stderr));
    }

    public CommandOutputWithStatus Output { get; }

    public TimeSpan Timeout { get; }

    public CommandOutputWithStatus GetOutput() => Output;

    public TimeSpan GetTimeout() => Timeout;
}
