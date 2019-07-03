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

package com.google.copybara.util.console;

import com.google.common.base.Preconditions;
import com.google.copybara.util.console.Message.MessageType;
import java.io.IOException;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * A simple console that can be extended to delegate automatically to another console.
 *
 * <p>This console delegates all the methods on the delegate, while implementors can handle the
 * messages written to the console, while not having to deal with the other methods. The reason is
 * to have implementors that can output the console contents to files or other formats while not
 * having to implement the delegate pattern over and over again.
 */
public abstract class DelegateConsole implements Console {

  private final Console delegate;

  protected DelegateConsole(Console delegate) {
    this.delegate = Preconditions.checkNotNull(delegate);
  }

  @Override
  public void startupMessage(String version) {
    handleMessage(MessageType.INFO, "Copybara source mover (Version: " + version + ")");
    delegate.startupMessage(version);
  }

  @Override
  public void error(String message) {
    handleMessage(MessageType.ERROR, message);
    delegate.error(message);
  }

  @Override
  public void warn(String message) {
    handleMessage(MessageType.WARNING, message);
    delegate.warn(message);
  }

  @Override
  public boolean isVerbose() {
    return delegate.isVerbose();
  }

  @Override
  public void info(String message) {
    handleMessage(MessageType.INFO, message);
    delegate.info(message);
  }

  @Override
  public void progress(String message) {
    handleMessage(MessageType.PROGRESS, message);
    delegate.progress(message);
  }

  @Override
  public void verbose(String message) {
    handleMessage(MessageType.VERBOSE, message);
    delegate.verbose(message);
  }

  @Override
  public String ask(String msg, @Nullable String defaultAnswer, Predicate<String> validator)
      throws IOException {
    return delegate.ask(msg, defaultAnswer, validator);
  }

  @Override
  public boolean promptConfirmation(String message) {
    return delegate.promptConfirmation(message);
  }

  @Override
  public String colorize(AnsiColor ansiColor, String message) {
    return delegate.colorize(ansiColor, message);
  }

  @Override
  public void close() {
    delegate.close();
  }

  /** Handle the message type and contents. */
  abstract protected void handleMessage(MessageType info, String message);
}
