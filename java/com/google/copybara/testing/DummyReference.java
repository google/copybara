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

package com.google.copybara.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Author;
import com.google.copybara.Authoring;
import com.google.copybara.Change;
import com.google.copybara.LabelFinder;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

/**
 * A reference of a change used for testing. This can be used with a {@link DummyOrigin} instance or
 * without an actual {@link Origin} implementation.
 */
public class DummyReference implements Origin.Reference {

  private static final Author DEFAULT_AUTHOR = new Author("Dummy Author", "no-reply@dummy.com");

  private final String reference;
  private final String message;
  private final Author author;
  final Path changesBase;
  private final Long timestamp;
  private final ImmutableMap<String, String> labels;

  public DummyReference(String reference) {
    this(reference, "DummyReference message", DEFAULT_AUTHOR,
        Paths.get("/DummyReference", reference), /*timestamp=*/null);
  }

  DummyReference(
      String reference, String message, Author author, Path changesBase,
      @Nullable Long timestamp) {
    this.reference = Preconditions.checkNotNull(reference);
    this.message = Preconditions.checkNotNull(message);
    this.author = Preconditions.checkNotNull(author);
    this.changesBase = Preconditions.checkNotNull(changesBase);
    this.timestamp = timestamp;

    ImmutableMap.Builder<String, String> labels = ImmutableMap.builder();
    for (String line : message.split("\n")) {
      LabelFinder labelFinder = new LabelFinder(line);
      if (labelFinder.isLabel()) {
        labels.put(labelFinder.getName(), labelFinder.getValue());
      }
    }
    this.labels = labels.build();
  }

  /**
   * Returns an instance equivalent to this one but with the timestamp set to the specified value.
   */
  public DummyReference withTimestamp(long newTimestamp) {
    return new DummyReference(
        this.reference, this.message, this.author, this.changesBase, newTimestamp);
  }

  public DummyReference withAuthor(Author newAuthor) {
    return new DummyReference(
        this.reference, this.message, newAuthor, this.changesBase, this.timestamp);
  }

  @Nullable
  @Override
  public Long readTimestamp() throws RepoException {
    return timestamp;
  }

  @Override
  public String asString() {
    return reference;
  }

  @Override
  public String getLabelName() {
    return DummyOrigin.LABEL_NAME;
  }

  Change<DummyReference> toChange(Authoring authoring) {
    Author safeAuthor = authoring.useAuthor(this.author.getEmail())
        ? this.author
        : authoring.getDefaultAuthor();
    return new Change<>(this, safeAuthor, message, new DateTime(timestamp), labels);
  }

  public Author getAuthor() {
    return author;
  }
}
