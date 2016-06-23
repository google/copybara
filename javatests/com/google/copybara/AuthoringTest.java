package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
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
  private static final ImmutableMap<String, String> WHITELIST = ImmutableMap.of(
      "foo <foo@gmail.com>", "foo",
      "bar@gmail.com", "bar");

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private Authoring.Yaml yaml;
  private OptionsBuilder options;

  @Before
  public void setUp() throws Exception {
    yaml = new Authoring.Yaml();
    options = new OptionsBuilder();
  }

  @Test
  public void testPassThruMapping() throws Exception {
    yaml.setDefaultAuthor("copybara-team@google.com");
    yaml.setMode(AuthoringMappingMode.PASS_THRU);
    yaml.setWhitelist(WHITELIST);

    Authoring authoring = yaml.withOptions(options.build(), CONFIG_NAME);

    assertThat(authoring.getDestinationAuthor("bar@gmail.com")).isEqualTo("bar@gmail.com");
  }

  @Test
  public void testWhitelistMapping() throws Exception {
    yaml.setDefaultAuthor("copybara-team@google.com");
    yaml.setMode(AuthoringMappingMode.WHITELIST);
    yaml.setWhitelist(WHITELIST);

    Authoring authoring = yaml.withOptions(options.build(), CONFIG_NAME);

    assertThat(authoring.getDestinationAuthor("bar@gmail.com")).isEqualTo("bar");
    assertThat(authoring.getDestinationAuthor("john@gmail.com"))
        .isEqualTo("copybara-team@google.com");
  }

  @Test
  public void testWhitelistMappingDuplicates() throws Exception {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage(
        "Duplicated whitelist entry 'foo external <foo@mydomain.com>' for keys [foo, bar].");
    yaml.setWhitelist(ImmutableMap.of(
          "foo", "foo external <foo@mydomain.com>",
          "bar", "foo external <foo@mydomain.com>"));
  }

  @Test
  public void testDefaultMapping() throws Exception {
    yaml.setDefaultAuthor("copybara-team@google.com");
    yaml.setMode(AuthoringMappingMode.USE_DEFAULT);
    yaml.setWhitelist(WHITELIST);

    Authoring authoring = yaml.withOptions(options.build(), CONFIG_NAME);

    assertThat(authoring.getDestinationAuthor("bar@gmail.com"))
        .isEqualTo("copybara-team@google.com");
    assertThat(authoring.getDestinationAuthor("john@gmail.com"))
        .isEqualTo("copybara-team@google.com");
  }

  @Test
  public void testDefaultAuthorNotEmpty() throws Exception {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Field 'defaultAuthor' cannot be empty.");
    yaml.withOptions(options.build(), CONFIG_NAME);
  }

  @Test
  public void testWhitelistNotEmpty() throws Exception {
    yaml.setDefaultAuthor("copybara-team@google.com");
    yaml.setMode(AuthoringMappingMode.WHITELIST);

    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Mode 'WHITELIST' requires a non-empty 'whitelist' mapping. "
        + "For default mapping, use 'USE_DEFAULT' mode instead.");
    yaml.withOptions(options.build(), CONFIG_NAME);

  }
}
