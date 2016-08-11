package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.testing.EqualsTester;
import com.google.copybara.config.ConfigValidationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AuthorTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testParse() throws Exception {
    Author author = Author.parse("Foo Bar <foo@bar.com>");
    assertThat(author.getName()).isEqualTo("Foo Bar");
    assertThat(author.getEmail()).isEqualTo("foo@bar.com");
  }

  @Test
  public void testWrongEmailFormat() throws Exception {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage(
        "Author 'foo-bar' doesn't match the expected format 'name <mail@example.com>");
    Author.parse("foo-bar");
  }

  @Test
  public void testToString() throws Exception {
    assertThat(new Author("Foo Bar", "foo@bar.com").toString())
        .isEqualTo("Foo Bar <foo@bar.com>");
    // An empty email is a valid author label
    assertThat(new Author("Foo Bar", "").toString())
        .isEqualTo("Foo Bar <>");
  }

  @Test
  public void testEquals() throws Exception {
    new EqualsTester()
        .addEqualityGroup(
            new Author("Foo Bar", "foo@bar.com"), new Author("Foo Bar", "foo@bar.com"))
        .addEqualityGroup(new Author("Copybara", "no-reply@google.com"))
        .testEquals();
  }
}
