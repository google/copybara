// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Options;
import com.google.copybara.git.GitRepository;
import com.google.copybara.transform.ReplaceRegex;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * A YAML parser for the configuration.
 */
public final class YamlParser {

  // An instance of the snakeyaml reader which doesn't do any implicit conversions.
  private final Yaml yaml;

  public YamlParser(Iterable<TypeDescription> typeDescriptions) {

    Constructor constructor = new Constructor(Config.Yaml.class);
    for (TypeDescription typeDescription : typeDescriptions) {
      constructor.addTypeDescription(typeDescription);
    }
    this.yaml = new Yaml(constructor, new Representer(), new DumperOptions(), new Resolver());
  }

  /**
   * Load a YAML content, configure it with the program {@code Options} and return a {@link Config}
   * object.
   *
   * @param path a file representing a YAML Copybara configuration
   * @param options the options passed to the Copybara command
   * @throws NoSuchFileException in case the config file cannot be found
   * @throws IOException if the config file cannot be load
   */
  public Config loadConfig(Path path, Options options)
      throws IOException, NoSuchFileException {
    String configContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    // TODO(matvore): The exceptions printed as a result of a bad configuration are hard to read.
    // It can include a long stack trace plus a nested cause. Find a way to make the error output
    // more digestable.
    return ((Config.Yaml) yaml.load(configContent)).withOptions(options);
  }

  /**
   * The transformations, repository types, etc. allowed in the configuration.
   */
  public static YamlParser createParser() {
    ImmutableList<TypeDescription> types = ImmutableList.of(
        new TypeDescription(ReplaceRegex.Yaml.class, "!ReplaceRegex"),
        new TypeDescription(GitRepository.Yaml.class, "!GitRepository"));
    return new YamlParser(types);
  }
}
