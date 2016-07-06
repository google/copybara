package com.google.copybara.testing;

import com.google.copybara.Author;
import com.google.copybara.Authoring;
import com.google.copybara.Authoring.AuthoringMappingMode;
import com.google.copybara.config.ConfigValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows building valid Authoring Yaml instances for testing purpose.
 */
public class AuthoringYamlBuilder {

  private Author.Yaml defaultAuthor = new Author.Yaml();
  private AuthoringMappingMode mode = AuthoringMappingMode.USE_DEFAULT;
  private List<String> whitelist = new ArrayList<>();

  public AuthoringYamlBuilder() throws ConfigValidationException {
    defaultAuthor.setName("Copybara");
    defaultAuthor.setEmail("no-reply@google.com");
  }

  public final AuthoringYamlBuilder setDefaultAuthor(String name, String email)
      throws ConfigValidationException {
    defaultAuthor = new Author.Yaml();
    defaultAuthor.setName(name);
    defaultAuthor.setEmail(email);
    return this;
  }

  public final AuthoringYamlBuilder setMode(AuthoringMappingMode mode) {
    this.mode = mode;
    return this;
  }

  public final AuthoringYamlBuilder setWhitelist(List<String> whitelist) {
    this.whitelist = new ArrayList<>(whitelist);
    return this;
  }

  public Authoring.Yaml build() throws ConfigValidationException {
    Authoring.Yaml yaml = new Authoring.Yaml();
    yaml.setDefaultAuthor(defaultAuthor);
    yaml.setMode(mode);
    yaml.setWhitelist(whitelist);
    return yaml;
  }
}
