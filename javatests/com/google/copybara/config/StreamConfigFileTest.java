/*
 * Copyright (C) 2026 Google LLC
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

package com.google.copybara.config;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.jimfs.Jimfs;
import java.io.ByteArrayInputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StreamConfigFileTest {

  private FileSystem fs;

  @Before
  public void setUp() {
    fs = Jimfs.newFileSystem();
  }

  @Test
  public void testReadContentAndCaching() throws Exception {
    Path fallbackPath = Files.writeString(fs.getPath("/copy.bara.sky"), "");
    ConfigFile delegate =
        new PathBasedConfigFile(fallbackPath, /* rootPath= */ null, /* identifierPrefix= */ null);
    AtomicInteger calls = new AtomicInteger(0);
    StreamConfigFile streamConfigFile =
        new StreamConfigFile(
            () -> {
              calls.incrementAndGet();
              return new ByteArrayInputStream("content from stream".getBytes(UTF_8));
            },
            delegate,
            "stdin");

    assertThat(streamConfigFile.path()).isEqualTo("<stdin>/copy.bara.sky");
    assertThat(streamConfigFile.getIdentifier()).isEqualTo("<stdin>/copy.bara.sky");
    assertThat(streamConfigFile.readContent()).isEqualTo("content from stream");
    assertThat(streamConfigFile.readContent()).isEqualTo("content from stream");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  public void testCustomStreamName() throws Exception {
    Path fallbackPath = Files.writeString(fs.getPath("/copy.bara.sky"), "");
    ConfigFile delegate =
        new PathBasedConfigFile(fallbackPath, /* rootPath= */ null, /* identifierPrefix= */ null);
    StreamConfigFile streamConfigFile =
        new StreamConfigFile(
            () -> new ByteArrayInputStream(new byte[0]), delegate, "custom_stream");

    assertThat(streamConfigFile.path()).isEqualTo("<custom_stream>/copy.bara.sky");
    assertThat(streamConfigFile.getIdentifier()).isEqualTo("<custom_stream>/copy.bara.sky");
  }

  @Test
  public void testResolveDelegates() throws Exception {
    Path fallbackPath = Files.writeString(fs.getPath("/copy.bara.sky"), "");
    Files.writeString(fs.getPath("/helper.bara.sky"), "helper_content");
    ConfigFile delegate =
        new PathBasedConfigFile(fallbackPath, /* rootPath= */ null, /* identifierPrefix= */ null);
    StreamConfigFile streamConfigFile =
        new StreamConfigFile(() -> new ByteArrayInputStream(new byte[0]), delegate, "stdin");

    assertThat(streamConfigFile.resolve("helper.bara.sky").readContent())
        .isEqualTo("helper_content");
  }
}
