/*
 * Copyright (C) 2022 Google LLC
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
using ConsoleT = Copybara.Util.Console.Console;

namespace Copybara.Approval;

/// <summary>
/// An approvals validator that is provided by the origin. Port of
/// <c>com.google.copybara.approval.ApprovalsProvider</c>.
/// </summary>
public interface IApprovalsProvider
{
    /// <summary>
    /// Given a list of changes, return a list of changes that have approvals.
    /// </summary>
    /// <param name="changes">changes to be verified with the existing approvals.</param>
    /// <param name="labelFinder">
    /// describes how to find label inputs in case the labels can't be found among
    /// <paramref name="changes"/>.
    /// </param>
    /// <param name="console">console, in case some message need to be printed.</param>
    /// <exception cref="Copybara.Exceptions.RepoException">
    /// if access to the origin system fails because of being unavailable, server error, etc.
    /// </exception>
    /// <exception cref="Copybara.Exceptions.ValidationException">
    /// if failure is attributable to the user setup (e.g. permission errors, etc.).
    /// </exception>
    ApprovalsResult ComputeApprovals(
        ImmutableArray<ChangeWithApprovals> changes,
        Func<string, IReadOnlyCollection<string>>? labelFinder,
        ConsoleT console);
}

/// <summary>
/// An object containing the approvals found for a set of changes.
///
/// <para>The purpose of this class is to make it easier to migrate to attestations in the future. For
/// example, storing general information about the source.</para>
/// </summary>
public sealed class ApprovalsResult
{
    private readonly ImmutableArray<ChangeWithApprovals> _changes;

    public ApprovalsResult(ImmutableArray<ChangeWithApprovals> changes)
    {
        _changes = changes;
    }

    /// <summary>
    /// List of changes with their corresponding approvals. Must be the complete list of changes, with
    /// or without any approval.
    /// </summary>
    public IReadOnlyList<ChangeWithApprovals> GetChanges() => _changes;
}
