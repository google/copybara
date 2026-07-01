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
using MessageType = Copybara.Util.Console.Message.MessageType;

namespace Copybara.Util.Console;

/// <summary>
/// A <see cref="Console"/> that captures the error/warn/info messages preserving the order.
///
/// <para>All the methods delegate on another <see cref="Console"/>.</para>
///
/// <para>Uses a list behind a lock and it's unbounded.</para>
/// </summary>
public class CapturingConsole : DelegateConsole
{
    protected static readonly ImmutableHashSet<MessageType> AllTypes =
        Enum.GetValues<MessageType>().ToImmutableHashSet();

    private readonly List<Message> _messages = new();
    private readonly ISet<MessageType> _captureTypes;
    private readonly object _lock = new();

    /// <summary>Creates a new <see cref="CapturingConsole"/> that captures all <see cref="MessageType"/>s.</summary>
    public static CapturingConsole CaptureAllConsole(Console @delegate)
    {
        return new CapturingConsole(@delegate, AllTypes);
    }

    /// <summary>
    /// Creates a new <see cref="CapturingConsole"/> that captures only the specified
    /// <see cref="MessageType"/>s.
    /// </summary>
    public static CapturingConsole CaptureOnlyConsole(
        Console @delegate, MessageType first, params MessageType[] others)
    {
        var builder = ImmutableHashSet.CreateBuilder<MessageType>();
        builder.Add(first);
        foreach (var other in others)
        {
            builder.Add(other);
        }
        return new CapturingConsole(@delegate, builder.ToImmutable());
    }

    protected CapturingConsole(Console @delegate, ISet<MessageType> captureTypes)
        : base(@delegate)
    {
        _captureTypes = captureTypes;
    }

    public ImmutableArray<Message> GetMessages()
    {
        lock (_lock)
        {
            return _messages.ToImmutableArray();
        }
    }

    public void ClearMessages()
    {
        lock (_lock)
        {
            _messages.Clear();
        }
    }

    protected override void HandleMessage(MessageType type, string message)
    {
        lock (_lock)
        {
            if (_captureTypes.Contains(type))
            {
                _messages.Add(new Message(type, message));
            }
        }
    }
}
