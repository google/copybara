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

import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.copybara.Origin.Reader;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RemoteFileModuleTest {

  private static final String CAPUTRED_HELLO_WORLD_ZIP_FILE =
      "UEsDBAoAAAAAAGptUVQtOwivDAAAAAwAAAAIABwAdGVzdC50eHRVVAkAA0iXDmJIlw5idXgLAAEE"
          + "Se4JAARTXwEAaGVsbG8gd29ybGQKUEsBAh4DCgAAAAAAam1RVC07CK8MAAAADAAAAAgAGAAAAAAA"
          + "AQAAAKSBAAAAAHRlc3QudHh0VVQFAANIlw5idXgLAAEESe4JAARTXwEAUEsFBgAAAAABAAEATgAA"
          + "AE4AAAAAAA==";

  private OptionsBuilder options;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  private Path workdir;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock HttpStreamFactory transport;

  @Before
  public void setUp() throws Exception {
    console = new TestingConsole();
    workdir = Files.createTempDirectory("workdir");
    RemoteFileOptions options = new RemoteFileOptions();
    options.transport = () -> transport;
    OptionsBuilder optionsBuilder = new OptionsBuilder().setConsole(console);
    optionsBuilder.remoteFile = options;
    skylark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testRemoteArchiveOriginZipFile() throws Exception {
    when(transport.open(any()))
        .thenReturn(
            new ByteArrayInputStream(BaseEncoding.base64().decode(CAPUTRED_HELLO_WORLD_ZIP_FILE)));
    RemoteArchiveOrigin underTest =
        skylark.eval("o", "o = remotefiles.origin(unpack_method = 'zip', message = 'hello world')");
    Reader<RemoteArchiveRevision> reader =
        underTest.newReader(
            Glob.ALL_FILES,
            new Authoring(
                new Author("foo", "default@example.com"),
                AuthoringMappingMode.PASS_THRU,
                ImmutableSet.of()));
    RemoteArchiveRevision revision = underTest.resolve("https://dirs.zip");
    reader.checkout(revision, workdir);
    assertThatPath(workdir).containsFile("test.txt", "hello world\n");
  }
}
