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

using System.Collections.Immutable;
using Copybara.Common;

namespace Copybara.Profiler;

/// <summary>
/// Represents a task run by Copybara.
/// </summary>
public sealed class Task
{
    private const long NotFinished = -1;

    private readonly string _description;
    private readonly ImmutableDictionary<string, string> _fields;
    private readonly long _startNanos;
    private readonly long _finishNanos;

    internal Task(string description, long startNanos)
        : this(description, startNanos, NotFinished)
    {
    }

    internal Task(string description, ImmutableDictionary<string, string> fields, long startNanos)
        : this(description, fields, startNanos, NotFinished)
    {
    }

    internal Task(string description, long startNanos, long finishNanos)
        : this(description, ImmutableDictionary<string, string>.Empty, startNanos, finishNanos)
    {
    }

    internal Task(
        string description,
        ImmutableDictionary<string, string> fields,
        long startNanos,
        long finishNanos)
    {
        _description = Preconditions.CheckNotNull(description);
        _fields = Preconditions.CheckNotNull(fields);
        _startNanos = startNanos;
        _finishNanos = finishNanos;
    }

    internal Task Finish(long finishNanos)
    {
        Preconditions.CheckArgument(finishNanos != -1, "Already finished!");
        return new Task(_description, _fields, _startNanos, finishNanos);
    }

    /// <summary>
    /// Description of the task. Follows a pattern like:
    /// <code>//copybara/task/subtask/subsubtask</code>
    /// </summary>
    public string Description => _description;

    /// <summary>
    /// Context fields of the task.
    ///
    /// <para>They are not part of the profiler path, but they give more context information on this
    /// task and it's type. Might be used to implement more extensive monitoring.</para>
    /// </summary>
    public IReadOnlyDictionary<string, string> Fields => _fields;

    /// <summary>
    /// Time elapsed (in nanoseconds) running the task. Should only be called if
    /// <see cref="IsFinished"/> returns true.
    /// </summary>
    public long ElapsedNanos()
    {
        Preconditions.CheckState(_finishNanos != NotFinished, "Not finished!");
        return _finishNanos - _startNanos;
    }

    /// <summary>Returns true if the task is finished.</summary>
    public bool IsFinished => _finishNanos != NotFinished;

    public override bool Equals(object? o)
    {
        if (ReferenceEquals(this, o))
        {
            return true;
        }
        if (o is null || GetType() != o.GetType())
        {
            return false;
        }
        var task = (Task)o;
        return _startNanos == task._startNanos
            && _finishNanos == task._finishNanos
            && string.Equals(_description, task._description);
    }

    public override int GetHashCode() => HashCode.Combine(_description, _startNanos, _finishNanos);

    public override string ToString() =>
        $"Task{{description={_description}, startNanos={_startNanos}, finishNanos={_finishNanos}}}";
}
