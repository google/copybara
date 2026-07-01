/*
 * Copyright (C) 2017 Google Inc.
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

using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Transform;
using Starlark.Syntax;

namespace Copybara.Transform.Metadata;

/// <summary>
/// A checker that validates that the change description satisfies a Regex or that it doesn't if
/// verifyNoMatch is set.
/// </summary>
public class MetadataVerifyMatch : ITransformation
{
    private readonly Regex _pattern;
    private readonly bool _verifyNoMatch;
    private readonly Location _location;

    internal MetadataVerifyMatch(Regex pattern, bool verifyNoMatch, Location location)
    {
        _pattern = Preconditions.CheckNotNull(pattern);
        _verifyNoMatch = verifyNoMatch;
        _location = Preconditions.CheckNotNull(location);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        bool found = _pattern.IsMatch(work.GetMessage());
        ValidationException.CheckCondition(
            found || _verifyNoMatch,
            "Could not find '{0}' in the change message. Message was:\n{1}",
            _pattern,
            work.GetMessage());

        ValidationException.CheckCondition(
            !found || !_verifyNoMatch,
            "'{0}' found in the change message. Message was:\n{1}",
            _pattern,
            work.GetMessage());
        return TransformationStatus.Success();
    }

    public ITransformation Reverse() =>
        new ExplicitReversal(IntentionalNoop.Instance, this);

    public string Describe() =>
        $"Verify message {(_verifyNoMatch ? "does not match" : "matches")} '{_pattern}'";

    public Location Location() => _location;
}
