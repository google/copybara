// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Change;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;

import org.joda.time.DateTime;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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

  private static final String DUMMY_REV_ID = "Dummy-RevId";

  public List<DummyReference> changes = new ArrayList<>();

  private class DummyReference implements ReferenceFiles<DummyOrigin> {

    private final int reference;
    private final String message;
    private final String author;
    private final Path changesBase;
    private final long timestamp;

    private DummyReference(int reference, String message, String author, Path changesBaseDir,
        long timestamp) {
      this.timestamp = timestamp;
      this.message = message;
      this.changesBase = changesBaseDir;
      this.author = author;
      this.reference = reference;
    }

    @Override
    public void checkout(final Path workdir) throws RepoException {
      try {
        Files.walkFileTree(changesBase, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Path destination = workdir.resolve(changesBase.relativize(file).toString());
            Files.createDirectories(destination.getParent());
            Files.write(destination, Files.readAllBytes(file));
            return FileVisitResult.CONTINUE;
          }
        });
      } catch (IOException e) {
        throw new RepoException("Error copying files", e);
      }
    }

    @Nullable
    @Override
    public Long readTimestamp() throws RepoException {
      return timestamp;
    }

    @Override
    public String asString() {
      return String.valueOf(reference);
    }

    @Override
    public String getLabelName() {
      return DUMMY_REV_ID;
    }

    private Change<DummyOrigin> toChange() {
      return new Change<>(this, author, message, new DateTime(timestamp));
    }

  }

  public void addSimpleChange(int timestamp) throws IOException {
    int current = changes.size();
    Path path = fs.getPath("" + current);
    Files.createDirectories(path);
    Files.write(path.resolve("file.txt"), String.valueOf(current).getBytes());
    addChange(timestamp, path, current + " change");
  }

  public void addChange(int timestamp, Path path, String message) {
    changes.add(new DummyReference(changes.size(), message, "Someone", path, timestamp));
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
  public ReferenceFiles<DummyOrigin> resolve(@Nullable final String reference) {
    int idx = 0;
    if (reference != null) {
      idx = Integer.parseInt(reference);
    }
    if (idx >= changes.size()) {
      throw new IllegalStateException("Cannot find any change for " + reference
          + ". Only " + changes.size() + " changes exist");
    }
    return changes.get(idx);
  }

  @Override
  public ImmutableList<Change<DummyOrigin>> changes(Reference<DummyOrigin> oldRef,
      @Nullable Reference<DummyOrigin> newRef) throws RepoException {

    int current = Integer.parseInt(oldRef.asString()) + 1;

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
  public String getLabelName() {
    return DUMMY_REV_ID;
  }
}
