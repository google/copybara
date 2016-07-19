package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.testing.EqualsTester;
import com.google.copybara.config.ConfigValidationException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AuthorTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private Author.Yaml yaml;

  @Before
  public void setUp() throws Exception {
    yaml = new Author.Yaml();
  }

  @Test
  public void testAllFields() throws Exception {
    yaml.setName("Foo Bar");
    yaml.setEmail("foo@bar.com");
    Author author = yaml.create();
    assertThat(author.getName()).isEqualTo("Foo Bar");
    assertThat(author.getEmail()).isEqualTo("foo@bar.com");
  }

  @Test
  public void testWrongEmailFormat() throws Exception {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Invalid email format: foo-bar");
    yaml.setEmail("foo-bar");
  }

  @Test
  public void testMissingName() throws Exception {
    yaml.setEmail("foo@bar.com");
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Field 'name' cannot be empty");
    yaml.create();
  }

  @Test
  public void testMissingEmail() throws Exception {
    yaml.setName("Foo Bar");
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Field 'email' cannot be empty");
    yaml.create();
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
