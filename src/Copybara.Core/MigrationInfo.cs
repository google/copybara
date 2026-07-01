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

namespace Copybara;

/// <summary>Reflective information about the migration in progress.</summary>
public class MigrationInfo
{
    private readonly string? _originLabel;
    private readonly IChangeVisitable<IRevision>? _destinationVisitable;

    public MigrationInfo(string? originLabel, IChangeVisitable<IRevision>? destinationVisitable)
    {
        _originLabel = originLabel;
        _destinationVisitable = destinationVisitable;
    }

    public string GetOriginLabel() => Preconditions.CheckNotNull(_originLabel);

    public IChangeVisitable<IRevision>? DestinationVisitable() => _destinationVisitable;
}
