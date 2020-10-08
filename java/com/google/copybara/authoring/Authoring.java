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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.util.console.Console;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;
import net.starlark.java.syntax.Location;

/**
 * Represents the authors mapping between an origin and a destination.
 *
 * <p>For a given author in the origin, always provides an author in the destination.
 */
@StarlarkBuiltin(
    name = "authoring_class",
    doc = "The authors mapping between an origin and a destination")
public final class Authoring implements StarlarkValue {

  private final Author defaultAuthor;
  private final AuthoringMappingMode mode;
  private final ImmutableSet<String> allowlist;

  public Authoring(
      Author defaultAuthor, AuthoringMappingMode mode, ImmutableSet<String> allowlist) {
    this.defaultAuthor = checkNotNull(defaultAuthor);
    this.mode = checkNotNull(mode);
    this.allowlist = checkNotNull(allowlist);
  }

  /**
   * Returns the mapping mode.
   */
  public AuthoringMappingMode getMode() {
    return mode;
  }

  /**
   * Returns the default author, used for squash workflows,
   * {@link AuthoringMappingMode#OVERWRITE} mode and for non-allowed authors.
   */
  public Author getDefaultAuthor() {
    return defaultAuthor;
  }

  /**
   * Returns a {@code Set} of allowed author identifiers.
   *
   * <p>An identifier is typically an email but might have different representations depending on
   * the origin.
   */
  public ImmutableSet<String> getAllowlist() {
    return allowlist;
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
      case ALLOWED:
        return allowlist.contains(userId);
      default:
        throw new IllegalStateException(String.format("Mode '%s' not implemented.", mode));
    }
  }

  /** Starlark Module for authoring. */
  @StarlarkBuiltin(
      name = "authoring",
      doc = "The authors mapping between an origin and a destination")
  public static final class Module implements StarlarkValue {

    public Module(Console console) {}

    @StarlarkMethod(
        name = "overwrite",
        doc =
            "Use the default author for all the submits in the destination. Note that some"
                + " destinations might choose to ignore this author and use the current user"
                + " running the tool (In other words they don't allow impersonation).",
        parameters = {
          @Param(
              name = "default",
              named = true,
              doc = "The default author for commits in the destination"),
        })
    @Example(
        title = "Overwrite usage example",
        before =
            "Create an authoring object that will overwrite any origin author with"
                + " noreply@foobar.com mail.",
        code = "authoring.overwrite(\"Foo Bar <noreply@foobar.com>\")")
    public Authoring overwrite(String defaultAuthor) throws EvalException {
      return new Authoring(
          Author.parse(defaultAuthor), AuthoringMappingMode.OVERWRITE, ImmutableSet.of());
    }

    @Example(
        title = "Pass thru usage example",
        before = "",
        code = "authoring.pass_thru(default = \"Foo Bar <noreply@foobar.com>\")")
    @StarlarkMethod(
        name = "pass_thru",
        doc = "Use the origin author as the author in the destination, no filtering.",
        parameters = {
          @Param(
              name = "default",
              named = true,
              doc =
                  "The default author for commits in the destination. This is used"
                      + " in squash mode workflows or if author cannot be determined."),
        })
    public Authoring passThru(String defaultAuthor) throws EvalException {
      return new Authoring(
          Author.parse(defaultAuthor), AuthoringMappingMode.PASS_THRU, ImmutableSet.of());
      }

    @StarlarkMethod(
        name = "allowed",
        doc = "Create a list for an individual or team contributing code.",
        parameters = {
          @Param(
              name = "default",
              named = true,
              doc =
                  "The default author for commits in the destination. This is used"
                      + " in squash mode workflows or when users are not on the list."),
          @Param(
              name = "allowlist",
              allowedTypes = {@ParamType(type = Sequence.class, generic1 = String.class)},
              named = true,
              doc =
                  "List of  authors in the origin that are allowed to contribute code. The "
                      + "authors must be unique"),
        })
    @Example(
        title = "Only pass thru allowed users",
        before = "",
        code =
            "authoring.allowed(\n"
                + "    default = \"Foo Bar <noreply@foobar.com>\",\n"
                + "    allowlist = [\n"
                + "       \"someuser@myorg.com\",\n"
                + "       \"other@myorg.com\",\n"
                + "       \"another@myorg.com\",\n"
                + "    ],\n"
                + ")")
    @Example(
        title = "Only pass thru allowed LDAPs/usernames",
        before =
            "Some repositories are not based on email but use LDAPs/usernames. This is also"
                + " supported since it is up to the origin how to check whether two authors are"
                + " the same.",
        code =
            "authoring.allowed(\n"
                + "    default = \"Foo Bar <noreply@foobar.com>\",\n"
                + "    allowlist = [\n"
                + "       \"someuser\",\n"
                + "       \"other\",\n"
                + "       \"another\",\n"
                + "    ],\n"
                + ")")
    public Authoring allowed(String defaultAuthor, Sequence<?> allowlist // <String>
        ) throws EvalException {
      return new Authoring(
          Author.parse(defaultAuthor),
          AuthoringMappingMode.ALLOWED,
          createAllowlist(Sequence.cast(allowlist, String.class, "allowed")));
    }

    private static ImmutableSet<String> createAllowlist(List<String> list)
        throws EvalException {
      if (list.isEmpty()) {
        throw Starlark.errorf(
            "'allowed' function requires a non-empty 'allowlist' field. For default mapping,"
                + " use 'overwrite(...)' mode instead.");
      }
      Set<String> uniqueAuthors = new HashSet<>();
      for (String author : list) {
        if (!uniqueAuthors.add(author)) {
          throw Starlark.errorf("Duplicated allowlist entry '%s'", author);
        }
      }
      return ImmutableSet.copyOf(list);
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
     * Corresponds to {@link Authoring.Module#allowed(String, Sequence)} built-in
     * function.
     */
    ALLOWED
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
        Objects.equals(allowlist, authoring.allowlist);
  }

  @Override
  public int hashCode() {
    return Objects.hash(defaultAuthor, mode, allowlist);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("defaultAuthor", defaultAuthor)
        .add("mode", mode)
        .add("allowlist", allowlist)
        .toString();
  }
}
