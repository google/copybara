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
import java.util.Map.Entry;

/**
 * Represents the authors mapping between an origin and a destination.
 *
 * <p>For a given author in the origin, always provides an author in the destination.
 */
final class Authoring {

  private final String defaultAuthor;
  private final AuthoringMappingMode mode;
  private final ImmutableMap<String, String> individuals;

  Authoring(
      String defaultAuthor, AuthoringMappingMode mode, ImmutableMap<String, String> individuals) {
    this.defaultAuthor = Preconditions.checkNotNull(defaultAuthor);
    this.mode = Preconditions.checkNotNull(mode);
    this.individuals = Preconditions.checkNotNull(individuals);
  }

  /**
   * Returns the default author for squash workflows where there is more than one author.
   */
  public String getDefaultAuthor() {
    return defaultAuthor;
  }

  String getDestinationAuthor(String originAuthor) {
    switch (mode) {
      case PASS_THRU:
        return originAuthor;
      case USE_DEFAULT:
        return defaultAuthor;
      case WHITELIST:
        return getWhitelistAuthor(originAuthor);
      default:
        throw new IllegalStateException(String.format("Mode '%s' not implemented.", mode));
    }
  }

  private String getWhitelistAuthor(String originAuthor) {
    if (!individuals.containsKey(originAuthor)) {
      return defaultAuthor;
    }
    return individuals.get(originAuthor);
  }

  /**
   * Config builder used by YAML.
   */
  @DocElement(yamlName = "!Authoring",
      description = "Defines the authoring mapping between the origin and destination of the "
          + "workflow.",
      elementKind = Authoring.class)
  public static final class Yaml {

    private String defaultAuthor;
    private ImmutableMap<String, String> whitelist = ImmutableMap.of();
    private AuthoringMappingMode mode = AuthoringMappingMode.USE_DEFAULT;


    /**
     * Sets the default author for commits in the destination.
     *
     * <p>This field cannot be empty, so there is always an author that can be used in the
     * destination in case there is no mapping for an individual.
     */
    @DocField(description = "Sets the default author for commits in the destination.",
        required = true)
    public void setDefaultAuthor(String defaultAuthor) throws ConfigValidationException {
      this.defaultAuthor = defaultAuthor;
    }

    @DocField(description = "Mode used for author mapping from origin to destination.",
        required = false, defaultValue = "USE_DEFAULT")
    public void setMode(AuthoringMappingMode mode) {
      this.mode = mode;
    }

    /**
     * Sets the mapping of whitelisted authors from origin to destination.
     *
     * TODO(danielromero): Load this mapping from an external file.
     * TODO(danielromero): Replace Map<String, String> by Map<String, Author>
     */
    @DocField(description = "List of whitelisted authors, mapped from origin to destination. "
        + "The mapping needs to be unique.", required = false)
    public void setWhitelist(Map<String, String> whitelist) throws ConfigValidationException {
      Map<String, String> whitelistInverseMap = new HashMap<>();
      for (Entry<String, String> whitelistEntry : whitelist.entrySet()) {
        String fromAuthor = whitelistEntry.getKey();
        String toAuthor = whitelistEntry.getValue();
        if (whitelistInverseMap.containsKey(toAuthor)) {
          throw new ConfigValidationException(
              String.format("Duplicated whitelist entry '%s' for keys [%s, %s].",
                  toAuthor, whitelistInverseMap.get(toAuthor), fromAuthor));
        }
        whitelistInverseMap.put(toAuthor, fromAuthor);
      }
      this.whitelist = ImmutableMap.copyOf(whitelist);
    }

    public Authoring withOptions(Options options, String configName)
        throws ConfigValidationException, EnvironmentException {
      if (Strings.isNullOrEmpty(defaultAuthor)) {
        throw new ConfigValidationException("Field 'defaultAuthor' cannot be empty.");
      }
      if (mode == AuthoringMappingMode.WHITELIST && whitelist.isEmpty()) {
        throw new ConfigValidationException(
            "Mode 'WHITELIST' requires a non-empty 'whitelist' mapping. "
                + "For default mapping, use 'USE_DEFAULT' mode instead.");
      }
      return new Authoring(defaultAuthor, mode, whitelist);
    }
  }

  /**
   * Mode used for author mapping from origin to destination.
   */
  public enum AuthoringMappingMode {
    /**
     * Use the default author for all the submits in the destination.
     */
    @DocField(description = "Use the default author for all the submits in the destination.")
    USE_DEFAULT,
    /**
     * Use the origin author as the author in the destination, no whitelisting.
     */
    @DocField(description =
        "Use the origin author as the author in the destination, no whitelisting.")
    PASS_THRU,
    /**
     * Use the whitelist map to translate origin authors to destination. Use the default author for
     * non-whitelisted authors.
     */
    @DocField(description = "Use the whitelist map to translate origin authors to destination. "
        + "Use the default author for non-whitelisted authors.")
    WHITELIST
  }
}
