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

namespace Copybara;

/// <summary>
/// Lightweight replacement for JCommander's <c>@Parameter</c> annotation. Marks a property (or
/// field) as a command-line flag, recording the flag names and a description that form the CLI
/// surface. A fully featured parser will consume this metadata later; for now it primarily
/// preserves the flag names/defaults exactly as declared upstream.
/// </summary>
[AttributeUsage(AttributeTargets.Property | AttributeTargets.Field, AllowMultiple = false)]
public sealed class FlagAttribute : Attribute
{
    /// <summary>The flag names, e.g. <c>--verbose</c>, <c>-v</c>.</summary>
    public string[] Names { get; }

    /// <summary>Human-readable description shown in help output.</summary>
    public string Description { get; }

    /// <summary>Whether the flag is hidden from help output.</summary>
    public bool Hidden { get; init; }

    /// <summary>Number of values the flag consumes (JCommander's <c>arity</c>). Default -1 (unset).</summary>
    public int Arity { get; init; } = -1;

    public FlagAttribute(string name, string description)
        : this(new[] { name }, description)
    {
    }

    public FlagAttribute(string[] names, string description)
    {
        Names = names;
        Description = description;
    }
}
