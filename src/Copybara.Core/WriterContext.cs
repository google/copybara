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

using System.Collections.Immutable;
using Copybara.Common;
using Copybara.Revision;

namespace Copybara;

/// <summary>Writer context which includes all the information for creating a writer.</summary>
public class WriterContext
{
    private readonly string _workflowName;
    private readonly string _workflowIdentityUser;
    private readonly bool _dryRun;
    private readonly IRevision _originalRevision;
    private readonly ImmutableHashSet<string> _roots;

    public WriterContext(
        string workflowName,
        string? workflowIdentityUser,
        bool dryRun,
        IRevision originalRevision,
        ImmutableHashSet<string> roots)
    {
        _workflowName = Preconditions.CheckNotNull(workflowName);
        _workflowIdentityUser = workflowIdentityUser ?? Environment.UserName;
        _dryRun = dryRun;
        _originalRevision = Preconditions.CheckNotNull(originalRevision);
        _roots = Preconditions.CheckNotNull(roots);
    }

    public IRevision GetOriginalRevision() => _originalRevision;

    public string GetWorkflowIdentityUser() => _workflowIdentityUser;

    public string GetWorkflowName() => _workflowName;

    public bool IsDryRun() => _dryRun;

    public ImmutableHashSet<string> GetRoots() => _roots;
}
