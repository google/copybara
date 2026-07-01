/*
 * Copyright (C) 2021 Google Inc.
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
/// A console logging to two delegates. Prompt is not supported for delegate2.
/// </summary>
public class MultiplexingConsole : DelegateConsole
{
    private readonly Console _delegate2;

    public MultiplexingConsole(Console delegate1, Console delegate2)
        : base(delegate1)
    {
        _delegate2 = Preconditions.CheckNotNull(delegate2);
    }

    protected override void HandleMessage(MessageType type, string message)
    {
        switch (type)
        {
            case MessageType.Error:
                _delegate2.Error(message);
                break;
            case MessageType.Warning:
                _delegate2.Warn(message);
                break;
            case MessageType.Verbose:
                _delegate2.Verbose(message);
                break;
            case MessageType.Progress:
                _delegate2.Progress(message);
                break;
            case MessageType.Info:
            case MessageType.Prompt:
                _delegate2.Info(message);
                break;
        }
    }
}
