/*
 * Copyright (C) 2020 Google Inc.
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

namespace Copybara.Exceptions;

/// <summary>
/// A special case of <see cref="ValidationException"/> when the error is likely to be a user error
/// related to access to the repo (access denied, not found, etc.)
/// </summary>
public class AccessValidationException : ValidationException
{
    public AccessValidationException(string message)
        : base(message)
    {
    }

    public AccessValidationException(string message, Exception? cause)
        : base(message, cause)
    {
    }
}
