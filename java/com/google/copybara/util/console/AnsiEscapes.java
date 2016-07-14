// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util.console;

final class AnsiEscapes {

  private AnsiEscapes() {
  }

  static String oneLineUp() {
    return "\u001B[1A";
  }

  static String deleteLine() {
    return "\u001B[2K";
  }
}
