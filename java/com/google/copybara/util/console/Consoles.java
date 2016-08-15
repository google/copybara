// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util.console;

import com.google.common.base.Splitter;
import java.util.Iterator;

/**
 * Utility methods for working with {@link Console}s.
 */
public class Consoles {

  private Consoles() {}

  /**
   * Logs text as separate lines using {@link Console#info(String)}. If {@code text} is an empty
   * string, does nothing.
   */
  public static void logLines(Console console, String prefix, String text) {
    Iterator<String> lines = Splitter.on('\n').split(text).iterator();
    while (lines.hasNext()) {
      String line = lines.next();
      if (line.isEmpty() && !lines.hasNext()) {
        break;
      }
      console.info(prefix + line);
    }
  }
}
