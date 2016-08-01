// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the authors mapping between an origin and a destination.
 *
 * <p>For a given author in the origin, always provides an author in the destination.
 */
@SkylarkModule(
    name = "authoring_class",
    namespace = true,
    doc = "The authors mapping between an origin and a destination",
    category = SkylarkModuleCategory.BUILTIN)
public final class Authoring {

  private final Author defaultAuthor;
  private final AuthoringMappingMode mode;
  private final ImmutableSet<String> whitelist;

  @VisibleForTesting
  public Authoring(
      Author defaultAuthor, AuthoringMappingMode mode, ImmutableSet<String> whitelist) {
    this.defaultAuthor = Preconditions.checkNotNull(defaultAuthor);
    this.mode = Preconditions.checkNotNull(mode);
    this.whitelist = Preconditions.checkNotNull(whitelist);
  }

  /**
   * Returns the mapping mode.
   */
  public AuthoringMappingMode getMode() {
    return mode;
  }

  /**
   * Returns the default author, used for squash workflows,
   * {@link AuthoringMappingMode#USE_DEFAULT} mode and for non-whitelisted authors.
   */
  public Author getDefaultAuthor() {
    return defaultAuthor;
  }

  /**
   * Returns a {@code Set} of whitelisted author identifiers.
   *
   * <p>An identifier is typically an email but might have different representations depending on
   * the origin.
   */
  public ImmutableSet<String> getWhitelist() {
    return whitelist;
  }

  /**
   * Resolves an origin author to a destination one.
   */
  public Author resolve(Author author) {
    switch (mode) {
      case PASS_THRU:
        return author;
      case USE_DEFAULT:
        return defaultAuthor;
      case WHITELIST:
        return whitelist.contains(author.getEmail())
            ? author
            : defaultAuthor;
      default:
        throw new IllegalStateException(
            String.format("Mode '%s' not implemented.", mode));
    }
  }

  @SkylarkModule(
      name = "authoring",
      namespace = true,
      doc = "The authors mapping between an origin and a destination",
      category = SkylarkModuleCategory.BUILTIN)
  public static final class Module {

    @SkylarkSignature(name = "overwrite", returnType = Authoring.class,
        doc = "Use the default author for all the submits in the destination.",
        parameters = {
            @Param(name = "default", type = String.class,
                doc = "The default author for commits in the destination"),
        },
        objectType = Module.class, useLocation = true)
    public static final BuiltinFunction OVERWRITE = new BuiltinFunction("overwrite") {
      public Authoring invoke(String defaultAuthor, Location location)
          throws EvalException, ConfigValidationException {
        return new Authoring(parseAuthoring(location, defaultAuthor),
            AuthoringMappingMode.USE_DEFAULT,
            ImmutableSet.<String>of());
      }
    };

    private static Author parseAuthoring(Location location, String defaultAuthor)
        throws EvalException {
      try {
        return Author.parse(defaultAuthor);
      } catch (ConfigValidationException e) {
        throw new EvalException(location, e.getMessage());
      }
    }

    @SkylarkSignature(name = "pass_thru", returnType = Authoring.class,
        doc = "Use the origin author as the author in the destination, no whitelisting.",
        parameters = {
            @Param(name = "default", type = String.class,
                doc = "The default author for commits in the destination. This is used"
                    + " in squash mode workflows"),
        },
        objectType = Module.class, useLocation = true)
    public static final BuiltinFunction PASS_THRU = new BuiltinFunction("pass_thru") {
      public Authoring invoke(String defaultAuthor, Location location)
          throws EvalException {
        return new Authoring(parseAuthoring(location, defaultAuthor),
            AuthoringMappingMode.PASS_THRU,
            ImmutableSet.<String>of());
      }
    };

    @SkylarkSignature(name = "whitelisted", returnType = Authoring.class,
        doc = "Create an individual or team that contributes code.",
        parameters = {
            @Param(name = "default", type = String.class,
                doc = "The default author for commits in the destination. This is used"
                    + " in squash mode workflows or when users are not whitelisted."),
            @Param(name = "whitelist", type = SkylarkList.class,
                generic1 = String.class,
                doc = "List of white listed authors in the origin. The authors must be unique"),
        },
        objectType = Module.class, useLocation = true)
    public static final BuiltinFunction WHITELISTED = new BuiltinFunction("whitelisted") {
      public Authoring invoke(String defaultAuthor, SkylarkList<String> whitelist,
          Location location)
          throws EvalException {
        return new Authoring(parseAuthoring(location, defaultAuthor),
            AuthoringMappingMode.WHITELIST,
            createWhitelist(location, Type.STRING_LIST.convert(whitelist, "whitelist")));
      }
    };

    private static ImmutableSet<String> createWhitelist(Location location, List<String> whitelist)
        throws EvalException {
      if (whitelist.isEmpty()) {
        throw new EvalException(location, "'whitelisted' function requires a non-empty 'whitelist'"
            + " field. For default mapping, use 'overwrite(...)' mode instead.");
      }
      Set<String> uniqueAuthors = new HashSet<>();
      for (String author : whitelist) {
        if (!uniqueAuthors.add(author)) {
          throw new EvalException(location,
              String.format("Duplicated whitelist entry '%s'", author));
        }
      }
      return ImmutableSet.copyOf(whitelist);
    }
  }
  /**
   * Config builder used by YAML.
   */
  @DocElement(yamlName = "!Authoring",
      description = "Defines the authoring mapping between the origin and destination of the "
          + "workflow.",
      elementKind = Authoring.class)
  public static final class Yaml {

    private Author defaultAuthor;
    private ImmutableSet<String> whitelist = ImmutableSet.of();
    private AuthoringMappingMode mode = AuthoringMappingMode.USE_DEFAULT;


    /**
     * Sets the default author for commits in the destination.
     *
     * <p>This field cannot be empty, so there is always an author that can be used in the
     * destination in case there is no mapping for an individual.
     */
    @DocField(description = "Sets the default author for commits in the destination.",
        required = true)
    public void setDefaultAuthor(Author.Yaml defaultAuthor) throws ConfigValidationException {
      try {
        this.defaultAuthor = defaultAuthor.create();
      } catch (ConfigValidationException e) {
        throw new ConfigValidationException("Invalid 'defaultAuthor'.", e);
      }
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
     */
    @DocField(description = "List of whitelisted authors in the origin. "
        + "The authors must be unique.", required = false)
    public void setWhitelist(List<String> whitelist) throws ConfigValidationException {
      Set<String> uniqueAuthors = new HashSet<>();
      for (String author : whitelist) {
        if (!uniqueAuthors.add(author)) {
          throw new ConfigValidationException(
              String.format("Duplicated whitelist entry '%s'", author));
        }
      }
      this.whitelist = ImmutableSet.copyOf(whitelist);
    }

    public Authoring withOptions()
        throws ConfigValidationException, EnvironmentException {
      if (this.defaultAuthor == null) {
        throw new ConfigValidationException("Field 'defaultAuthor' cannot be empty.");
      }
      if (mode == AuthoringMappingMode.WHITELIST && whitelist.isEmpty()) {
        throw new ConfigValidationException(
            "Mode 'WHITELIST' requires a non-empty 'whitelist' field. "
                + "For default mapping, use 'USE_DEFAULT' mode instead.");
      }
      return new Authoring(defaultAuthor, mode, whitelist);
    }
  }

  /**
   * Mode used for author mapping from origin to destination.
   */
  @SkylarkModule(
      name = "authoring_mode",
      doc = "Mode used for author mapping from origin to destination",
      category = SkylarkModuleCategory.TOP_LEVEL_TYPE)
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Authoring authoring = (Authoring) o;
    return Objects.equals(defaultAuthor, authoring.defaultAuthor) &&
        mode == authoring.mode &&
        Objects.equals(whitelist, authoring.whitelist);
  }

  @Override
  public int hashCode() {
    return Objects.hash(defaultAuthor, mode, whitelist);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("defaultAuthor", defaultAuthor)
        .add("mode", mode)
        .add("whitelist", whitelist)
        .toString();
  }
}
