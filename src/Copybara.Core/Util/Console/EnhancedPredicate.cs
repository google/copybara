/*
 * Copyright (C) 2021 Google Inc.
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

namespace Copybara.Util.Console;

/// <summary>Enhanced Predicate object for use with Copybara console objects.</summary>
public sealed class EnhancedPredicate
{
    private readonly Func<string, bool> _predicate;
    private readonly string _errorMsg;

    private EnhancedPredicate(Func<string, bool> predicate, string errorMsg)
    {
        _predicate = predicate;
        _errorMsg = errorMsg;
    }

    public static EnhancedPredicate Create(Func<string, bool> predicate, string errorMsg)
    {
        return new EnhancedPredicate(
            Preconditions.CheckNotNull(predicate), Preconditions.CheckNotNull(errorMsg));
    }

    /// <summary>Tests <paramref name="value"/> against the underlying predicate.</summary>
    public bool Predicate(string value) => _predicate(value);

    public string ErrorMsg => _errorMsg;
}
