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

package com.google.copybara.util.console;

import com.google.copybara.util.console.Message.MessageType;

/**
 * A console that skips y/n prompts
 */
public class NoPromptConsole extends DelegateConsole {

  private final boolean defaultAnswer;
  public NoPromptConsole(Console delegate, boolean defaultAnswer) {
    super(delegate);
    this.defaultAnswer = defaultAnswer;
  }

  @Override
  public boolean promptConfirmation(String msg) {
    info("Prompt: " + msg);
    info("Answering: " + (defaultAnswer ? "yes" : "no"));
    return defaultAnswer;
  }

  @Override
  protected void handleMessage(MessageType info, String message) {
  }
}
