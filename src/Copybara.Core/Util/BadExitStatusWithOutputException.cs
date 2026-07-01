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
/// An exception that represents a program that did not exit with a 0 exit code, while retaining the
/// collected stdout/stderr. Port of
/// <c>com.google.copybara.util.BadExitStatusWithOutputException</c>.
/// </summary>
public class BadExitStatusWithOutputException : AbnormalTerminationException
{
    public BadExitStatusWithOutputException(
        Command command, CommandResult result, string message, byte[] stdout, byte[] stderr)
        : base(command, result, message)
    {
        Output = new CommandOutputWithStatus(result.TerminationStatus, stdout, stderr);
    }

    public CommandOutputWithStatus Output { get; }

    public CommandOutputWithStatus GetOutput() => Output;
}
