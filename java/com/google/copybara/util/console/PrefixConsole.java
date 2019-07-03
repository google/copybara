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
import java.io.IOException;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * A console that delegates to another console but adds a prefix to all its messages
 */
public class PrefixConsole implements Console {

  private final String prefix;
  private final Console delegate;

  public PrefixConsole(String prefix, Console delegate) {
    this.prefix = Preconditions.checkNotNull(prefix);
    this.delegate = Preconditions.checkNotNull(delegate);
  }

  @Override
  public void startupMessage(String version) {
    delegate.startupMessage(version);
  }

  @Override
  public boolean isVerbose() {
    return delegate.isVerbose();
  }

  @Override
  public void error(String message) {
    delegate.error(prefix(message));
  }

  @Override
  public void warn(String message) {
    delegate.warn(prefix(message));
  }

  @Override
  public void info(String message) {
    delegate.info(prefix(message));
  }

  @Override
  public void progress(String progress) {
    delegate.progress(prefix(progress));
  }

  @Override
  public boolean promptConfirmation(String message) {
    return delegate.promptConfirmation(prefix(message));
  }

  @Override
  public String ask(String msg, @Nullable String defaultAnswer, Predicate<String> validator)
      throws IOException {
    return delegate.ask(msg, defaultAnswer, validator);
  }

  private String prefix(String progress) {
    return prefix + progress;
  }

  @Override
  public String colorize(AnsiColor ansiColor, String message) {
    return message;
  }
}
