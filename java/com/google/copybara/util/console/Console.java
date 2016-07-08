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
   * Print an informational message in the console
   *
   * <p> Warning: Do not abuse the usage of this method. We don't
   * want to spam our users.
   */
  void info(String message);

  /**
   * Print a progress message in the console
   */
  void progress(String progress);

  /**
   * Returns true if this Console's input registers Y/y after showing the prompt message.
   */
  boolean promptConfirmation(String message);

  interface PromptPrinter {
    /**
     * Prints a prompt message.
     */
    void print(String message);
  }
}
