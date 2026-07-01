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
/// A console that delegates to another console but adds a prefix to all its messages.
/// </summary>
public class PrefixConsole : Console
{
    private readonly string _prefix;
    private readonly Console _delegate;

    public PrefixConsole(string prefix, Console @delegate)
    {
        _prefix = Preconditions.CheckNotNull(prefix);
        _delegate = Preconditions.CheckNotNull(@delegate);
    }

    public void StartupMessage(string version) => _delegate.StartupMessage(version);

    public bool IsVerbose => _delegate.IsVerbose;

    public void Error(string message) => _delegate.Error(Prefix(message));

    public void Warn(string message) => _delegate.Warn(Prefix(message));

    public void Info(string message) => _delegate.Info(Prefix(message));

    public void Progress(string progress) => _delegate.Progress(Prefix(progress));

    public bool PromptConfirmation(string message) => _delegate.PromptConfirmation(Prefix(message));

    public string Ask(string msg, string? defaultAnswer, Func<string, bool> validator)
    {
        return _delegate.Ask(msg, defaultAnswer, validator);
    }

    private string Prefix(string progress) => _prefix + progress;

    public string Colorize(AnsiColor ansiColor, string message) => message;
}
