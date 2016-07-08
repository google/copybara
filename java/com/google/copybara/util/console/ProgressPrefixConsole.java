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
}
