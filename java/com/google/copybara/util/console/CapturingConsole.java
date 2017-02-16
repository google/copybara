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

package com.google.copybara.util.console;

import static com.google.copybara.util.console.Message.MessageType.ERROR;
import static com.google.copybara.util.console.Message.MessageType.INFO;
import static com.google.copybara.util.console.Message.MessageType.PROGRESS;
import static com.google.copybara.util.console.Message.MessageType.PROMPT;
import static com.google.copybara.util.console.Message.MessageType.WARNING;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.util.console.Message.MessageType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

/**
 * A {@link Console} that captures the error/warn/info messages preserving the order.
 *
 * <p>All the methods delegate on another {@link Console}.
 *
 * <p>Uses an {@link ArrayList} behind a lock and it's unbounded.
 */
public class CapturingConsole implements Console {

  protected static final Set<MessageType> ALL_TYPES =
      ImmutableSet.of(ERROR, WARNING, INFO, PROGRESS, PROMPT);

  private final Console delegate;
  private final ArrayList<Message> messages = new ArrayList<>();
  private final Set<MessageType> captureTypes;

  /**
   * Creates a new {@link CapturingConsole} that captures all {@link MessageType}s.
   */
  public static CapturingConsole captureAllConsole(Console delegate) {
    return new CapturingConsole(delegate, ALL_TYPES);
  }

  /**
   * Creates a new {@link CapturingConsole} that captures only the specified {@link MessageType}s.
   */
  public static CapturingConsole captureOnlyConsole(
      Console delegate, MessageType first, MessageType... others) {
    ImmutableSet<MessageType> messageTypes = ImmutableSet.<MessageType>builder()
        .add(first)
        .addAll(Arrays.asList(others))
        .build();
    return new CapturingConsole(delegate, messageTypes);
  }

  protected CapturingConsole(Console delegate, Set<MessageType> captureTypes) {
    this.delegate = Preconditions.checkNotNull(delegate);
    this.captureTypes = captureTypes;
  }

  @Override
  public void startupMessage(String version) {
    delegate.startupMessage(version);
  }

  @Override
  public void error(String message) {
    addMessage(MessageType.ERROR, message);
    delegate.error(message);
  }

  @Override
  public void warn(String message) {
    addMessage(MessageType.WARNING, message);
    delegate.warn(message);
  }

  @Override
  public void info(String message) {
    addMessage(MessageType.INFO, message);
    delegate.info(message);
  }

  @Override
  public boolean isVerbose() {
    return delegate.isVerbose();
  }

  @Override
  public void progress(String message) {
    addMessage(MessageType.PROGRESS, message);
    delegate.progress(message);
  }

  @Override
  public boolean promptConfirmation(String message) {
    addMessage(MessageType.PROMPT, message);
    return delegate.promptConfirmation(message);
  }

  @Override
  public String colorize(AnsiColor ansiColor, String message) {
    return delegate.colorize(ansiColor, message);
  }

  public ImmutableList<Message> getMessages() {
    return ImmutableList.copyOf(messages);
  }

  private synchronized void addMessage(MessageType type, String message) {
    if (captureTypes.contains(type)) {
      messages.add(new Message(type, message));
    }
  }
}
