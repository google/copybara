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

using Copybara.Common;
using Copybara.Exceptions;
using Copybara.TemplateToken;
using Copybara.Transform;
using Starlark.Syntax;

namespace Copybara.Transform.Metadata;

/// <summary>
/// Adds a header text on top of the change message.
///
/// <para>This transform allows referring to change labels both from the current message or the set
/// of commits being imported.</para>
/// </summary>
public class TemplateMessage : ITransformation
{
    private readonly bool _ignoreIfLabelNotFound;
    private readonly bool _newLine;
    private readonly bool _replaceMessage;
    private readonly LabelTemplate _labelTemplate;
    private readonly Location _location;

    internal TemplateMessage(
        string header,
        bool ignoreIfLabelNotFound,
        bool newLine,
        bool replaceMessage,
        Location location)
    {
        _ignoreIfLabelNotFound = ignoreIfLabelNotFound;
        _newLine = newLine;
        _replaceMessage = replaceMessage;
        _labelTemplate = new LabelTemplate(header);
        _location = Preconditions.CheckNotNull(location);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        string newMsg;
        try
        {
            newMsg = _labelTemplate.Resolve(work.GetLabel);
        }
        catch (LabelTemplate.LabelNotFoundException e)
        {
            if (_ignoreIfLabelNotFound)
            {
                return TransformationStatus.Success();
            }
            throw new ValidationException(
                $"Cannot find label '{e.Label}' in message:\n {work.GetMessage()}\nor any of the"
                + " original commit messages");
        }

        if (!_replaceMessage)
        {
            newMsg += (_newLine ? "\n" : "") + work.GetMessage();
        }
        work.SetMessage(newMsg);
        return TransformationStatus.Success();
    }

    public ITransformation Reverse() =>
        new ExplicitReversal(IntentionalNoop.Instance, this);

    public string Describe() => "Adding header to the message";

    public Location Location() => _location;
}
