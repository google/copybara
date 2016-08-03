// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.copybara.Destination;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocField;

/**
 * Common super-class for Git destination YAML objects. This includes fields common to all Git
 * destinations.
 */
abstract class AbstractDestinationYaml implements Destination.Yaml {

  protected String url;
  protected String fetch;

  /**
   * Indicates the URL to push to as well as the URL from which to get the parent commit.
   */
  @DocField(description = "Indicates the URL to push to as well as the URL from which to get the "
      + "parent commit")
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
}
