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

using Copybara.Common;
using Copybara.Transform;
using Starlark.Syntax;

namespace Copybara.Transform.Metadata;

/// <summary>Removes a label from the change message.</summary>
public class RemoveLabelInMessage : ITransformation
{
    private readonly string _label;
    private readonly Location _location;

    internal RemoveLabelInMessage(string label, Location location)
    {
        _label = Preconditions.CheckNotNull(label);
        _location = Preconditions.CheckNotNull(location);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        string message = work.GetMessage();
        work.RemoveLabel(_label, wholeMessage: false);
        // Lets try to find the message in all the text.
        if (work.GetMessage().Equals(message))
        {
            work.RemoveLabel(_label, wholeMessage: true);
        }
        return TransformationStatus.Success();
    }

    public ITransformation Reverse() =>
        new ExplicitReversal(IntentionalNoop.Instance, this);

    public string Describe() => "Removing label " + _label;

    public Location Location() => _location;
}
