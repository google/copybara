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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.copybara.Change;
import com.google.copybara.LabelFinder;
import com.google.copybara.Origin;
import com.google.copybara.Revision;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.RepoException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A reference of a change used for testing. This can be used with a {@link DummyOrigin} instance or
 * without an actual {@link Origin} implementation.
 */
public class DummyRevision implements Revision {

  private static final Author DEFAULT_AUTHOR = new Author("Dummy Author", "no-reply@dummy.com");

  private final String reference;
  private final String message;
  private final Author author;
  final Path changesBase;
  private final ZonedDateTime timestamp;
  @Nullable
  private final String contextReference;
  private final ImmutableListMultimap<String, String> referenceLabels;
  private final boolean matchesGlob;
  @Nullable private final Path previousPath;
  private final ImmutableListMultimap<String, String> descriptionLabels;
  @Nullable private final String url;

  public DummyRevision(String reference) {
    this(reference, "DummyReference message", DEFAULT_AUTHOR,
        Paths.get("/DummyReference", reference), /*timestamp=*/null,
        /*contextReference=*/ null, /*referenceLabels=*/ ImmutableListMultimap.of(),
         /*matchesGlob=*/true, /*previousPath=*/null, /*url*/ null);
  }

  public DummyRevision(String reference, @Nullable String contextReference) {
    this(
        reference,
        "DummyReference message",
        DEFAULT_AUTHOR,
        Paths.get("/DummyReference", reference),
        /*timestamp=*/ null,
        /*contextReference=*/ contextReference,
        /*referenceLabels=*/ ImmutableListMultimap.of(),
        /*matchesGlob=*/ true,
        /*previousPath=*/ null, null);
  }

  DummyRevision(
      String reference, String message, Author author, Path changesBase,
      @Nullable ZonedDateTime timestamp, @Nullable String contextReference,
      ImmutableListMultimap<String, String> referenceLabels, boolean matchesGlob,
      @Nullable Path previousPath, @Nullable String url) {
    this.reference = Preconditions.checkNotNull(reference);
    this.message = Preconditions.checkNotNull(message);
    this.author = Preconditions.checkNotNull(author);
    this.changesBase = Preconditions.checkNotNull(changesBase);
    this.timestamp = timestamp;
    this.contextReference = contextReference;
    this.referenceLabels = Preconditions.checkNotNull(referenceLabels);
    this.matchesGlob = matchesGlob;
    this.previousPath = previousPath;

    ImmutableListMultimap.Builder<String, String> labels = ImmutableListMultimap.builder();
    for (String line : message.split("\n")) {
      LabelFinder labelFinder = new LabelFinder(line);
      if (labelFinder.isLabel()) {
        labels.put(labelFinder.getName(), labelFinder.getValue());
      }
    }
    this.descriptionLabels = labels.build();
    this.url = url;
  }

  /**
   * Returns an instance equivalent to this one but with the timestamp set to the specified value.
   */
  public DummyRevision withTimestamp(ZonedDateTime newTimestamp) {
    return new DummyRevision(
        this.reference, this.message, this.author, this.changesBase, newTimestamp,
        this.contextReference, this.referenceLabels, this.matchesGlob, this.previousPath, this.url);
  }

  public DummyRevision withAuthor(Author newAuthor) {
    return new DummyRevision(
        this.reference, this.message, newAuthor, this.changesBase, this.timestamp,
        this.contextReference, this.referenceLabels, this.matchesGlob, this.previousPath, this.url);
  }

  public DummyRevision withContextReference(String contextReference) {
    Preconditions.checkNotNull(contextReference);
    return new DummyRevision(
        this.reference, this.message, this.author, this.changesBase, this.timestamp,
        contextReference, this.referenceLabels, this.matchesGlob, this.previousPath, this.url);
  }

  public DummyRevision withLabels(ImmutableListMultimap<String, String> labels) {
    Preconditions.checkNotNull(labels);
    return new DummyRevision(
        this.reference, this.message, this.author, this.changesBase, this.timestamp,
        this.contextReference, labels, this.matchesGlob, this.previousPath, this.url);
  }

  public DummyRevision withUrl(String url) {
    Preconditions.checkNotNull(url);
    return new DummyRevision(
        this.reference, this.message, this.author, this.changesBase, this.timestamp,
        this.contextReference, this.referenceLabels, this.matchesGlob, this.previousPath, url);
  }

  @Nullable
  @Override
  public ZonedDateTime readTimestamp() throws RepoException {
    return timestamp;
  }

  @Override
  public String asString() {
    return reference;
  }

  public Change<DummyRevision> toChange(Authoring authoring) {
    Author safeAuthor = authoring.useAuthor(this.author.getEmail())
        ? this.author
        : authoring.getDefaultAuthor();
    return new Change<>(this, safeAuthor, message,
                        timestamp, descriptionLabels,
                        computeChangedFiles());
  }

  private ImmutableSet<String> computeChangedFiles() {
    Map<String, String> pathToContent = readAllFiles(changesBase);
    Map<String, String> previousContent = previousPath == null
        ? ImmutableMap.of()
        : readAllFiles(previousPath);

    MapDifference<String, String> diff = Maps.difference(pathToContent, previousContent);

    return ImmutableSet.<String>builder()
        .addAll(diff.entriesOnlyOnLeft().keySet())
        .addAll(diff.entriesOnlyOnRight().keySet())
        .addAll(diff.entriesDiffering().keySet())
        .build();
  }

  private static Map<String, String> readAllFiles(Path basePath) {
    Map<String, String> pathToContent = new HashMap<>();
    try {
      Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          pathToContent.put(basePath.relativize(file).toString(),
                            new String(Files.readAllBytes(file), UTF_8));
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new RuntimeException("Shouldn't happen", e);
    }
    return pathToContent;
  }

  @Nullable
  @Override
  public String contextReference() {
    return contextReference;
  }

  @Override
  public ImmutableListMultimap<String, String> associatedLabels() {
    return referenceLabels;
  }

  public Author getAuthor() {
    return author;
  }

  public boolean matchesGlob() {
    return matchesGlob;
  }

  public String getMessage() {
    return message;
  }

  public String getUrl() {
    return url;
  }

  public ImmutableListMultimap<String, String> getLabels() {
    return ImmutableListMultimap.<String, String>builder().putAll(associatedLabels())
        .putAll(descriptionLabels)
        .build();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("reference", reference)
        .add("message", message)
        .add("author", author)
        .add("changesBase", changesBase)
        .add("timestamp", timestamp)
        .add("descriptionLabels", descriptionLabels)
        .add("url", url)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DummyRevision)) {
      return false;
    }
    DummyRevision that = (DummyRevision) o;
    return Objects.equals(reference, that.reference);
  }

  @Override
  public int hashCode() {

    return Objects.hash(reference);
  }
}
