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

using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Transform;
using Starlark.Syntax;

namespace Copybara.Transform.Metadata;

/// <summary>A transformer that removes matching substrings from the change description.</summary>
public class Scrubber : ITransformation
{
    private readonly Regex _pattern;
    private readonly string _replacement;
    private readonly Location _location;
    private readonly string? _defaultPublicMsg;
    private readonly bool _failIfNoMatch;

    internal Scrubber(
        Regex pattern,
        string? defaultPublicMsg,
        bool failIfNoMatch,
        string replacement,
        Location location)
    {
        _pattern = Preconditions.CheckNotNull(pattern);
        _defaultPublicMsg = defaultPublicMsg;
        _failIfNoMatch = failIfNoMatch;
        _replacement = Preconditions.CheckNotNull(replacement);
        _location = Preconditions.CheckNotNull(location);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        try
        {
            string scrubbedMessage = _pattern.Replace(work.GetMessage(), _replacement);
            if (!work.GetMessage().Equals(scrubbedMessage))
            {
                work.GetConsole()
                    .VerboseFmt(
                        "Scrubbed change description '{0}' by '{1}'",
                        work.GetMessage(),
                        scrubbedMessage);
                work.SetMessage(scrubbedMessage);
                return TransformationStatus.Success();
            }
            ValidationException.CheckCondition(
                !_failIfNoMatch,
                "Scrubber regex: '{0}' didn't match for description: '{1}'",
                _pattern,
                work.GetMessage());
            if (_defaultPublicMsg != null)
            {
                work.SetMessage(_defaultPublicMsg);
            }
        }
        catch (ArgumentOutOfRangeException e)
        {
            throw new ValidationException(
                $"Could not find matching group. Are you missing a group in your regex '{_pattern}'?",
                e);
        }
        return TransformationStatus.Success();
    }

    public ITransformation Reverse() =>
        new ExplicitReversal(IntentionalNoop.Instance, this);

    public string Describe() => "Description scrubber";

    public Location Location() => _location;
}
