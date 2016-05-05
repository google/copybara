package com.google.copybara.util.console;

/**
 * Created by malcon on 5/5/16.
 */
final class AnsiEscapes {

  private AnsiEscapes() {
  }

  enum Color {
    RESET("\u001B[0m"),
    BLACK("\u001B[30m"),
    RED("\u001B[31m"),
    GREEN("\u001B[32m"),
    YELLOW("\u001B[33m"),
    BLUE("\u001B[34m"),
    PURPLE("\u001B[35m"),
    CYAN("\u001B[36m"),
    WHITE("\u001B[37m");

    private final String color;

    Color(String color) {
      this.color = color;
    }

    String write(String text) {
      return color + text + RESET.color;
    }
  }

  static String oneLineUp() {
    return "\u001B[1A";
  }

  static String deleteLine() {
    return "\u001B[2K";
  }
}
