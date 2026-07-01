/*
 * Copyright (C) 2018 Google Inc.
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

namespace Copybara.Git;

/// <summary>
/// Exception thrown when an invalid refspec is passed to Copybara via config, flags, etc. Port of
/// <c>com.google.copybara.git.InvalidRefspecException</c>.
/// </summary>
public class InvalidRefspecException : ValidationException
{
    public InvalidRefspecException(string message)
        : base(message)
    {
    }
}
