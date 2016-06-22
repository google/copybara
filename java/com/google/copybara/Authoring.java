// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableBiMap;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;

import java.util.Map;

/**
 * Represents the authors mapping between an origin and a destination.
 *
 * <p>For a given author in the origin, always provides an author in the destination.
 * TODO(danielromero): Use strong type for author and validation
 */
public final class Authoring {

  private final String defaultAuthor;
  private final ImmutableBiMap<String, String> individuals;
  private final Authoring.MappingMode mode;

  Authoring(String defaultAuthor, ImmutableBiMap<String, String> individuals, MappingMode mode) {
    this.defaultAuthor = Preconditions.checkNotNull(defaultAuthor);
    this.individuals = Preconditions.checkNotNull(individuals);
    this.mode = Preconditions.checkNotNull(mode);
  }

  /**
   * Returns the default author used to anonymize or for squash workflows where there is more than
   * one author.
   */
  public String getDefaultAuthor() {
    return defaultAuthor;
  }

  public String getDestinationAuthor(String originAuthor) {
    return lookup(mode == MappingMode.INVERSE ? individuals.inverse() : individuals, originAuthor);
  }

  private String lookup(ImmutableBiMap<String, String> individuals, String originAuthor) {
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
    private ImmutableBiMap<String, String> individuals;
    private MappingMode mode = MappingMode.DIRECT;


    /**
     * Sets the default author for commits in the destination.
     *
     * <p>This field cannot be empty, so there is always an author that can be used in the
     * destination in case there is no mapping for an individual.
     */
    @DocField(description = "Sets the default author for commits in the destination.", required = true)
    public void setDefaultAuthor(String defaultAuthor) throws ConfigValidationException {
      this.defaultAuthor = defaultAuthor;
    }

    /**
     * Sets the mapping of individuals from origin to destination.
     *
     * TODO(danielromero): Load this mapping from an external file.
     */
    @DocField(description = "List of author mappings from origin to destination. "
        + "The mapping needs to be unique.", required = false)
    public void setIndividuals(Map<String, String> individuals) throws ConfigValidationException {
      try {
        this.individuals = ImmutableBiMap.copyOf(individuals);
      } catch (IllegalArgumentException e) {
        // ImmutableBiMap throws IAE if two keys have the same value
        throw new ConfigValidationException(e.getMessage());
      }
    }

    @DocField(description = "Use the given mapping of individuals left to right (DIRECT) or right "
        + "to left (INVERSE). This allows reusing the same mapping from different workflows or "
        + "configurations.",
        required = false, defaultValue = "DIRECT")
    public void setMode(MappingMode mode) {
      this.mode = mode;
    }

    public Authoring withOptions(Options options, String configName)
        throws ConfigValidationException, EnvironmentException {
      if (Strings.isNullOrEmpty(defaultAuthor)) {
        throw new ConfigValidationException("Field 'defaultAuthor' cannot be empty.");
      }
      return new Authoring(defaultAuthor, individuals, mode);
    }
  }

  /**
   * Direction used for the individuals mapping.
   */
  public enum MappingMode {
    /**
     * Use the individuals mapping from left to right.
     */
    @DocField(description = "Use the individuals mapping from left to right.")
    DIRECT,
    /**
     * Use the individuals mapping from right to left.
     */
    @DocField(description = "Use the individuals mapping from right to left.")
    INVERSE
  }
}
