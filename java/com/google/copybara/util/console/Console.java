package com.google.copybara.util.console;

/**
 * Write user messages to the console
 */
public interface Console {

  /**
   * Print an error in the console
   */
  void error(String message);

  /**
   * Print a warning in the console
   */
  void warn(String message);

  /**
   * Print a progress message in the console
   */
  void progress(String progress);
}
