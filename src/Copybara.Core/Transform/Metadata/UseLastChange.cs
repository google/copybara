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
using Copybara.Revision;
using Copybara.Transform;
using Starlark.Syntax;

namespace Copybara.Transform.Metadata;

/// <summary>
/// Use metadata (i.e. message/author) from the last change being migrated. Useful when using
/// 'SQUASH' mode but user only cares about the last change.
/// </summary>
public class UseLastChange : ITransformation
{
    private readonly bool _useMessage;
    private readonly bool _useAuthor;
    private readonly string? _defaultMessage;
    private readonly bool _useMerge;
    private readonly Location _location;

    internal UseLastChange(
        bool useAuthor,
        bool useMessage,
        string? defaultMessage,
        bool useMerge,
        Location location)
    {
        _useAuthor = useAuthor;
        _useMessage = useMessage;
        _defaultMessage = defaultMessage;
        _useMerge = useMerge;
        _location = Preconditions.CheckNotNull(location);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        Change<IRevision>? lastChange = GetLastChange(work);
        if (lastChange == null)
        {
            if (_useMessage && _defaultMessage != null)
            {
                work.SetMessage(_defaultMessage);
            }
            return TransformationStatus.Success();
        }
        if (_useMessage)
        {
            work.SetMessage(lastChange.GetMessage());
        }
        if (_useAuthor)
        {
            work.SetAuthor(lastChange.GetMappedAuthor());
        }
        return TransformationStatus.Success();
    }

    private Change<IRevision>? GetLastChange(TransformWork work)
    {
        foreach (var changeObj in work.GetChanges().GetCurrent())
        {
            var change = (Change<IRevision>)changeObj;
            if (!_useMerge && change.IsMerge())
            {
                continue;
            }
            return change;
        }
        return null;
    }

    public ITransformation Reverse() =>
        new ExplicitReversal(IntentionalNoop.Instance, this);

    public string Describe() => "Use last change metadata";

    public Location Location() => _location;
}
