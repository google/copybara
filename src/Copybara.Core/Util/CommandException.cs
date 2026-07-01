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
/// Superclass of all exceptions that may be thrown during command execution. Port of the Bazel
/// shell library's <c>CommandException</c>.
/// </summary>
public class CommandException : Exception
{
    public Command Command { get; }

    public CommandException(Command command, string message) : base(message) => Command = command;

    public CommandException(Command command, Exception cause) : base(cause.Message, cause) =>
        Command = command;

    public CommandException(Command command, string message, Exception? cause)
        : base(message, cause) => Command = command;

    public Command GetCommand() => Command;
}

/// <summary>
/// Thrown when a command terminates abnormally (non-zero exit, killed by a signal, etc.). Port of
/// the Bazel shell library's <c>AbnormalTerminationException</c>.
/// </summary>
public class AbnormalTerminationException : CommandException
{
    public CommandResult Result { get; }

    public AbnormalTerminationException(Command command, CommandResult result, string message)
        : base(command, message) => Result = result;

    public AbnormalTerminationException(
        Command command, CommandResult result, string message, Exception? cause)
        : base(command, message, cause) => Result = result;

    public CommandResult GetResult() => Result;
}

/// <summary>
/// Thrown when a command exits with a non-zero exit code. Port of the Bazel shell library's
/// <c>BadExitStatusException</c>.
/// </summary>
public class BadExitStatusException : AbnormalTerminationException
{
    public BadExitStatusException(Command command, CommandResult result, string message)
        : base(command, result, message)
    {
    }
}
