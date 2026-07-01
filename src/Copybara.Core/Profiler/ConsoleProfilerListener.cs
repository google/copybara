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

using Copybara.Common;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Profiler;

/// <summary>
/// A profiler <see cref="IListener"/> that prints profiling stats to the console in verbose mode.
/// </summary>
public class ConsoleProfilerListener : IListener
{
    private readonly Console _console;

    public ConsoleProfilerListener(Console console)
    {
        _console = Preconditions.CheckNotNull(console);
    }

    public void TaskStarted(Task task)
    {
        // Ignored. We only record the finish event.
    }

    public void TaskFinished(Task task)
    {
        _console.VerboseFmt("PROFILE: %6d %s", task.ElapsedNanos() / 1_000_000, task.Description);
    }
}
