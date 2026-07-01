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

using System.Diagnostics.CodeAnalysis;

namespace Copybara.Common;

/// <summary>
/// Guava-style precondition helpers. Ported from com.google.common.base.Preconditions
/// (only the members Copybara actually uses).
/// </summary>
public static class Preconditions
{
    /// <summary>Ensures <paramref name="reference"/> is not null, returning it.</summary>
    public static T CheckNotNull<T>([NotNull] T? reference) where T : class
    {
        if (reference is null)
        {
            throw new ArgumentNullException();
        }
        return reference;
    }

    /// <summary>Ensures <paramref name="reference"/> is not null, returning it.</summary>
    public static T CheckNotNull<T>([NotNull] T? reference, object? errorMessage) where T : class
    {
        if (reference is null)
        {
            throw new ArgumentNullException(null, errorMessage?.ToString());
        }
        return reference;
    }

    /// <summary>Ensures <paramref name="reference"/> is not null, returning it.</summary>
    public static T CheckNotNull<T>([NotNull] T? reference, string format, params object?[] args)
        where T : class
    {
        if (reference is null)
        {
            throw new ArgumentNullException(null, string.Format(format, args));
        }
        return reference;
    }

    /// <summary>Ensures the truth of an expression involving parameters to the calling method.</summary>
    public static void CheckArgument([DoesNotReturnIf(false)] bool expression)
    {
        if (!expression)
        {
            throw new ArgumentException();
        }
    }

    /// <summary>Ensures the truth of an expression involving parameters to the calling method.</summary>
    public static void CheckArgument([DoesNotReturnIf(false)] bool expression, object? errorMessage)
    {
        if (!expression)
        {
            throw new ArgumentException(errorMessage?.ToString());
        }
    }

    /// <summary>Ensures the truth of an expression involving parameters to the calling method.</summary>
    public static void CheckArgument(
        [DoesNotReturnIf(false)] bool expression, string format, params object?[] args)
    {
        if (!expression)
        {
            throw new ArgumentException(string.Format(format, args));
        }
    }

    /// <summary>Ensures the truth of an expression involving the state of the calling instance.</summary>
    public static void CheckState([DoesNotReturnIf(false)] bool expression)
    {
        if (!expression)
        {
            throw new InvalidOperationException();
        }
    }

    /// <summary>Ensures the truth of an expression involving the state of the calling instance.</summary>
    public static void CheckState([DoesNotReturnIf(false)] bool expression, object? errorMessage)
    {
        if (!expression)
        {
            throw new InvalidOperationException(errorMessage?.ToString());
        }
    }

    /// <summary>Ensures the truth of an expression involving the state of the calling instance.</summary>
    public static void CheckState(
        [DoesNotReturnIf(false)] bool expression, string format, params object?[] args)
    {
        if (!expression)
        {
            throw new InvalidOperationException(string.Format(format, args));
        }
    }
}
