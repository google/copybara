// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * A YAML parser for the configuration.
 */
public class YamlParser {

  // An instance of the snakeyaml reader which doesn't do any implicit conversions.
  private final Yaml yaml;

  public YamlParser() {
    Constructor constructor = new Constructor(Config.Builder.class);
    constructor.addTypeDescription(
        new TypeDescription(ReplaceRegex.Builder.class, new Tag("!ReplaceRegex")));
    this.yaml = new Yaml(constructor, new Representer(), new DumperOptions(), new Resolver());
  }

  /**
   * Parse a YAML content and return a {@link Config} object.
   *
   * @param content yaml text representing a config object
   */
  public Config parse(String content) {
    // TODO(matvore): The exceptions printed as a result of a bad configuration are hard to read.
    // It can include a long stack trace plus a nested cause. Find a way to make the error output
    // more digestable.
    return ((Config.Builder) yaml.load(content)).build();
  }
}
