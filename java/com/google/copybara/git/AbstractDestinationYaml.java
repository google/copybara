// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.copybara.Destination;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocField;

import java.util.regex.Pattern;

/**
 * Common super-class for Git destination YAML objects. This includes fields common to all Git
 * destinations.
 */
abstract class AbstractDestinationYaml implements Destination.Yaml {

  private static final Pattern AUTHOR_PATTERN = Pattern.compile(".+ <.+@.+>");
  private static final String DEFAULT_AUTHOR = "Copybara <noreply@google.com>";

  protected String url;
  protected String fetch;
  protected String author = DEFAULT_AUTHOR;

  /**
   * Indicates the URL to push to as well as the URL from which to get the parent commit.
   */
  @DocField(description = "Indicates the URL to push to as well as the URL from which to get the parent commit")
  public void setUrl(String url) {
    this.url = url;
  }

  /**
   * Indicates the ref from which to get the parent commit.
   */
  @DocField(description = "Indicates the ref from which to get the parent commit")
  public void setFetch(String fetch) {
    this.fetch = fetch;
  }

  protected void checkRequiredFields() throws ConfigValidationException {
    ConfigValidationException.checkNotMissing(url, "url");
    ConfigValidationException.checkNotMissing(fetch, "fetch");
  }

  /**
   * Sets the author line to use for the generated commit. Should be in the form
   * {@code Full Name <email@foo.com>}.
   */
  @DocField(description = "Sets the author line to use for the generated commit. Should be in the form: Full Name <email@foo.com>",
      required = false, defaultValue = DEFAULT_AUTHOR)
  public void setAuthor(String author) throws ConfigValidationException {
    // The author line is validated by git commit, but it is nicer to validate early so the user
    // can see the source of the error a little more clearly and he doesn't have to wait until
    // after the transformations are finished.
    if (!AUTHOR_PATTERN.matcher(author).matches()) {
      throw new ConfigValidationException("author field must be in the form of 'Name <email@domain>'");
    }
    this.author = author;
  }
}
