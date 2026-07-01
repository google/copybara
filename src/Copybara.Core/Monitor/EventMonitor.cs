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
using Copybara.Effect;
using Copybara.Util;
using Task = Copybara.Profiler.Task;

namespace Copybara.Monitor;

/// <summary>
/// A monitor that allows triggering actions when high-level actions take place during the execution.
///
/// <para>Default implementation logs in the console the events in verbose mode only.</para>
/// </summary>
public interface IEventMonitor
{
    /// <summary>An empty monitor that does nothing on any event.</summary>
    static readonly IEventMonitor EmptyMonitor = new EmptyEventMonitor();

    /// <summary>Invoked when the migration starts, only once at the beginning of the execution.</summary>
    void OnMigrationStarted(MigrationStartedEvent @event) { }

    /// <summary>Invoked when each change migration starts.</summary>
    void OnChangeMigrationStarted(ChangeMigrationStartedEvent @event) { }

    /// <summary>Invoked when each change migration finishes.</summary>
    void OnChangeMigrationFinished(ChangeMigrationFinishedEvent @event) { }

    /// <summary>Invoked when the migration finishes, only once at the end of the execution.</summary>
    void OnMigrationFinished(MigrationFinishedEvent @event) { }

    /// <summary>Invoked when an info subcommand finishes, only once at the end of the execution.</summary>
    void OnInfoFinished(InfoFinishedEvent @event) { }

    /// <summary>Invoked when an info subcommand fails, only once at the end of the execution.</summary>
    void OnInfoFailed(InfoFailedEvent @event) { }

    /// <summary>Concrete no-op monitor backing <see cref="EmptyMonitor"/>.</summary>
    private sealed class EmptyEventMonitor : IEventMonitor
    {
    }

    /// <summary>Event that happens for every migration that is started.</summary>
    class MigrationStartedEvent
    {
        public override string ToString() => "MigrationStartedEvent";
    }

    /// <summary>Event that happens for every change migration that is started.</summary>
    class ChangeMigrationStartedEvent
    {
        public override string ToString() => "ChangeMigrationStartedEvent{}";
    }

    /// <summary>Event that happens for every change migration that is finished.</summary>
    class ChangeMigrationFinishedEvent
    {
        private readonly ImmutableArray<DestinationEffect> _destinationEffects;
        private readonly ImmutableListMultimap<string, string> _originDescription;
        private readonly ImmutableListMultimap<string, string> _destinationDescription;

        public ChangeMigrationFinishedEvent(
            ImmutableArray<DestinationEffect> destinationEffects,
            ImmutableListMultimap<string, string> originDescription,
            ImmutableListMultimap<string, string> destinationDescription)
        {
            _destinationEffects = destinationEffects;
            _originDescription = originDescription;
            _destinationDescription = destinationDescription;
        }

        public IReadOnlyList<DestinationEffect> DestinationEffects => _destinationEffects;

        public ImmutableListMultimap<string, string> DestinationDescription => _destinationDescription;

        public ImmutableListMultimap<string, string> OriginDescription => _originDescription;

        public override string ToString() =>
            $"ChangeMigrationFinishedEvent{{destinationEffects=[{string.Join(", ", _destinationEffects)}]}}";
    }

    /// <summary>Event that happens for every migration that is finished.</summary>
    class MigrationFinishedEvent
    {
        private readonly ExitCode _exitCode;
        private readonly IReadOnlyList<Task>? _profileData;

        public MigrationFinishedEvent(ExitCode exitCode)
            : this(exitCode, null)
        {
        }

        public MigrationFinishedEvent(ExitCode exitCode, IReadOnlyList<Task>? profileData)
        {
            _exitCode = exitCode;
            _profileData = profileData?.ToImmutableArray();
        }

        public ExitCode ExitCode => _exitCode;

        /// <summary>Profiler task data, if any (Java's <c>Optional&lt;List&lt;Task&gt;&gt;</c>).</summary>
        public IReadOnlyList<Task>? ProfileData => _profileData;

        public override string ToString() =>
            $"MigrationFinishedEvent{{exitCode={_exitCode}, profiler="
                + (_profileData == null ? "null" : $"[{string.Join(", ", _profileData)}]") + "}";
    }

    /// <summary>Event that happens for every info subcommand that is finished.</summary>
    class InfoFinishedEvent
    {
        // Java holds an Info<? extends Revision>. The com.google.copybara.Info type has not been
        // ported yet, so the payload is carried as object; consumers can cast once Info lands.
        private readonly object _info;
        private readonly ImmutableDictionary<string, string> _context;

        public InfoFinishedEvent(object info, ImmutableDictionary<string, string> context)
        {
            _info = Preconditions.CheckNotNull(info);
            _context = Preconditions.CheckNotNull(context);
        }

        public InfoFinishedEvent(object info)
            : this(info, ImmutableDictionary<string, string>.Empty)
        {
        }

        public object Info => _info;

        public IReadOnlyDictionary<string, string> Context => _context;

        public override string ToString() =>
            $"InfoFinishedEvent{{info={_info}, context={{{string.Join(", ", _context.Select(kv => kv.Key + "=" + kv.Value))}}}}}";
    }

    /// <summary>Event that happens for every info subcommand that failed.</summary>
    class InfoFailedEvent
    {
        private readonly string _error;
        private readonly ImmutableDictionary<string, string> _context;

        public InfoFailedEvent(string error, ImmutableDictionary<string, string> context)
        {
            _error = Preconditions.CheckNotNull(error);
            _context = Preconditions.CheckNotNull(context);
        }

        public string Error => _error;

        public IReadOnlyDictionary<string, string> Context => _context;

        public override string ToString() =>
            $"InfoFailedEvent{{error={_error}, context={{{string.Join(", ", _context.Select(kv => kv.Key + "=" + kv.Value))}}}}}";
    }

    /// <summary>Holder for all active event monitors.</summary>
    class EventMonitors
    {
        private readonly IReadOnlyList<IEventMonitor> _monitors;

        public EventMonitors(IReadOnlyList<IEventMonitor> monitors)
        {
            _monitors = monitors;
        }

        /// <summary>Accepts a functional to apply to all active monitors.</summary>
        public void DispatchEvent(Action<IEventMonitor> @event)
        {
            foreach (var monitor in _monitors)
            {
                @event(monitor);
            }
        }
    }
}
