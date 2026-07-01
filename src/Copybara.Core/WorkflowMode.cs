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

namespace Copybara;

/// <summary>
/// Workflow type to run between origin and destination.
/// </summary>
// In upstream Java, each enum constant carries a per-mode `run(WorkflowRunHelper)` implementation
// (SQUASH / ITERATIVE / CHANGE_REQUEST / CHANGE_REQUEST_FROM_SOT). C# enums cannot carry behavior;
// the run() dispatch and its helpers (FilterChanges, MaybeGetLastRev, IsHistorySupported, etc.) live
// in <see cref="WorkflowModeRunner"/> and are invoked from <see cref="Workflow{O,D}.Run"/>.
public enum WorkflowMode
{
    /// <summary>
    /// Create a single commit in the destination with new tree state.
    /// </summary>
    Squash,

    /// <summary>
    /// Import each origin change individually.
    /// </summary>
    Iterative,

    /// <summary>
    /// Import an origin tree state diffed by a common parent in destination. This could be a GH
    /// Pull Request, a Gerrit Change, etc.
    /// </summary>
    ChangeRequest,

    /// <summary>
    /// Import <b>from</b> the Source-of-Truth. This mode is useful when, despite the pending change
    /// being already in the SoT, the users want to review the code on a different system.
    /// </summary>
    ChangeRequestFromSot,
}
