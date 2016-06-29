package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.OptionsBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AuthorTest {

  private static final String CONFIG_NAME = "copybara_project";

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private Author.Yaml yaml;
  private OptionsBuilder options;

  @Before
  public void setUp() throws Exception {
    yaml = new Author.Yaml();
    options = new OptionsBuilder();
  }

  @Test
  public void testNoMandatoryEmail() throws Exception {
    yaml.setName("Foo Bar");
    Author author = yaml.withOptions(options.build(), CONFIG_NAME);
    assertThat(author.getEmail()).isNull();
  }

  @Test
  public void testAllFields() throws Exception {
    yaml.setName("Foo Bar");
    yaml.setEmail("foo@bar.com");
    Author author = yaml.withOptions(options.build(), CONFIG_NAME);
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
  public void testToString() throws Exception {
    assertThat(new Author("Foo Bar", "foo@bar.com").toString())
        .isEqualTo("Foo Bar <foo@bar.com>");
    // An empty email is a valid author label
    assertThat(new Author("Foo Bar", /*email*/null).toString())
        .isEqualTo("Foo Bar <>");
  }
}
