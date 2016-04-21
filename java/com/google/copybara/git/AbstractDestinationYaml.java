// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.copybara.Destination;
import com.google.copybara.config.ConfigValidationException;

import java.util.regex.Pattern;

/**
 * Common super-class for Git destination YAML objects. This includes fields common to all Git
 * destinations.
 */
abstract class AbstractDestinationYaml implements Destination.Yaml {

  private static final Pattern AUTHOR_PATTERN = Pattern.compile(".+ <.+@.+>");

  protected String url;
  protected String pullFromRef;
  protected String author = "Copybara <noreply@google.com>";

  /**
   * Indicates the URL to push to as well as the URL from which to get the parent commit.
   */
  public void setUrl(String url) {
    this.url = url;
  }

  /**
   * Indicates the ref from which to get the parent commit.
   */
  public void setPullFromRef(String pullFromRef) {
    this.pullFromRef = pullFromRef;
  }

  /**
   * Sets the author line to use for the generated commit. Should be in the form
   * {@code Full Name <email@foo.com>}.
   */
  public void setAuthor(String author) {
    // The author line is validated by git commit, but it is nicer to validate early so the user
    // can see the source of the error a little more clearly and he doesn't have to wait until
    // after the transformations are finished.
    if (!AUTHOR_PATTERN.matcher(author).matches()) {
      throw new ConfigValidationException("author field must be in the form of 'Name <email@domain>'");
    }
    this.author = author;
  }
}
