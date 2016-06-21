package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.OptionsBuilder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AuthoringTest {

  private static final String CONFIG_NAME = "copybara_project";

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testMapping() throws Exception {
    Authoring.Yaml yaml = new Authoring.Yaml();
    yaml.setDefaultAuthor("copybara-team@google.com");
    yaml.setIndividuals(ImmutableMap.of(
        "foo <foo@gmail.com>", "foo",
        "bar@gmail.com", "bar"));

    OptionsBuilder options = new OptionsBuilder();
    Authoring authoring = yaml.withOptions(options.build(), CONFIG_NAME);

    assertThat(authoring.getDestinationAuthor("bar@gmail.com")).isEqualTo("bar");
    assertThat(authoring.getDestinationAuthor("john@gmail.com"))
        .isEqualTo("copybara-team@google.com");
  }

  @Test
  public void testDefaultAuthorNotEmpty() throws Exception {
    Authoring.Yaml yaml = new Authoring.Yaml();
    OptionsBuilder options = new OptionsBuilder();
    
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Field 'defaultAuthor' cannot be empty.");
    yaml.withOptions(options.build(), CONFIG_NAME);
  }

}
