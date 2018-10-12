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

import static com.google.copybara.util.FileUtil.CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.BaselinesWithoutLabelVisitor;
import com.google.copybara.Change;
import com.google.copybara.Endpoint;
import com.google.copybara.Origin;
import com.google.copybara.Origin.Reader.ChangesResponse.EmptyReason;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final DummyEndpoint endpoint = new DummyEndpoint();

  public DummyOrigin() {
    this.fs = Jimfs.newFileSystem();
    this.author = DEFAULT_AUTHOR;
  }

  public static final String LABEL_NAME = "DummyOrigin-RevId";

  public final List<DummyRevision> changes = new ArrayList<>();

  private final Map<String, String> changeIdToGroup = new HashMap<>();

  private ImmutableSetMultimap<String, String> descriptionLabels =
      ImmutableSetMultimap.of("type", getType());

  /**
   * Sets the author to use for the following changes that get added.
   */
  public DummyOrigin setAuthor(Author author) {
    this.author = author;
    return this;
  }

  public void addRevisionToGroup(DummyRevision rev, String group) {
    changeIdToGroup.put(rev.asString(), group);
  }

  public DummyOrigin addSimpleChange(int timestamp) throws IOException {
    addSimpleChange(timestamp, changes.size() + " change");
    return this;
  }

  public DummyOrigin addSimpleChangeWithContextReference(int timestamp, String contextRef)
      throws IOException {
    Path path = fs.getPath("" + changes.size());
    Files.createDirectories(path);
    if (changes.size() > 1) {
      // Copy files from previous change folder to emulate history
      Path previousChangePath = fs.getPath("" + (changes.size() - 1));
      FileUtil.copyFilesRecursively(previousChangePath, path, FAIL_OUTSIDE_SYMLINKS);
    }
    Path writePath = path.resolve("file.txt");
    Files.createDirectories(writePath.getParent());
    Files.write(writePath, String.valueOf(changes.size()).getBytes(StandardCharsets.UTF_8));
    /*matchesGlob=*/
    Path previousChanges = changes.isEmpty() ? null : Iterables.getLast(changes).changesBase;
    changes.add(new DummyRevision(
        "" + changes.size(), changes.size() + " change", author, path,
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault()),
        contextRef, /*referenceLabels=*/ ImmutableListMultimap.of(),
        true, previousChanges, null));
    return this;
  }

  public DummyOrigin addSimpleChange(int timestamp, String message) throws IOException {
    return singleFileChange(timestamp, message, "file.txt", String.valueOf(changes.size()));
  }

  public DummyOrigin singleFileChange(int timestamp, String message, String strPath, String content)
      throws IOException {
    Path path = fs.getPath("" + changes.size());
    Files.createDirectories(path);
    if (changes.size() > 1) {
      // Copy files from previous change folder to emulate history
      Path previousChangePath = fs.getPath("" + (changes.size() - 1));
      FileUtil.copyFilesRecursively(previousChangePath, path, FAIL_OUTSIDE_SYMLINKS);
    }
    Path writePath = path.resolve(strPath);
    Files.createDirectories(writePath.getParent());
    Files.write(writePath, content.getBytes(StandardCharsets.UTF_8));
    addChange(timestamp, path, message, /*matchesGlob=*/true);
    return this;
  }

  public DummyOrigin addChange(int timestamp, Path path, String message, boolean matchesGlob) {
    Path previousChanges = changes.isEmpty() ? null : Iterables.getLast(changes).changesBase;
    changes.add(new DummyRevision(
        "" + changes.size(), message, author, path,
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault()),
        /*contextReference=*/ null, /*referenceLabels=*/ ImmutableListMultimap.of(),
        matchesGlob, previousChanges, ""));
    return this;
  }

  @Override
  public DummyRevision resolve(@Nullable String reference)
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

    private final Glob originFiles;
    final Authoring authoring;

    ReaderImpl(Glob originFiles, Authoring authoring) {
      this.originFiles = Preconditions.checkNotNull(originFiles);
      this.authoring = Preconditions.checkNotNull(authoring);
    }

    @Override
    public Endpoint getFeedbackEndPoint(Console console) {
      return endpoint;
    }

    @Override
    public ImmutableList<DummyRevision> findBaselinesWithoutLabel(DummyRevision startRevision,
        int limit) throws RepoException {
      BaselinesWithoutLabelVisitor<DummyRevision> visitor = new BaselinesWithoutLabelVisitor<>(
          originFiles, limit, /*skipFirst=*/ true);
      visitChanges(startRevision, visitor);
      return visitor.getResult();
    }

    @Override
    public void checkout(DummyRevision rev, Path workdir) throws RepoException {
      try {
        Files.walkFileTree(rev.changesBase, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Path destination = workdir.resolve(rev.changesBase.relativize(file).toString());
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
    public ChangesResponse<DummyRevision> changes(
        @Nullable DummyRevision oldRev, DummyRevision newRev) {

      if (oldRev != null
          && Integer.parseInt(oldRev.asString()) >= Integer.parseInt(newRev.asString())) {
        return ChangesResponse.noChanges(EmptyReason.TO_IS_ANCESTOR);
      }
      int current = (oldRev == null) ? 0 : Integer.parseInt(oldRev.asString()) + 1;

      ImmutableList.Builder<Change<DummyRevision>> result = ImmutableList.builder();
      String group = changeIdToGroup.get(newRev.asString());
      while (current < changes.size()) {
        DummyRevision rev = changes.get(current);
        if (rev.matchesGlob() && Objects.equals(changeIdToGroup.get(rev.asString()), group)) {
          result.add(rev.toChange(authoring));
        }
        if (newRev.equals(rev)) {
          break;
        }
        current++;
      }
      ImmutableList<Change<DummyRevision>> changes = result.build();
      if (changes.isEmpty()) {
        return ChangesResponse.noChanges(EmptyReason.NO_CHANGES);
      }
      return ChangesResponse.forChanges(changes);
    }

    @Override
    public Change<DummyRevision> change(DummyRevision rev) throws RepoException {
      int idx = Integer.parseInt(rev.asString());
      DummyRevision dummyRev;
      try {
        dummyRev = changes.get(idx);
      } catch (IndexOutOfBoundsException e) {
        throw new RepoException(String.format("Reference '%s' not found", rev));
      }
      return dummyRev.toChange(authoring);
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
    return new ReaderImpl(originFiles, authoring);
  }

  @Override
  public String getLabelName() {
    return LABEL_NAME;
  }

  public DummyEndpoint getEndpoint() {
    return endpoint;
  }

  /** Allows customizing the response of #describe to pose as another kind of origin in tests*/
  public void setDescribeResponse(ImmutableSetMultimap<String, String> newResponse) {
    descriptionLabels = newResponse;
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob originFiles) {
    return descriptionLabels;
  }
}
