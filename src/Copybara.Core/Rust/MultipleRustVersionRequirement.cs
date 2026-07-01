/*
 * Copyright (C) 2023 Google Inc.
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

using System.Collections.Immutable;
using System.Linq;
using Copybara.Exceptions;

namespace Copybara.Rust;

/// <summary>A class that handles multiple <see cref="RustVersionRequirement"/> instances.</summary>
public class MultipleRustVersionRequirement : RustVersionRequirement
{
    private readonly ImmutableArray<RustVersionRequirement> _requirements;

    /// <summary>Returns true if this class can handle the given Cargo version requirement.</summary>
    public static bool HandlesRequirement(string requirement) =>
        SplitMultipleRequirements(requirement).Length > 1;

    /// <exception cref="ValidationException"/>
    public static MultipleRustVersionRequirement Create(string requirement) => new(requirement);

    /// <exception cref="ValidationException"/>
    private MultipleRustVersionRequirement(string requirement)
        : base(requirement)
    {
        ImmutableArray<string> requirementStrings = SplitMultipleRequirements(requirement);

        var requirementsBuilder = ImmutableArray.CreateBuilder<RustVersionRequirement>();
        try
        {
            foreach (string requirementString in requirementStrings)
            {
                requirementsBuilder.Add(GetVersionRequirement(requirementString, false));
            }
        }
        catch (ValidationException e)
        {
            throw new ValidationException(
                $"The requirement {requirement} is not a valid multiple version requirement.", e);
        }

        _requirements = requirementsBuilder.ToImmutable();
    }

    private static ImmutableArray<string> SplitMultipleRequirements(string requirement) =>
        requirement
            .Split(',')
            .Select(s => s.Trim())
            .Where(s => s.Length > 0)
            .ToImmutableArray();

    /// <exception cref="ValidationException"/>
    public override bool Fulfills(string version)
    {
        foreach (RustVersionRequirement requirement in _requirements)
        {
            if (!requirement.Fulfills(version))
            {
                return false;
            }
        }

        return true;
    }
}
