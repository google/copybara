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

package com.google.copybara.transform.metadata;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Change;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.AuthorParser;
import com.google.copybara.authoring.InvalidAuthorException;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.IntentionalNoop;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import net.starlark.java.eval.EvalException;
import net.starlark.java.syntax.Location;

/**
 * Map authors between revision systems.
 */
public class MapAuthor implements Transformation {

  // Author only uses mail for comparation
  private final ImmutableMap<String, String> authorToAuthor;
  private final ImmutableMap<String, Author> mailToAuthor;
  private final ImmutableMap<String, Author> nameToAuthor;
  private final boolean reversible;
  private final boolean noopReverse;
  private final boolean failIfNotFound;
  private final boolean failIfNotFoundInReverse;
  private final boolean mapAll;
  private final Location location;

  private MapAuthor(Location location, ImmutableMap<String, String> authorToAuthor,
      ImmutableMap<String, Author> mailToAuthor, ImmutableMap<String, Author> nameToAuthor,
      boolean reversible, boolean noopReverse, boolean failIfNotFound,
      boolean failIfNotFoundInReverse, boolean mapAll) {
    this.location = Preconditions.checkNotNull(location);
    this.authorToAuthor = Preconditions.checkNotNull(authorToAuthor);
    this.mailToAuthor = Preconditions.checkNotNull(mailToAuthor);
    this.nameToAuthor = Preconditions.checkNotNull(nameToAuthor);
    this.reversible = reversible;
    this.noopReverse = noopReverse;
    this.failIfNotFound = failIfNotFound;
    this.failIfNotFoundInReverse = failIfNotFoundInReverse;
    this.mapAll = mapAll;
  }

  public static MapAuthor create(Location location, Map<String, String> authorMap,
      boolean reversible, boolean noopReverse, boolean failIfNotFound,
      boolean failIfNotFoundInReverse, boolean mapAll) throws EvalException {
    ImmutableMap.Builder<String, String> authorToAuthor = ImmutableMap.builder();
    ImmutableMap.Builder<String, Author> mailToAuthor = ImmutableMap.builder();
    ImmutableMap.Builder<String, Author> nameToAuthor = ImmutableMap.builder();

    for (Entry<String, String> e : authorMap.entrySet()) {
      Author to = Author.parse(e.getValue());
      try {
        authorToAuthor.put(AuthorParser.parse(e.getKey()).toString(),
            to.toString());
      } catch (InvalidAuthorException ex) {
        if (e.getKey().contains("@")) {
          mailToAuthor.put(e.getKey(), to);
        } else {
          nameToAuthor.put(e.getKey(), to);
        }
      }
    }
    return new MapAuthor(location, authorToAuthor.build(), mailToAuthor.build(),
        nameToAuthor.build(), reversible, noopReverse, failIfNotFound, failIfNotFoundInReverse,
        mapAll);
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    work.setAuthor(getMappedAuthor(work.getAuthor()));

    if (mapAll) {
      for (Change<?> current : work.getChanges().getCurrent()) {
        current.setMappedAuthor(getMappedAuthor(current.getAuthor()));
      }
    }

    return TransformationStatus.success();
  }

  private Author getMappedAuthor(Author originalAuthor) throws ValidationException {
    String newAuthor = authorToAuthor.get(originalAuthor.toString());
    if (newAuthor != null) {
      try {
        return AuthorParser.parse(newAuthor);
      } catch (InvalidAuthorException e) {
        throw new IllegalStateException("Shouldn't happen. We validate before", e);
      }
    }
    Author byMail = mailToAuthor.get(originalAuthor.getEmail());
    if (byMail != null) {
      return byMail;
    }
    Author byName = nameToAuthor.get(originalAuthor.getName());
    if (byName != null) {
      return byName;
    }
    checkCondition(!failIfNotFound, "Cannot find a mapping for author '%s'", originalAuthor);
    return originalAuthor;
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    if (noopReverse) {
      return new ExplicitReversal(IntentionalNoop.INSTANCE, this);
    }
    if (!reversible) {
      throw new NonReversibleValidationException("Author mapping doesn't have reversible enabled");
    } else if (!mailToAuthor.isEmpty()) {
      throw new NonReversibleValidationException(
          String.format(
              "author mapping is not reversible because it contains mail -> author mappings."
                  + " Only author -> author is reversible: %s",
              nameToAuthor));
    } else if (!nameToAuthor.isEmpty()) {
      throw new NonReversibleValidationException(
          String.format(
              "author mapping is not reversible because it contains name -> author mappings."
                  + " Only author -> author is reversible: %s",
              nameToAuthor));
    }

    try {
      ImmutableMap<String, String> reverse = ImmutableBiMap.<String, String>builder()
          .putAll(authorToAuthor).build().inverse();
      return new MapAuthor(location, reverse, ImmutableMap.of(),
          ImmutableMap.of(), reversible, noopReverse, failIfNotFoundInReverse, failIfNotFound,
          mapAll);
    } catch (IllegalArgumentException e) {
      throw new NonReversibleValidationException("non-reversible author map:" + e.getMessage());
    }
  }

  @Override
  public String describe() {
    return "Mapping authors";
  }

  @Override
  public Location location() {
    return location;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("authorToAuthor", authorToAuthor)
        .add("mailToAuthor", mailToAuthor)
        .add("nameToAuthor", nameToAuthor)
        .add("reversible", reversible)
        .add("failIfNotFound", failIfNotFound)
        .add("failIfNotFoundInReverse", failIfNotFoundInReverse)
        .add("location", location)
        .toString();
  }
}
