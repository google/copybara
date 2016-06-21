// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the authors mapping between an origin and a destination.
 * TODO(danielromero): Use strong type for author and validation
 */
public final class Authoring {

  private final ImmutableMap<String, String> individuals;
  private final String defaultAuthor;

  Authoring(String defaultAuthor, ImmutableMap<String, String> individuals) {
    this.defaultAuthor = Preconditions.checkNotNull(defaultAuthor);
    this.individuals = Preconditions.checkNotNull(individuals);
  }

  /**
   * Returns the default author used to anonymize or for squash workflows where there is more than
   * one author.
   */
  public String getDefaultAuthor() {
    return defaultAuthor;
  }

  public String getDestinationAuthor(String originAuthor) {
    if (!individuals.containsKey(originAuthor)) {
      return defaultAuthor;
    }
    return individuals.get(originAuthor);
  }

  /**
   * Config builder used by YAML.
   */
  @DocElement(yamlName = "!Authoring",
      description = "Defines the authoring mapping between the origin and destination of the workflow.",
      elementKind = Authoring.class)
  public static final class Yaml {

    private String defaultAuthor;
    private Map<String, String> individuals = new HashMap<>();

    /**
     * Sets the default author for commints in the destination.
     *
     * <p>This field cannot be empty, so there is always an author that can be used in the
     * destination in case there is no mapping for an individual.
     */
    @DocField(description = "Sets the default author for commits in the destination.", required = true)
    public void setDefaultAuthor(String defaultAuthor) throws ConfigValidationException {
      this.defaultAuthor = defaultAuthor;
    }

    @DocField(description = "List of author mappings from origin to destination.", required = false)
    public void setIndividuals(Map<String, String> individuals)
        throws ConfigValidationException {
      this.individuals.clear();
      this.individuals.putAll(individuals);
    }

    public Authoring withOptions(Options options, String configName)
        throws ConfigValidationException, EnvironmentException {
      if (Strings.isNullOrEmpty(defaultAuthor)) {
        throw new ConfigValidationException("Field 'defaultAuthor' cannot be empty.");
      }
      return new Authoring(defaultAuthor, ImmutableMap.copyOf(individuals));
    }
  }
}
