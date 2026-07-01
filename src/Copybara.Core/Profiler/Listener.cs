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

namespace Copybara.Profiler;

/// <summary>
/// A listener that, when registered in a <see cref="Profiler"/>, will be notified every time
/// a task is started/finished.
/// </summary>
public interface IListener
{
    /// <summary>A notification about a task that has started.</summary>
    void TaskStarted(Task task);

    /// <summary>
    /// A notification about a task finish. It is guaranteed that <see cref="Task.IsFinished"/>
    /// will return true.
    /// </summary>
    void TaskFinished(Task task);
}
