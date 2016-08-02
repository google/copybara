package com.google.copybara.testing;

import com.google.common.base.Objects;
import com.google.copybara.Author;
import com.google.copybara.Origin.OriginalAuthor;

/**
 * An {@link OriginalAuthor} used for testing.
 */
public class DummyOriginalAuthor implements OriginalAuthor {

  private final Author author;

  public DummyOriginalAuthor(String name, String email) {
    this.author = new Author(name, email);
  }

  @Override
  public String getId() {
    return author.getEmail();
  }

  @Override
  public Author resolve() {
    return author;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DummyOriginalAuthor that = (DummyOriginalAuthor) o;
    return Objects.equal(author, that.author);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(author);
  }
}
