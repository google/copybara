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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.Author;
import com.google.copybara.Authoring;
import com.google.copybara.Change;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * An origin for testing with very basic features. It allows specifying the timestamp of references
 * and populates the workdir with a single file.
 */
public class DummyOrigin implements Origin<DummyReference> {

  private static final Author DEFAULT_AUTHOR = new Author("Dummy Author", "no-reply@dummy.com");

  private final FileSystem fs;
  private Author author;

  public DummyOrigin() {
    this.fs = Jimfs.newFileSystem();
    this.author = DEFAULT_AUTHOR;
  }

  public static final String LABEL_NAME = "DummyOrigin-RevId";

  public final List<DummyReference> changes = new ArrayList<>();

  /**
   * Sets the author to use for the following changes that get added.
   */
  public DummyOrigin setAuthor(Author author) {
    this.author = author;
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

  public DummyOrigin addChange(int timestamp, Path path, String message) {
    changes.add(new DummyReference(
        "" + changes.size(), message, author, path, Instant.ofEpochSecond(timestamp)));
    return this;
  }

  public String getHead() {
    if (changes.isEmpty()) {
      throw new IllegalStateException("Empty respository");
    }
    return Integer.toString(changes.size() - 1);
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

  private class ReaderImpl implements Reader<DummyReference> {

    final Authoring authoring;

    ReaderImpl(Authoring authoring) {
      this.authoring = Preconditions.checkNotNull(authoring);
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
    public ImmutableList<Change<DummyReference>> changes(
        DummyReference oldRef, @Nullable DummyReference newRef) throws RepoException {

      int current = (oldRef == null) ? 0 : Integer.parseInt(oldRef.asString()) + 1;

      ImmutableList.Builder<Change<DummyReference>> result = ImmutableList.builder();
      while (current < changes.size()) {
        DummyReference ref = changes.get(current);
        result.add(ref.toChange(authoring));
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
      return dummyRef.toChange(authoring);
    }

    @Override
    public void visitChanges(DummyReference start, ChangesVisitor<DummyReference> visitor)
        throws RepoException {
      boolean found = false;
      for (DummyReference change : Lists.reverse(changes)) {
        if (change.equals(start)) {
          found = true;
        }
        if (found) {
          if (visitor.visit(change.toChange(authoring)) == VisitResult.TERMINATE) {
            return;
          }
        }
      }
      if (!found) {
        throw new RepoException(
            "Could not find " + start.asString() + " reference in the repository");
      }
    }
  }

  public Reader<DummyReference> newReader(Glob originFiles, Authoring authoring) {
    return new ReaderImpl(authoring);
  }

  @Override
  public String getLabelName() {
    return LABEL_NAME;
  }
}
