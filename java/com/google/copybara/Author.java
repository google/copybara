// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;

import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Represents a contributor in an origin or destination repository. A contributor can be either an
 * individual or a team.
 *
 * <p>Author is lenient in name or email validation. Validation happens in the Yaml class.
 */
public final class Author {

  private final String name;
  private final String email;

  public Author(String name, String email) {
    this.name = Preconditions.checkNotNull(name);
    this.email = Preconditions.checkNotNull(email);
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

  /**
   * Returns the string representation of an author, which is the standard format
   * {@code Name <email>} used by most version control systems.
   */
  @Override
  public String toString() {
    return String.format("%s <%s>", name, email);
  }

  @Override
  public boolean equals(Object o) {
    // TODO(danielromero): We should revisit this. Email should be the identifier to be able to
    // match two users with slightly different names "Foo B <foo@bar.com>" and
    // "Foo Bar <foo@bar.com>", but email can be empty in Git standard author format.
    if (o instanceof Author) {
      Author that = (Author) o;
      return Objects.equal(this.name, that.name)
          && Objects.equal(this.email, that.email);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.name, this.email);
  }

  /**
   * Config builder used by YAML.
   */
  @DocElement(yamlName = "!Author",
      description = "An individual or team that contributes code.",
      elementKind = Author.class)
  public static final class Yaml {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(".+@.+");

    private String name;
    private String email;

    /**
     * Sets author name.
     */
    @DocField(description = "Sets the name of the author.", required = true)
    public void setName(String name) throws ConfigValidationException {
      this.name = name;
    }

    /**
     * Sets author email address.
     *
     */
    @DocField(description = "Sets the email address of the author.", required = true)
    public void setEmail(String email) throws ConfigValidationException {
      if (!EMAIL_PATTERN.matcher(email).matches()) {
        throw new ConfigValidationException(String.format("Invalid email format: %s", email));
      }
      this.email = email;
    }

    public Author create() throws ConfigValidationException {
      if (name == null) {
        throw new ConfigValidationException("Field 'name' cannot be empty.");
      }
      if (email == null) {
        throw new ConfigValidationException("Field 'email' cannot be empty.");
      }
      return new Author(name, email);
    }
  }
}
