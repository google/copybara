/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.authoring;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.doc.annotations.Example;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkList;
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
   * {@link AuthoringMappingMode#OVERWRITE} mode and for non-whitelisted authors.
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
   * Returns true if the user can be safely used.
   */
  public boolean useAuthor(String userId) {
    switch (mode) {
      case PASS_THRU:
        return true;
      case OVERWRITE:
        return false;
      case WHITELISTED:
        return whitelist.contains(userId);
      default:
        throw new IllegalStateException(String.format("Mode '%s' not implemented.", mode));
    }
  }

  @SkylarkModule(
      name = "authoring",
      namespace = true,
      doc = "The authors mapping between an origin and a destination",
      category = SkylarkModuleCategory.BUILTIN)
  public static final class Module {

    @SkylarkCallable(name = "overwrite",
        doc = "Use the default author for all the submits in the destination. Note that some"
            + " destinations might choose to ignore this author and use the current user running"
            + " the tool (In other words they don't allow impersonation).",
        parameters = {
            @Param(name = "default", type = String.class, named = true,
                doc = "The default author for commits in the destination"),
        }, useLocation = true)
    @Example(title = "Overwrite usage example",
        before = "Create an authoring object that will overwrite any origin author with"
            + " noreply@foobar.com mail.",
        code = "authoring.overwrite(\"Foo Bar <noreply@foobar.com>\")")
    public Authoring overwrite(String defaultAuthor, Location location)
        throws EvalException {
      return new Authoring(Author.parse(location, defaultAuthor),
          AuthoringMappingMode.OVERWRITE,
          ImmutableSet.of());
    }

    @Example(title = "Pass thru usage example", before = "",
        code = "authoring.pass_thru(default = \"Foo Bar <noreply@foobar.com>\")")
    @SkylarkCallable(name = "pass_thru",
        doc = "Use the origin author as the author in the destination, no whitelisting.",
        parameters = {
            @Param(name = "default", type = String.class,  named = true,
                doc = "The default author for commits in the destination. This is used"
                    + " in squash mode workflows or if author cannot be determined."),
        }, useLocation = true)
    public Authoring passThru(String defaultAuthor, Location location)
          throws EvalException {
        return new Authoring(Author.parse(location, defaultAuthor),
            AuthoringMappingMode.PASS_THRU,
            ImmutableSet.of());
      }

    @SkylarkCallable(
        name = "whitelisted",
        doc = "Create an individual or team that contributes code.",
        parameters = {
          @Param(
              name = "default",
              type = String.class,
              named = true,
              doc =
                  "The default author for commits in the destination. This is used"
                      + " in squash mode workflows or when users are not whitelisted."),
          @Param(
              name = "whitelist",
              type = SkylarkList.class,
              generic1 = String.class,
              named = true,
              doc = "List of white listed authors in the origin. The authors must be unique"),
        },
        useLocation = true)
    @Example(
        title = "Only pass thru whitelisted users",
        before = "",
        code =
            "authoring.whitelisted(\n"
                + "    default = \"Foo Bar <noreply@foobar.com>\",\n"
                + "    whitelist = [\n"
                + "       \"someuser@myorg.com\",\n"
                + "       \"other@myorg.com\",\n"
                + "       \"another@myorg.com\",\n"
                + "    ],\n"
                + ")")
    @Example(
        title = "Only pass thru whitelisted LDAPs/usernames",
        before =
            "Some repositories are not based on email but use LDAPs/usernames. This is also"
                + " supported since it is up to the origin how to check whether two authors are"
                + " the same.",
        code =
            "authoring.whitelisted(\n"
                + "    default = \"Foo Bar <noreply@foobar.com>\",\n"
                + "    whitelist = [\n"
                + "       \"someuser\",\n"
                + "       \"other\",\n"
                + "       \"another\",\n"
                + "    ],\n"
                + ")")
    public Authoring whitelisted(
        String defaultAuthor,
        SkylarkList<?> whitelist, // <String>
        Location location)
        throws EvalException {
      return new Authoring(
          Author.parse(location, defaultAuthor),
          AuthoringMappingMode.WHITELISTED,
          createWhitelist(
              location,
              whitelist.getContents(String.class, "whitelist"))); // can't import SkylarkUtil here
      }

    private static ImmutableSet<String> createWhitelist(Location location, List<String> whitelist)
        throws EvalException {
      if (whitelist.isEmpty()) {
        throw new EvalException(location, "'whitelisted' function requires a non-empty 'whitelist'"
            + " field. For default mapping, use 'overwrite(...)' mode instead.");
      }
      Set<String> uniqueAuthors = new HashSet<>();
      for (String author : whitelist) {
        if (!uniqueAuthors.add(author)) {
          // TODO(danielromero): Use SkylarkUtil.check (needs refactoring deps)
          throw new EvalException(location,
              String.format("Duplicated whitelist entry '%s'", author));
        }
      }
      return ImmutableSet.copyOf(whitelist);
    }
  }

  /**
   * Mode used for author mapping from origin to destination.
   *
   * <p>This enum is our internal representation for the different Skylark built-in functions.
   */
  public enum AuthoringMappingMode {
    /**
     * Corresponds with {@link Authoring.Module#overwrite(String, Location)} built-in function.
     */
    OVERWRITE,
    /**
     * Corresponds with {@link Authoring.Module#passThru(String, Location)} built-in function.
     */
    PASS_THRU,
    /**
     * Corresponds with {@link Authoring.Module#whitelisted(String, SkylarkList, Location)}
     * built-in function.
     */
    WHITELISTED
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
