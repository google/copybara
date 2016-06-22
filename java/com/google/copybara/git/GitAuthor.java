// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.copybara.Author;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An author that contributes to Git repositories.
 */
class GitAuthor implements Author {

  private static final Pattern AUTHOR_PATTERN = Pattern.compile("(.+ )<(.+@.+)>");

  private final String name;
  private final String email;

  GitAuthor(String author) {
    Preconditions.checkNotNull(author);
    Matcher matcher = AUTHOR_PATTERN.matcher(author);
    Preconditions.checkArgument(matcher.matches(),
        "Invalid author '%s'. Must be in the form of 'Name <email@domain>'", author);
    this.name = matcher.group(1).trim();
    this.email = matcher.group(2).trim();
  }

  @Override
  public String getId() {
    return String.format("%s <%s>", name, email);
  }

  /**
   * Returns the name of the author.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the email address of the author.
   */
  public String getEmail() {
    return email;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("email", email)
        .toString();
  }
}
