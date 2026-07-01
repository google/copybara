/*
 * Copyright (C) 2019 Google Inc.
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

namespace Copybara.Profiler;

/// <summary>
/// A profiler listener storing all completed tasks.
/// </summary>
public class RecordingListener : IListener
{
    private readonly List<Task> _finishedTasks = new();
    private readonly object _lock = new();

    public void TaskStarted(Task task)
    {
        // Ignored. We only record the finish event.
    }

    public void TaskFinished(Task task)
    {
        // For now, just finished tasks. In the future we should consider exporting open tasks as they
        // might help pinpoint timeouts.
        lock (_lock)
        {
            _finishedTasks.Add(task);
        }
    }

    /// <summary>List of all completed tasks, immutable.</summary>
    public IReadOnlyList<Task> CompletedTasks
    {
        get
        {
            lock (_lock)
            {
                return _finishedTasks.ToImmutableArray();
            }
        }
    }
}
