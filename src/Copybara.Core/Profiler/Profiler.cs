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

using System.Collections.Generic;
using System.Collections.Immutable;
using Copybara.Common;

namespace Copybara.Profiler;

/// <summary>
/// A profiler that allows to record hierarchical time statistics of the different Copybara
/// components.
/// </summary>
///
/// <remarks>
/// <para>The Java original stores its per-thread task stack in an <c>InheritableThreadLocal</c>
/// whose <c>childValue</c> hook copies only the top element into a spawned thread so a child can
/// build (but not finish) descendants of its parent's current task. .NET has no
/// <c>InheritableThreadLocal</c> with a <c>childValue</c> hook; this port uses a
/// <see cref="ThreadLocal{T}"/> whose factory seeds a fresh stack with a "//detached_thread" root,
/// mirroring the Java <c>initialValue</c> path. The common single-threaded start/stop/scope
/// behaviour is preserved exactly.</para>
/// </remarks>
public sealed class Profiler
{
    public const string RootName = "//copybara";
    public const string TypeKey = "type";

    private readonly Ticker _ticker;
    private readonly ProfilerTask _nullProfilerTask;

    /// <summary>
    /// A stack of tasks to be finished. Each thread keeps its own stack; a freshly seen thread gets
    /// a "//detached_thread" root, matching Java's <c>initialValue</c>.
    /// </summary>
    private readonly ThreadLocal<Stack<Task>?> _taskQueue;

    private volatile bool _stopped;
    private IReadOnlyList<IListener> _listeners = ImmutableArray<IListener>.Empty;
    private ProfilerTask? _rootProfilerTask;

    public Profiler(Ticker ticker)
    {
        _ticker = ticker;
        _nullProfilerTask = new ProfilerTask(this, expectedTask: null);
        _taskQueue = new ThreadLocal<Stack<Task>?>(() =>
        {
            if (_stopped)
            {
                return null;
            }
            return CreateQueue(new Task("//detached_thread", _ticker.Read()));
        });
    }

    private static Stack<Task> CreateQueue(Task element)
    {
        var tasks = new Stack<Task>(2);
        tasks.Push(element);
        return tasks;
    }

    /// <summary>
    /// Call this method once at the beginning of the Copybara binary run.
    /// </summary>
    /// <param name="listeners">the listeners to be notified of the task events</param>
    public void Init(IReadOnlyList<IListener> listeners)
    {
        _listeners = listeners;
        if (listeners.Count == 0)
        {
            return;
        }
        var task = new Task(RootName, _ticker.Read());
        _taskQueue.Value = CreateQueue(task);
        foreach (var listener in listeners)
        {
            listener.TaskStarted(task);
        }
        _rootProfilerTask = new ProfilerTask(this, task);
    }

    /// <summary>
    /// Call this method once at the end of the Copybara binary run.
    /// </summary>
    public void Stop()
    {
        if (_listeners.Count == 0)
        {
            return;
        }
        var queue = _taskQueue.Value!;
        Preconditions.CheckState(queue.Peek().Description.Equals(RootName));
        _rootProfilerTask!.Close();
        Preconditions.CheckState(queue.Count == 0);
        _stopped = true;
    }

    public ImmutableDictionary<string, string> TaskType(string type) =>
        ImmutableDictionary<string, string>.Empty.Add(TypeKey, type);

    /// <summary>
    /// Create a new profiler task that can be closed using a <c>using</c> statement.
    ///
    /// <para>The profiler tasks are reentrant. So you can stack them in multiple nested
    /// <c>using</c> blocks.</para>
    ///
    /// <para>Example usage:</para>
    /// <code>
    ///     using (var p = profiler.Start("migration"))
    ///     {
    ///         using (var p2 = profiler.Start("subtask"))
    ///         {
    ///             // Do job
    ///         }
    ///     }
    /// </code>
    /// </summary>
    /// <param name="description">description of the task</param>
    /// <returns>an <see cref="IDisposable"/> task that can be closed manually or with a
    /// <c>using</c> statement</returns>
    public ProfilerTask Start(string description) =>
        Start(description, ImmutableDictionary<string, string>.Empty);

    /// <summary>
    /// Overloaded method for <see cref="Start(string)"/>, that allows adding <paramref name="fields"/>
    /// to the context of this task.
    /// </summary>
    public ProfilerTask Start(string description, ImmutableDictionary<string, string> fields)
    {
        if (_stopped || _listeners.Count == 0)
        {
            return _nullProfilerTask;
        }
        var tasks = _taskQueue.Value!;
        Preconditions.CheckState(tasks.Count != 0);
        var parent = tasks.Peek();
        var child = new Task(parent.Description + "/" + description, fields, _ticker.Read());
        tasks.Push(child);
        foreach (var listener in _listeners)
        {
            listener.TaskStarted(child);
        }
        return new ProfilerTask(this, child);
    }

    /// <summary>
    /// Record a simple task metric. The user is in charge of providing its own time.
    /// </summary>
    public void SimpleTask(string description, long startNanos, long endNanos)
    {
        if (_stopped || _listeners.Count == 0)
        {
            return;
        }
        var tasks = _taskQueue.Value!;
        Preconditions.CheckState(tasks.Count != 0);
        var parent = tasks.Peek();
        var child = new Task(parent.Description + "/" + description, startNanos);
        var finishedChild = child.Finish(endNanos);
        foreach (var listener in _listeners)
        {
            listener.TaskStarted(child);
            listener.TaskFinished(finishedChild);
        }
    }

    /// <summary>
    /// A profiler task that can be closed to send the finish metric.
    /// </summary>
    public sealed class ProfilerTask : IDisposable
    {
        private readonly Profiler _profiler;
        private readonly Task? _expectedTask;

        internal ProfilerTask(Profiler profiler, Task? expectedTask)
        {
            _profiler = profiler;
            _expectedTask = expectedTask;
        }

        /// <summary>Close the task if it's not null.</summary>
        public void Close()
        {
            if (_expectedTask != null && !_profiler._stopped)
            {
                var task = _profiler._taskQueue.Value!.Pop();
                if (task != _expectedTask)
                {
                    throw new InvalidOperationException(
                        "Trying to finish a task that is different from the registered one: "
                            + task.Description + ". Expecting: " + _expectedTask.Description);
                }
                task = task.Finish(_profiler._ticker.Read());
                foreach (var listener in _profiler._listeners)
                {
                    listener.TaskFinished(task);
                }
            }
        }

        public void Dispose() => Close();
    }
}
