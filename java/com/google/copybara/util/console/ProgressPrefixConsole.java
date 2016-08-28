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

import com.google.common.base.Preconditions;

/**
 * A console that delegates to another console but adds a prefix to the progress messages
 */
public class ProgressPrefixConsole implements Console {

  private final String prefix;
  private final Console delegate;

  public ProgressPrefixConsole(String prefix, Console delegate) {
    this.prefix = Preconditions.checkNotNull(prefix);
    this.delegate = Preconditions.checkNotNull(delegate);
  }

  @Override
  public void startupMessage() {
    delegate.startupMessage();
  }

  @Override
  public void error(String message) {
    delegate.error(message);
  }

  @Override
  public void warn(String message) {
    delegate.warn(message);
  }

  @Override
  public void info(String message) {
    delegate.info(message);
  }

  @Override
  public void progress(String progress) {
    delegate.progress(prefix + progress);
  }

  @Override
  public boolean promptConfirmation(String message) {
    return delegate.promptConfirmation(message);
  }

  @Override
  public String colorize(AnsiColor ansiColor, String message) {
    return message;
  }
}
