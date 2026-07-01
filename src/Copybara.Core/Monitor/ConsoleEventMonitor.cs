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
using Console = Copybara.Util.Console.Console;

namespace Copybara.Monitor;

/// <summary>
/// An <see cref="IEventMonitor"/> that logs every event to the console in verbose mode, then
/// forwards it to a delegate monitor.
/// </summary>
public class ConsoleEventMonitor : IEventMonitor
{
    private readonly Console _console;
    private readonly IEventMonitor _delegate;

    public ConsoleEventMonitor(Console console, IEventMonitor @delegate)
    {
        _console = Preconditions.CheckNotNull(console);
        _delegate = Preconditions.CheckNotNull(@delegate);
    }

    public void OnMigrationStarted(IEventMonitor.MigrationStartedEvent @event)
    {
        _console.VerboseFmt("onMigrationStarted(): %s", @event);
        _delegate.OnMigrationStarted(@event);
    }

    public void OnChangeMigrationStarted(IEventMonitor.ChangeMigrationStartedEvent @event)
    {
        _console.VerboseFmt("onChangeMigrationStarted(): %s", @event);
        _delegate.OnChangeMigrationStarted(@event);
    }

    public void OnChangeMigrationFinished(IEventMonitor.ChangeMigrationFinishedEvent @event)
    {
        _console.VerboseFmt("onChangeMigrationFinished(): %s", @event);
        _delegate.OnChangeMigrationFinished(@event);
    }

    public void OnMigrationFinished(IEventMonitor.MigrationFinishedEvent @event)
    {
        _console.VerboseFmt("onMigrationFinished(): %s", @event);
        _delegate.OnMigrationFinished(@event);
    }

    public void OnInfoFinished(IEventMonitor.InfoFinishedEvent @event)
    {
        _console.VerboseFmt("onInfoFinished(): %s", @event);
        _delegate.OnInfoFinished(@event);
    }
}
