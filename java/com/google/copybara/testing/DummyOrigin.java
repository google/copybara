// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.common.collect.ImmutableList;
import com.google.copybara.CannotComputeChangesException;
import com.google.copybara.Change;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;

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

  private class DummyReference implements Origin.Reference<DummyOrigin> {
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
  }

  public HashMap<String, Long> referenceToTimestamp = new HashMap<>();

  @Override
  public DummyOrigin withOptions(Options options) {
    return this;
  }

  @Override
  public Reference<DummyOrigin> resolve(@Nullable final String reference) {
    DummyReference wrappedReference = new DummyReference();
    wrappedReference.reference = reference;
    return wrappedReference;
  }

  @Override
  public ImmutableList<Change<DummyOrigin>> changes(Reference<DummyOrigin> oldRef,
      @Nullable Reference<DummyOrigin> newRef) throws RepoException {
    throw new CannotComputeChangesException("not supported");
  }
}
