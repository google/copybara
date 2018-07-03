/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.hg;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Origin.Reader;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.hg.HgRepository.HgLogEntry;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.Glob;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HgOriginTest {

  private final Authoring authoring = new Authoring(new Author("Copy",
      "copy@bara.com"),
      AuthoringMappingMode.PASS_THRU, ImmutableSet.of());

  private HgOrigin origin;
  private OptionsBuilder options;
  private SkylarkTestExecutor skylark;
  private Glob originFiles;
  private HgRepository repository;
  private Path remotePath;
  private String url;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder().setOutputRootToTmpDir();
    skylark = new SkylarkTestExecutor(options);
    originFiles = Glob.ALL_FILES;

    remotePath = Files.createTempDirectory("remote");
    url = remotePath.toAbsolutePath().toString();

    origin = skylark.eval("result",
        String.format("result = hg.origin( url = '%s')", url));

    repository = new HgRepository(remotePath);
    repository.init();
  }

  private Reader<HgRevision> newReader() {
    return origin.newReader(originFiles, authoring);
  }

  private HgOrigin origin() throws ValidationException {
    return skylark.eval("result",
        String.format("result = hg.origin(\n"
            + "    url = '%s')", url));
  }

  @Test
  public void testHgOrigin() throws Exception {
    origin = skylark.eval("result",
        "result = hg.origin(\n"
            + "url = 'https://my-server.org/copybara'"
            + ")");

    assertThat(origin.getLabelName())
        .isEqualTo("HgOrigin{"
          + "url = https://my-server.org/copybara"
          + "}");
  }

  @Test
  public void testEmptyUrl() throws Exception {
    skylark.evalFails("hg.origin(url = '')", "Invalid empty field 'url'");
  }

  @Test
  public void testResolveNullReference() throws Exception {
    String ref = null;
    try {
      origin.resolve(ref);
      fail("Exception should have been thrown");
    }
    catch (CannotResolveRevisionException expected) {
      assertThat(expected.getMessage()).isEqualTo("Cannot resolve null or empty reference");
    }

    try {
      origin.resolve("");
      fail("Exception should have been throw");
    }
    catch (CannotResolveRevisionException expected) {
      assertThat(expected.getMessage()).isEqualTo("Cannot resolve null or empty reference");
    }
  }

  @Test
  public void testCheckout() throws Exception {
    Reader<HgRevision> reader = newReader();

    Path workDir = Files.createTempDirectory("workDir");

    Files.write(remotePath.resolve("foo.txt"), "hello".getBytes(UTF_8));
    Files.write(remotePath.resolve("bar.txt"), "hello".getBytes(UTF_8));

    repository.hg(remotePath, "add", "foo.txt");
    repository.hg(remotePath, "add", "bar.txt");
    repository.hg(remotePath, "commit", "-m", "foo");

    Files.write(remotePath.resolve("foo.txt"), "goodbye".getBytes(UTF_8));
    Files.write(remotePath.resolve("bar.txt"), "other".getBytes(UTF_8));
    repository.hg(remotePath, "add", "foo.txt");
    repository.hg(remotePath, "commit", "-m", "bye");

    repository.hg(remotePath, "rm", "foo.txt");
    repository.hg(remotePath, "commit", "-m", "rm foo");

    ImmutableList<HgLogEntry> commits = repository.log().run();

    reader.checkout(origin.resolve(commits.get(2).getGlobalId()), workDir);

    assertThatPath(workDir)
        .containsFile("foo.txt", "hello")
        .containsFile("bar.txt", "hello")
        .containsFiles(".hg_archival.txt")
        .containsNoMoreFiles();

    reader.checkout(origin.resolve(commits.get(1).getGlobalId()), workDir);

    assertThatPath(workDir)
        .containsFile("foo.txt", "goodbye")
        .containsFile("bar.txt", "other")
        .containsFiles(".hg_archival.txt")
        .containsNoMoreFiles();

    reader.checkout(origin.resolve(commits.get(0).getGlobalId()), workDir);

    assertThatPath(workDir)
        .containsFile("bar.txt", "other")
        .containsFiles(".hg_archival.txt")
        .containsNoMoreFiles();
  }
}
