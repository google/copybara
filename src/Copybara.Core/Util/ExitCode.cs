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
/// Exit codes to be used by the application. Port of <c>com.google.copybara.util.ExitCode</c>.
/// </summary>
public enum ExitCode
{
    /// <summary>Everything went well and the migration was successful.</summary>
    Success = 0,

    /// <summary>An error parsing the command line. For example wrong arguments/options.</summary>
    CommandLineError = 1,

    /// <summary>
    /// An error in the configuration, flags values or in general an error attributable to the user.
    /// </summary>
    ConfigurationError = 2,

    /// <summary>An error that happened during repository manipulation.</summary>
    RepositoryError = 3,

    /// <summary>
    /// Execution resulted in no-op, which means that no changes were made in the destination.
    /// </summary>
    NoOp = 4,

    /// <summary>Execution was interrupted.</summary>
    Interrupted = 8,

    /// <summary>
    /// Any error transient or permanent due to the environment (Error accessing the network,
    /// filesystem errors, etc.)
    /// </summary>
    EnvironmentError = 30,

    /// <summary>Any error that was unexpected. This would be a Copybara bug.</summary>
    InternalError = 31,
}

/// <summary>Helpers for <see cref="ExitCode"/>.</summary>
public static class ExitCodeExtensions
{
    /// <summary>Returns the integer code associated with this exit code.</summary>
    public static int GetCode(this ExitCode exitCode) => (int)exitCode;

    /// <summary>Returns the <see cref="ExitCode"/> for the given integer code.</summary>
    /// <exception cref="ArgumentException">if the code does not correspond to a known value.</exception>
    public static ExitCode ForCode(int code)
    {
        if (Enum.IsDefined(typeof(ExitCode), code))
        {
            return (ExitCode)code;
        }
        throw new ArgumentException("Invalid exit code: " + code);
    }
}
