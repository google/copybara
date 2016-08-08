// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Change;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
public class DummyOrigin implements Origin<DummyReference>, Origin.Yaml {

  private static final OriginalAuthor DEFAULT_AUTHOR =
      new DummyOriginalAuthor("Dummy Author", "no-reply@dummy.com");

  private final FileSystem fs;
  private OriginalAuthor originalAuthor;

  public DummyOrigin() {
    this.fs = Jimfs.newFileSystem();
    this.originalAuthor = DEFAULT_AUTHOR;
  }

  public static final String LABEL_NAME = "DummyOrigin-RevId";

  public List<DummyReference> changes = new ArrayList<>();

  /**
   * Sets the author to use for the following changes that get added.
   */
  public DummyOrigin setOriginalAuthor(OriginalAuthor originalAuthor) {
    this.originalAuthor = originalAuthor;
    return this;
  }

  public DummyOrigin addSimpleChange(int timestamp) throws IOException {
    addSimpleChange(timestamp, changes.size() + " change");
    return this;
  }

  public DummyOrigin addSimpleChange(int timestamp, String message) throws IOException {
    return singleFileChange(timestamp, message, "file.txt", String.valueOf(changes.size()));
  }

  public DummyOrigin singleFileChange(int timestamp, String message, String strPath, String content)
      throws IOException {
    Path path = fs.getPath("" + changes.size());
    Files.createDirectories(path);
    Files.write(path.resolve(strPath), content.getBytes(StandardCharsets.UTF_8));
    addChange(timestamp, path, message);
    return this;
  }

  public DummyOrigin addChange(long timestamp, Path path, String message) {
    changes.add(new DummyReference("" + changes.size(), message, originalAuthor, path, timestamp));
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
  public void checkout(final DummyReference ref, final Path workdir) throws RepoException {
    try {
      Files.walkFileTree(ref.changesBase, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
            throws IOException {
          Path destination = workdir.resolve(ref.changesBase.relativize(file).toString());
          Files.createDirectories(destination.getParent());
          Files.write(destination, Files.readAllBytes(file));
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new RepoException("Error copying files", e);
    }
  }

  @Override
  public DummyReference resolve(@Nullable final String reference) throws RepoException {
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
  public ImmutableList<Change<DummyReference>> changes(
      DummyReference oldRef, @Nullable DummyReference newRef) throws RepoException {

    int current = (oldRef == null) ? 0 : Integer.parseInt(oldRef.asString()) + 1;

    ImmutableList.Builder<Change<DummyReference>> result = ImmutableList.builder();
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
  public Change<DummyReference> change(DummyReference ref) throws RepoException {
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
  public void visitChanges(DummyReference start, ChangesVisitor visitor) throws RepoException {
    boolean found = false;
    for (DummyReference change : Lists.reverse(changes)) {
      if (change.equals(start)) {
        found = true;
      }
      if (found) {
        if (visitor.visit(change.toChange()) == VisitResult.TERMINATE) {
          return;
        }
      }
    }
    if (!found) {
      throw new RepoException(
          "Could not find " + start.asString() + " reference in the repository");
    }
  }

  @Override
  public String getLabelName() {
    return LABEL_NAME;
  }
}
