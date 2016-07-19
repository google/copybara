package com.google.copybara;

import static org.hamcrest.CoreMatchers.any;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Authoring.AuthoringMappingMode;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.OptionsBuilder;

import org.junit.Before;
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

  private Author.Yaml authorYaml = new Author.Yaml();
  private Authoring.Yaml yaml;
  private OptionsBuilder options;

  @Before
  public void setUp() throws Exception {
    authorYaml.setName("Copybara");
    authorYaml.setEmail("no-reply@google.com");
    yaml = new Authoring.Yaml();
    options = new OptionsBuilder();
  }

  @Test
  public void testWhitelistMappingDuplicates() throws Exception {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Duplicated whitelist entry 'foo'");
    yaml.setWhitelist(ImmutableList.of("foo", "foo"));
  }

  @Test
  public void testDefaultAuthorNotEmpty() throws Exception {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Field 'defaultAuthor' cannot be empty.");
    yaml.withOptions(options.build(), CONFIG_NAME);
  }

  @Test
  public void testInvalidDefaultAuthor() throws Exception {
    authorYaml = new Author.Yaml();
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Invalid 'defaultAuthor'");
    thrown.expectCause(any(ConfigValidationException.class));
    yaml.setDefaultAuthor(authorYaml);
  }

  @Test
  public void testWhitelistNotEmpty() throws Exception {
    yaml.setDefaultAuthor(authorYaml);
    yaml.setMode(AuthoringMappingMode.WHITELIST);

    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Mode 'WHITELIST' requires a non-empty 'whitelist' field. "
        + "For default mapping, use 'USE_DEFAULT' mode instead.");
    yaml.withOptions(options.build(), CONFIG_NAME);
  }
}
