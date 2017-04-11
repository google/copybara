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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.Change;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/**
 * An origin for testing with very basic features. It allows specifying the timestamp of references
 * and populates the workdir with a single file.
 */
public class DummyOrigin implements Origin<DummyRevision> {

  private static final Author DEFAULT_AUTHOR = new Author("Dummy Author", "no-reply@dummy.com");
  public static final String HEAD = "HEAD";

  private final FileSystem fs;
  private Author author;

  public DummyOrigin() {
    this.fs = Jimfs.newFileSystem();
    this.author = DEFAULT_AUTHOR;
  }

  public static final String LABEL_NAME = "DummyOrigin-RevId";

  public final List<DummyRevision> changes = new ArrayList<>();

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
     return addChange(timestamp, message, ImmutableMap.of(strPath, content));
  }

  public DummyOrigin addChange(int timestamp, String message, Map<String, String> pathToContent)
      throws IOException {
    Path path = fs.getPath("" + changes.size());
    Files.createDirectories(path);
    for (Entry<String, String> entry : pathToContent.entrySet()) {
      Path writePath = path.resolve(entry.getKey());
      Files.createDirectories(writePath.getParent());
      Files.write(writePath, entry.getValue().getBytes(StandardCharsets.UTF_8));
    }
    addChange(timestamp, path, message, /*matchesGlob=*/true);
    return this;
  }

  public DummyOrigin addChange(int timestamp, Path path, String message, boolean matchesGlob) {
    Path previousChanges = changes.isEmpty() ? null : Iterables.getLast(changes).changesBase;
    changes.add(new DummyRevision(
        "" + changes.size(), message, author, path,
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault()),
        /*contextReference=*/ null, /*referenceLabels=*/ ImmutableMap.of(),
        matchesGlob, previousChanges));
    return this;
  }

  @Override
  public DummyRevision resolve(@Nullable final String reference)
      throws RepoException, CannotResolveRevisionException {
    if (HEAD.equals(reference)) {
      if (changes.isEmpty()) {
        throw new IllegalStateException("Empty respository");
      }
      return changes.get(changes.size() - 1).withContextReference(HEAD);
    }
    int idx = changes.size() - 1;
    if (reference != null) {
      try {
        idx = Integer.parseInt(reference);
      } catch (NumberFormatException e) {
        throw new RepoException("Not a well-formatted reference: " + reference, e);
      }
    }
    if (idx >= changes.size()) {
      throw new CannotResolveRevisionException("Cannot find any change for " + reference
          + ". Only " + changes.size() + " changes exist");
    }
    return changes.get(idx);
  }

  private class ReaderImpl implements Reader<DummyRevision> {

    final Authoring authoring;

    ReaderImpl(Authoring authoring) {
      this.authoring = Preconditions.checkNotNull(authoring);
    }

    @Override
    public void checkout(final DummyRevision ref, final Path workdir) throws RepoException {
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
    public ImmutableList<Change<DummyRevision>> changes(
        @Nullable DummyRevision oldRef, DummyRevision newRef) throws RepoException {

      if (oldRef != null
          && Integer.parseInt(oldRef.asString()) >= Integer.parseInt(newRef.asString())) {
        return ImmutableList.of();
      }
      int current = (oldRef == null) ? 0 : Integer.parseInt(oldRef.asString()) + 1;

      ImmutableList.Builder<Change<DummyRevision>> result = ImmutableList.builder();
      while (current < changes.size()) {
        DummyRevision ref = changes.get(current);
        if (ref.matchesGlob()) {
          result.add(ref.toChange(authoring));
        }
        if (newRef == ref) {
          break;
        }
        current++;
      }
      return result.build();
    }

    @Override
    public Change<DummyRevision> change(DummyRevision ref) throws RepoException {
      int idx = Integer.parseInt(ref.asString());
      DummyRevision dummyRef;
      try {
        dummyRef = changes.get(idx);
      } catch (IndexOutOfBoundsException e) {
        throw new RepoException(String.format("Reference '%s' not found", ref));
      }
      return dummyRef.toChange(authoring);
    }

    @Override
    public void visitChanges(DummyRevision start, ChangesVisitor visitor)
        throws RepoException {
      boolean found = false;
      for (DummyRevision change : Lists.reverse(changes)) {
        // Use ref equality since start could be a revision with contextReference/labels,
        // so Object equality is not good.
        if (change.asString().equals(start.asString())) {
          found = true;
        }
        if (found && change.matchesGlob()) {
          if (visitor.visit(change.toChange(authoring)) == VisitResult.TERMINATE) {
            return;
          }
        }
      }
      if (!found) {
        throw new RepoException(
            String.format("Could not find '%s' reference in the repository. Available changes:\n"
                              + "  %s",
                          start.asString(), Joiner.on("\n  ").join(changes)));
      }
    }
  }

  @Override
  public Reader<DummyRevision> newReader(Glob originFiles, Authoring authoring) {
    return new ReaderImpl(authoring);
  }

  @Override
  public String getLabelName() {
    return LABEL_NAME;
  }
}
