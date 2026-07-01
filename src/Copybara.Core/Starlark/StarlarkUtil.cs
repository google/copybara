/*
 * Copyright (C) 2022 Google Inc.
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

using StarlarkRt = Starlark.Eval.Starlark;
using Starlark.Eval;

namespace Copybara.StarlarkSupport;

/// <summary>
/// Utilities for dealing with the Starlark language. Port of
/// <c>com.google.copybara.starlark.StarlarkUtil</c>.
/// </summary>
public static class StarlarkUtil
{
    /// <summary>Checks a condition or throws <see cref="EvalException"/>.</summary>
    /// <exception cref="EvalException"/>
    public static void Check(bool condition, string format, params object?[] args)
    {
        if (!condition)
        {
            throw StarlarkRt.Errorf(format, args);
        }
    }
}
