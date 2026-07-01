/*
 * Copyright (C) 2023 Google Inc.
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

using MessageType = Copybara.Util.Console.Message.MessageType;

namespace Copybara.Util.Console;

/// <summary>
/// A console that skips y/n prompts.
/// </summary>
public class NoPromptConsole : DelegateConsole
{
    private readonly bool _defaultAnswer;

    public NoPromptConsole(Console @delegate, bool defaultAnswer)
        : base(@delegate)
    {
        _defaultAnswer = defaultAnswer;
    }

    public override bool PromptConfirmation(string msg)
    {
        Info("Prompt: " + msg);
        Info("Answering: " + (_defaultAnswer ? "yes" : "no"));
        return _defaultAnswer;
    }

    protected override void HandleMessage(MessageType info, string message)
    {
    }
}
