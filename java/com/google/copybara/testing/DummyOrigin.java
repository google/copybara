// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.common.collect.ImmutableList;
import com.google.copybara.CannotComputeChangesException;
import com.google.copybara.Change;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;

import org.joda.time.DateTime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import javax.annotation.Nullable;

/**
 * An origin for testing with very basic features. It allows specifying the timestamp of references
 * and populates the workdir with a single file.
 */
public class DummyOrigin implements Origin<DummyOrigin>, Origin.Yaml {

  private static final String DUMMY_REV_ID = "Dummy-RevId";

  private class DummyReference implements ReferenceFiles<DummyOrigin> {
    String reference;

    @Override
    public Long readTimestamp() {
      return referenceToTimestamp.get(reference);
    }

    @Override
    public void checkout(Path workdir) throws RepoException {
      try {
        Files.createDirectories(workdir);
        Files.write(workdir.resolve("file.txt"), reference.getBytes());
      } catch (IOException e) {
        throw new RepoException("Unexpected error", e);
      }
    }

    @Override
    public String asString() {
      return reference;
    }

    @Override
    public String getLabelName() {
      return DUMMY_REV_ID;
    }
  }

  public HashMap<String, Long> referenceToTimestamp = new HashMap<>();

  @Override
  public DummyOrigin withOptions(Options options) {
    return this;
  }

  @Override
  public ReferenceFiles<DummyOrigin> resolve(@Nullable final String reference) {
    DummyReference wrappedReference = new DummyReference();
    wrappedReference.reference = reference;
    return wrappedReference;
  }

  @Override
  public ImmutableList<Change<DummyOrigin>> changes(Reference<DummyOrigin> oldRef,
      @Nullable Reference<DummyOrigin> newRef) throws RepoException {
    int last = Integer.parseInt(oldRef.asString());
    if (newRef == null) {
      throw new CannotComputeChangesException("new ref not set");
    }
    int current = Integer.parseInt(newRef.asString());
    ImmutableList.Builder<Change<DummyOrigin>> changes = ImmutableList.builder();
    for (int i = last + 1; i <= current; i++) {
      DummyReference dummyReference = new DummyReference();
      dummyReference.reference = i + "";
      changes.add(new Change<>(dummyReference, "someone" + i, "message" + i, new DateTime(i)));
    }
    return changes.build();
  }

  @Override
  public String getLabelName() {
    return DUMMY_REV_ID;
  }
}
