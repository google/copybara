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
using Copybara.Revision;

namespace Copybara.Hg;

/// <summary>A Hg repository revision (changeset).</summary>
public class HgRevision : IRevision
{
    private readonly string _globalId;
    private readonly string? _reference;

    /// <summary>
    /// Creates a hg revision from a hexadecimal string identifier. Currently, Mercurial uses SHA1 to
    /// hash revisions.
    /// </summary>
    /// <param name="globalId">global identifier for the revision</param>
    public HgRevision(string globalId)
    {
        _globalId = Preconditions.CheckNotNull(globalId);
        _reference = null;
    }

    /// <summary>
    /// Creates a hg revision from a hexadecimal string identifier. Currently, Mercurial uses SHA1 to
    /// hash revisions.
    /// </summary>
    /// <param name="globalId">global identifier for the revision</param>
    /// <param name="reference">The reference provided by the user (i.e. 'tip')</param>
    public HgRevision(string globalId, string reference)
    {
        _globalId = Preconditions.CheckNotNull(globalId);
        _reference = Preconditions.CheckNotNull(reference);
    }

    public string AsString() => _globalId;

    public string? ContextReference() => _reference;

    // TODO(jlliu): properly implement after LogCmd is implemented
    public DateTimeOffset? ReadTimestamp() => null;

    public override string ToString() => $"HgRevision{{global ID={_globalId}}}";

    internal string GetGlobalId() => _globalId;
}
