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

using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace Copybara.Profiler;

/// <summary>
/// A simple callback for the profiler that logs the execution of the tasks when they finish.
/// </summary>
///
/// <remarks>
/// The Java original injects the caller's log site via a <c>StackWalker</c> so the log message is
/// attributed to the caller rather than to the profiler. That log-site injection has no equivalent
/// in <c>Microsoft.Extensions.Logging</c>, so this port logs at Information level directly.
/// </remarks>
public class LogProfilerListener : IListener
{
    private readonly ILogger _logger;

    public LogProfilerListener()
        : this(NullLogger.Instance)
    {
    }

    public LogProfilerListener(ILogger logger)
    {
        _logger = logger;
    }

    public void TaskStarted(Task task)
    {
        // Ignored. We only record the finish event.
    }

    public void TaskFinished(Task task)
    {
        long millis = task.ElapsedNanos() / 1_000_000;
        _logger.LogInformation("PROFILE: {Millis,6} {Description}", millis, task.Description);
    }
}
