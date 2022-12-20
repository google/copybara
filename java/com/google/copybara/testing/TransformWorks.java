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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.DestinationReader;
import com.google.copybara.Endpoint;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.Metadata;
import com.google.copybara.MigrationInfo;
import com.google.copybara.TransformWork;
import com.google.copybara.authoring.Author;
import com.google.copybara.revision.Change;
import com.google.copybara.revision.Changes;
import com.google.copybara.util.console.Console;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Utility methods related to {@link TransformWork}.
 */
public class TransformWorks {

  /**
   * A {@link Changes} object with no changes inside.
   */
  public static final Changes EMPTY_CHANGES = Changes.EMPTY;

  /**
   * Creates an instance with reasonable defaults for testing.
   */
  public static TransformWork of(Path checkoutDir, String msg, Console console) {
    return new TransformWork(
        checkoutDir,
        new Metadata(msg, new Author("foo", "foo@foo.com"), ImmutableSetMultimap.of()),
        Changes.EMPTY,
        console,
        new MigrationInfo(DummyOrigin.LABEL_NAME, /* destinationVisitable= */ null),
        new DummyRevision("1234567890"), c -> new DummyEndpoint(), c -> new DummyEndpoint(),
        () -> DestinationReader.NOT_IMPLEMENTED);
  }

  /** Creates an instance with reasonable defaults for testing, including a DestinationReader */
  public static TransformWork of(
      Path checkoutDir, String msg, Console console, DestinationReader destinationReader) {
    return new TransformWork(
        checkoutDir,
        new Metadata(msg, new Author("foo", "foo@foo.com"), ImmutableSetMultimap.of()),
        Changes.EMPTY,
        console,
        new MigrationInfo(DummyOrigin.LABEL_NAME, /* destinationVisitable= */ null),
        new DummyRevision("1234567890"),
        c -> new DummyEndpoint(),
        c -> new DummyEndpoint(),
        () -> destinationReader);
  }

  /**
   * Creates an instance with reasonable defaults for testing.
   */
  public static TransformWork of(Path checkoutDir, String msg, Console console,
      LazyResourceLoader<Endpoint> originApi,  LazyResourceLoader<Endpoint> destinationApi) {
    return new TransformWork(
        checkoutDir,
        new Metadata(msg, new Author("foo", "foo@foo.com"), ImmutableSetMultimap.of()),
        Changes.EMPTY,
        console,
        new MigrationInfo(DummyOrigin.LABEL_NAME, /* destinationVisitable= */ null),
        new DummyRevision("1234567890"), originApi, destinationApi,
        () -> DestinationReader.NOT_IMPLEMENTED);
  }


  public static Change<DummyRevision> toChange(DummyRevision dummyRevision, Author author) {
    return new Change<>(
        dummyRevision,
        author,
        dummyRevision.getMessage(),
        ZonedDateTime.now(ZoneId.systemDefault()),
        dummyRevision.getLabels(),
        /*changeFiles=*/ null);
  }

  public static Change<DummyRevision> mergeChange(DummyRevision dummyRevision, Author author) {
    return new Change<>(
        dummyRevision,
        author,
        dummyRevision.getMessage(),
        ZonedDateTime.now(ZoneId.systemDefault()),
        dummyRevision.getLabels(),
        null,
        /* merge= */ true,
        /* parents= */ null);
  }
}
