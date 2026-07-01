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

using Copybara.Common;

namespace Copybara.Util.Console;

/// <summary>
/// Represents a message registered in a console.
/// </summary>
public sealed class Message
{
    /// <summary>
    /// The type of messages registered in a console.
    /// </summary>
    public enum MessageType
    {
        Error,
        Warning,
        Info,
        Verbose,
        Progress,
        Prompt,
    }

    private readonly MessageType _type;
    private readonly string _text;

    public static Message Error(string text) => new(MessageType.Error, text);

    public static Message Warning(string text) => new(MessageType.Warning, text);

    public static Message Info(string text) => new(MessageType.Info, text);

    public Message(MessageType type, string text)
    {
        _type = type;
        _text = Preconditions.CheckNotNull(text);
    }

    public MessageType Type => _type;

    public string Text => _text;

    public override string ToString() => _type + ": " + _text;

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
        var message = (Message)o;
        return _type == message._type && string.Equals(_text, message._text);
    }

    public override int GetHashCode() => HashCode.Combine(_type, _text);
}
