// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Change;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * An origin for testing with very basic features. It allows specifying the timestamp of references
 * and populates the workdir with a single file.
 */
public class DummyOrigin implements Origin<DummyOrigin>, Origin.Yaml {

  private final FileSystem fs;

  public DummyOrigin() {
    this.fs = Jimfs.newFileSystem();
  }

  public static final String LABEL_NAME = "DummyOrigin-RevId";

  public List<DummyReference> changes = new ArrayList<>();

  public DummyOrigin addSimpleChange(int timestamp) throws IOException {
    addSimpleChange(timestamp, changes.size() + " change");
    return this;
  }

  public DummyOrigin addSimpleChange(int timestamp, String message) throws IOException {
    int current = changes.size();
    Path path = fs.getPath("" + current);
    Files.createDirectories(path);
    Files.write(path.resolve("file.txt"), String.valueOf(current).getBytes());
    addChange(timestamp, path, message);
    return this;
  }

  public DummyOrigin addChange(long timestamp, Path path, String message) {
    changes.add(new DummyReference("" + changes.size(), message, "Someone", path, timestamp));
    return this;
  }

  public String getHead() {
    if (changes.isEmpty()) {
      throw new IllegalStateException("Empty respository");
    }
    return Integer.toString(changes.size() - 1);
  }
  @Override
  public DummyOrigin withOptions(Options options) {
    return this;
  }

  @Override
  public ReferenceFiles<DummyOrigin> resolve(@Nullable final String reference)
      throws RepoException {
    int idx = changes.size() - 1;
    if (reference != null) {
      try {
        idx = Integer.parseInt(reference);
      } catch (NumberFormatException e) {
        throw new RepoException("Not a well-formatted reference: " + reference, e);
      }
    }
    if (idx >= changes.size()) {
      throw new RepoException("Cannot find any change for " + reference
          + ". Only " + changes.size() + " changes exist");
    }
    return changes.get(idx);
  }

  @Override
  public ImmutableList<Change<DummyOrigin>> changes(Reference<DummyOrigin> oldRef,
      @Nullable Reference<DummyOrigin> newRef) throws RepoException {

    int current = (oldRef == null) ? 0 : Integer.parseInt(oldRef.asString()) + 1;

    ImmutableList.Builder<Change<DummyOrigin>> result = ImmutableList.builder();
    while (current < changes.size()) {
      DummyReference ref = changes.get(current);
      result.add(ref.toChange());
      if (newRef == ref) {
        break;
      }
      current++;
    }
    return result.build();
  }

  @Override
  public Change<DummyOrigin> change(Reference<DummyOrigin> ref) throws RepoException {
    int idx = Integer.parseInt(ref.asString());
    DummyReference dummyRef;
    try {
      dummyRef = changes.get(idx);
    } catch (IndexOutOfBoundsException e) {
      throw new RepoException(String.format("Reference '%s' not found", ref));
    }
    return dummyRef.toChange();
  }

  @Override
  public String getLabelName() {
    return LABEL_NAME;
  }
}
