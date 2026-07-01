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

namespace Copybara.Config;

/// <summary>The immutable result of a validation performed by <see cref="ConfigValidator"/>.</summary>
public sealed class ValidationResult
{
    public static readonly ValidationResult EMPTY =
        new(ImmutableArray<ValidationMessage>.Empty);

    private readonly ImmutableArray<ValidationMessage> _messages;

    private ValidationResult(ImmutableArray<ValidationMessage> messages) => _messages = messages;

    /// <summary>Returns all the <see cref="ValidationMessage"/>s in the order they were registered.</summary>
    public IReadOnlyList<ValidationMessage> GetAllMessages() => _messages;

    /// <summary>Returns true iff there was at least one warning message.</summary>
    public bool HasWarnings() => GetWarnings().Count > 0;

    /// <summary>Returns true iff there was at least one error message.</summary>
    public bool HasErrors() => GetErrors().Count > 0;

    /// <summary>Returns the text of the warning messages, in the order that were registered.</summary>
    public IReadOnlyList<string> GetWarnings() =>
        _messages.Where(v => v.GetLevel() == Level.WARNING).Select(v => v.GetMessage())
            .ToImmutableArray();

    /// <summary>Returns the text of the error messages, in the order that were registered.</summary>
    public IReadOnlyList<string> GetErrors() =>
        _messages.Where(v => v.GetLevel() == Level.ERROR).Select(v => v.GetMessage())
            .ToImmutableArray();

    public override string ToString() =>
        $"ValidationResult{{messages=[{string.Join(", ", _messages)}]}}";

    /// <summary>
    /// Levels of validation messages. Can only be warning or error, because it doesn't make sense
    /// to have info here.
    /// </summary>
    public enum Level
    {
        WARNING,
        ERROR,
    }

    /// <summary>Encapsulates a validation message and a <see cref="Level"/>.</summary>
    public sealed class ValidationMessage
    {
        private readonly Level _level;
        private readonly string _message;

        internal ValidationMessage(Level level, string message)
        {
            _level = level;
            _message = Preconditions.CheckNotNull(message);
        }

        public Level GetLevel() => _level;

        public string GetMessage() => _message;

        /// <summary>
        /// Generates a string from this validation message with padded level and message text.
        /// </summary>
        public override string ToString() => string.Format("{0,-8} {1}", _level, _message);
    }

    /// <summary>A builder of <see cref="ValidationResult"/>.</summary>
    public sealed class Builder
    {
        private readonly List<ValidationMessage> _messages = new();

        public Builder Warning(string message)
        {
            _messages.Add(new ValidationMessage(Level.WARNING, message));
            return this;
        }

        public Builder WarningFmt(string message, params object?[] args)
        {
            _messages.Add(new ValidationMessage(Level.WARNING, string.Format(message, args)));
            return this;
        }

        public Builder Error(string message)
        {
            _messages.Add(new ValidationMessage(Level.ERROR, message));
            return this;
        }

        public Builder ErrorFmt(string message, params object?[] args)
        {
            _messages.Add(new ValidationMessage(Level.ERROR, string.Format(message, args)));
            return this;
        }

        public Builder Append(ValidationResult result)
        {
            _messages.AddRange(result._messages);
            return this;
        }

        public ValidationResult Build() => new(_messages.ToImmutableArray());
    }
}
