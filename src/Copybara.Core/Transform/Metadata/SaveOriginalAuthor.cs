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
using Starlark.Syntax;

namespace Copybara.Transform.Metadata;

/// <summary>Saves the original author of the change in the message with a label.</summary>
public class SaveOriginalAuthor : ITransformation
{
    private readonly string _label;
    private readonly string _separator;
    private readonly Location _location;

    internal SaveOriginalAuthor(string label, string separator, Location location)
    {
        _label = Preconditions.CheckNotNull(label);
        _separator = Preconditions.CheckNotNull(separator);
        _location = Preconditions.CheckNotNull(location);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        work.AddOrReplaceLabel(_label, work.GetAuthor().ToString(), _separator);
        return TransformationStatus.Success();
    }

    public ITransformation Reverse() =>
        new RestoreOriginalAuthor(_label, _separator, searchAllChanges: false, _location);

    public string Describe() => "Saving original author";

    public Location Location() => _location;
}
