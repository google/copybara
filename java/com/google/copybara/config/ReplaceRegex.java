// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

/**
 * A source code transformation which replaces a regular expression with some other string.
 */
public final class ReplaceRegex implements Transformation {
  private final String regex;
  private final String replacement;

  private ReplaceRegex(Builder builder) {
    // TODO(matvore): Make sure regex is a valid pattern.
    this.regex = builder.regex;
    this.replacement = builder.replacement;
  }

  @Override
  public String toString() {
    return String.format("ReplaceRegex{regex: %s, replacement: %s}", regex, replacement);
  }

  public final static class Builder implements Transformation.Builder {
    private String regex;
    private String replacement;

    public void setRegex(String regex) {
      this.regex = regex;
    }

    public void setReplacement(String replacement) {
      this.replacement = replacement;
    }

    @Override
    public Transformation build() {
      return new ReplaceRegex(this);
    }
  }
}
