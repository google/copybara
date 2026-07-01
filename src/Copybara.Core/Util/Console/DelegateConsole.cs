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
using MessageType = Copybara.Util.Console.Message.MessageType;

namespace Copybara.Util.Console;

/// <summary>
/// A simple console that can be extended to delegate automatically to another console.
///
/// <para>This console delegates all the methods on the delegate, while implementors can handle the
/// messages written to the console, while not having to deal with the other methods. The reason is
/// to have implementors that can output the console contents to files or other formats while not
/// having to implement the delegate pattern over and over again.</para>
/// </summary>
public abstract class DelegateConsole : Console
{
    private readonly Console _delegate;

    protected DelegateConsole(Console @delegate)
    {
        _delegate = Preconditions.CheckNotNull(@delegate);
    }

    public virtual void StartupMessage(string version)
    {
        HandleMessage(MessageType.Info, "Copybara (Version: " + version + ")");
        _delegate.StartupMessage(version);
    }

    public virtual void Error(string message)
    {
        HandleMessage(MessageType.Error, message);
        _delegate.Error(message);
    }

    public virtual void Warn(string message)
    {
        HandleMessage(MessageType.Warning, message);
        _delegate.Warn(message);
    }

    public virtual bool IsVerbose => _delegate.IsVerbose;

    public virtual void Info(string message)
    {
        HandleMessage(MessageType.Info, message);
        _delegate.Info(message);
    }

    public virtual void Progress(string message)
    {
        HandleMessage(MessageType.Progress, message);
        _delegate.Progress(message);
    }

    public virtual void Verbose(string message)
    {
        HandleMessage(MessageType.Verbose, message);
        _delegate.Verbose(message);
    }

    public virtual string Ask(string msg, string? defaultAnswer, Func<string, bool> validator)
    {
        return _delegate.Ask(msg, defaultAnswer, validator);
    }

    public virtual bool PromptConfirmation(string message)
    {
        return _delegate.PromptConfirmation(message);
    }

    public virtual string Colorize(AnsiColor ansiColor, string message)
    {
        return _delegate.Colorize(ansiColor, message);
    }

    public virtual void Dispose()
    {
        _delegate.Dispose();
    }

    /// <summary>Handle the message type and contents.</summary>
    protected abstract void HandleMessage(MessageType info, string message);
}
