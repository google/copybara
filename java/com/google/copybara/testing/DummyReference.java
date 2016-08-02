// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Change;
import com.google.copybara.LabelFinder;
import com.google.copybara.Origin;
import com.google.copybara.Origin.OriginalAuthor;
import com.google.copybara.RepoException;

import org.joda.time.DateTime;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nullable;

/**
 * A reference of a change used for testing. This can be used with a {@link DummyOrigin} instance or
 * without an actual {@link Origin} implementation.
 */
public class DummyReference implements Origin.Reference {

  private static final OriginalAuthor DEFAULT_AUTHOR =
      new DummyOriginalAuthor("Dummy Author", "no-reply@dummy.com");

  private final String reference;
  private final String message;
  private final OriginalAuthor originalAuthor;
  final Path changesBase;
  private final Long timestamp;
  private final ImmutableMap<String, String> labels;

  public DummyReference(String reference) {
    this(reference, "DummyReference message", DEFAULT_AUTHOR,
        Paths.get("/DummyReference", reference), /*timestamp=*/null);
  }

  DummyReference(
      String reference, String message, OriginalAuthor originalAuthor, Path changesBase,
      @Nullable Long timestamp) {
    this.reference = Preconditions.checkNotNull(reference);
    this.message = Preconditions.checkNotNull(message);
    this.originalAuthor = Preconditions.checkNotNull(originalAuthor);
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
        this.reference, this.message, this.originalAuthor, this.changesBase, newTimestamp);
  }

  public DummyReference withOriginalAuthor(OriginalAuthor originalAuthor) {
    return new DummyReference(
        this.reference, this.message, originalAuthor, this.changesBase, this.timestamp);
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

  Change<DummyReference> toChange() {
    return new Change<>(this, originalAuthor, message, new DateTime(timestamp), labels);
  }

  public OriginalAuthor getOriginalAuthor() {
    return originalAuthor;
  }
}
