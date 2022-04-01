/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.remotefile;

import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.testing.FakeTicker;
import com.google.copybara.Origin.Reader;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.util.Glob;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RemoteArchiveOriginTest {

  private static final String CAPUTRED_HELLO_WORLD_ZIP_FILE =
      "UEsDBAoAAAAAAGptUVQtOwivDAAAAAwAAAAIABwAdGVzdC50eHRVVAkAA0iXDmJIlw5idXgLAAEE"
          + "Se4JAARTXwEAaGVsbG8gd29ybGQKUEsBAh4DCgAAAAAAam1RVC07CK8MAAAADAAAAAgAGAAAAAAA"
          + "AQAAAKSBAAAAAHRlc3QudHh0VVQFAANIlw5idXgLAAEESe4JAARTXwEAUEsFBgAAAAABAAEATgAA"
          + "AE4AAAAAAA==";

  private static final String CAPTURED_MULTIPLE_DIRS_ZIP_FILE =
      "UEsDBAoAAAAAAAxlV1QAAAAAAAAAAAAAAAAHABwAcGFyZW50L1VUCQADiHEWYqJxFmJ1eAsAAQRJ"
          + "7gkABFNfAQBQSwMECgAAAAAAFmVXVAAAAAAAAAAAAAAAAA8AHABwYXJlbnQvZGlyLXR3by9VVAkA"
          + "A5xxFmKicRZidXgLAAEESe4JAARTXwEAUEsDBAoAAAAAABZlV1SC/3LkDAAAAAwAAAAZABwAcGFy"
          + "ZW50L2Rpci10d28vc2Vjb25kLnR4dFVUCQADnHEWYpxxFmJ1eAsAAQRJ7gkABFNfAQBzZWNvbmQg"
          + "ZmlsZQpQSwMECgAAAAAAEWVXVAAAAAAAAAAAAAAAAA8AHABwYXJlbnQvZGlyLW9uZS9VVAkAA5Fx"
          + "FmKicRZidXgLAAEESe4JAARTXwEAUEsDBAoAAAAAABFlV1TfMNv0CwAAAAsAAAAYABwAcGFyZW50"
          + "L2Rpci1vbmUvZmlyc3QudHh0VVQJAAORcRZikXEWYnV4CwABBEnuCQAEU18BAGZpcnN0IGZpbGUK"
          + "UEsBAh4DCgAAAAAADGVXVAAAAAAAAAAAAAAAAAcAGAAAAAAAAAAQAO1BAAAAAHBhcmVudC9VVAUA"
          + "A4hxFmJ1eAsAAQRJ7gkABFNfAQBQSwECHgMKAAAAAAAWZVdUAAAAAAAAAAAAAAAADwAYAAAAAAAA"
          + "ABAA7UFBAAAAcGFyZW50L2Rpci10d28vVVQFAAOccRZidXgLAAEESe4JAARTXwEAUEsBAh4DCgAA"
          + "AAAAFmVXVIL/cuQMAAAADAAAABkAGAAAAAAAAQAAAKSBigAAAHBhcmVudC9kaXItdHdvL3NlY29u"
          + "ZC50eHRVVAUAA5xxFmJ1eAsAAQRJ7gkABFNfAQBQSwECHgMKAAAAAAARZVdUAAAAAAAAAAAAAAAA"
          + "DwAYAAAAAAAAABAA7UHpAAAAcGFyZW50L2Rpci1vbmUvVVQFAAORcRZidXgLAAEESe4JAARTXwEA"
          + "UEsBAh4DCgAAAAAAEWVXVN8w2/QLAAAACwAAABgAGAAAAAAAAQAAAKSBMgEAAHBhcmVudC9kaXIt"
          + "b25lL2ZpcnN0LnR4dFVUBQADkXEWYnV4CwABBEnuCQAEU18BAFBLBQYAAAAABQAFALQBAACPAQAA"
          + "AAA=";

  private static final String BASE_URL = "https://foo.zip";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock HttpStreamFactory transport;
  private Path workdir;
  private Profiler profiler;
  private FakeTicker ticker;
  private RemoteFileOptions remoteFileOptions;

  @Before
  public void setUp() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    ticker = new FakeTicker().setAutoIncrementStep(1, TimeUnit.MILLISECONDS);
    profiler = new Profiler(ticker);
    remoteFileOptions = new RemoteFileOptions();
  }

  @Test
  public void testZipFileUnpacked() throws Exception {
    when(transport.open(any()))
        .thenReturn(
            new ByteArrayInputStream(BaseEncoding.base64().decode(CAPUTRED_HELLO_WORLD_ZIP_FILE)));
    RemoteArchiveOrigin underTest =
        new RemoteArchiveOrigin(
            "zip",
            Author.parse("Copybara <noreply@copybara.io>"),
            "a message",
            transport,
            profiler,
            remoteFileOptions,
            BASE_URL,
            new NoVersionSelector());
    underTest.resolve("foo");
    Reader<RemoteArchiveRevision> reader =
        underTest.newReader(
            Glob.ALL_FILES,
            new Authoring(
                new Author("foo", "default@example.com"),
                AuthoringMappingMode.PASS_THRU,
                ImmutableSet.of()));
    RemoteArchiveRevision revision = underTest.resolve("https://foo.zip");
    reader.checkout(revision, workdir);
    assertThatPath(workdir).containsFile("test.txt", "hello world\n");
  }

  @Test
  public void testZipFileEmptyGlob() throws Exception {
    when(transport.open(any()))
        .thenReturn(
            new ByteArrayInputStream(BaseEncoding.base64().decode(CAPUTRED_HELLO_WORLD_ZIP_FILE)));
    RemoteArchiveOrigin underTest =
        new RemoteArchiveOrigin(
            "zip",
            Author.parse("Copybara <noreply@copybara.io>"),
            "a message",
            transport,
            profiler,
            remoteFileOptions,
            BASE_URL,
            new NoVersionSelector());
    underTest.resolve("foo");
    Reader<RemoteArchiveRevision> reader =
        underTest.newReader(
            Glob.createGlob(ImmutableList.of("no match"), ImmutableList.of("**")),
            new Authoring(
                new Author("foo", "default@example.com"),
                AuthoringMappingMode.PASS_THRU,
                ImmutableSet.of()));
    RemoteArchiveRevision revision = underTest.resolve("https://foo.zip");
    reader.checkout(revision, workdir);
    assertThatPath(workdir).containsNoFiles("test.txt", "hello world\n");
  }

  @Test
  public void testZipFileMultipleDirs() throws Exception {
    when(transport.open(any()))
        .thenReturn(
            new ByteArrayInputStream(
                BaseEncoding.base64().decode(CAPTURED_MULTIPLE_DIRS_ZIP_FILE)));
    RemoteArchiveOrigin underTest =
        new RemoteArchiveOrigin(
            "zip",
            Author.parse("Copybara <noreply@copybara.io>"),
            "a message",
            transport,
            profiler,
            remoteFileOptions,
            BASE_URL,
            new NoVersionSelector());
    Reader<RemoteArchiveRevision> reader =
        underTest.newReader(
            Glob.ALL_FILES,
            new Authoring(
                new Author("foo", "default@example.com"),
                AuthoringMappingMode.PASS_THRU,
                ImmutableSet.of()));
    RemoteArchiveRevision revision = underTest.resolve("https://dirs.zip");
    reader.checkout(revision, workdir);
    assertThatPath(workdir).containsFile("parent/dir-one/first.txt", "first file\n");
    assertThatPath(workdir).containsFile("parent/dir-two/second.txt", "second file\n");
  }
}
