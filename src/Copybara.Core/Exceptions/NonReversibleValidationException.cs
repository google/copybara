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

using Starlark.Eval;

namespace Copybara.Exceptions;

/// <summary>
/// Exception thrown when a Transformation is not reversible but the configuration asked for the
/// reverse.
/// </summary>
public class NonReversibleValidationException : EvalException
{
    public NonReversibleValidationException(string message)
        : base(message)
    {
    }

    public NonReversibleValidationException(string message, Exception? cause)
        : base(message, cause)
    {
    }
}
