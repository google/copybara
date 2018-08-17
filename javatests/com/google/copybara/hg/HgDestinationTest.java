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
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Destination.Writer;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.TransformResult;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.RepoException;
import com.google.copybara.hg.HgRepository.HgLogEntry;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HgDestinationTest {

  private HgRepository remoteRepo;
  private HgDestination destination;
  private Glob destinationFiles;
  private Path hgDestPath;
  private Path workdir;
  private TestingConsole console;
  private OptionsBuilder options;
  private String url;
  private String fetch;
  private String push;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder()
        .setHomeDir(Files.createTempDirectory("home").toString());
    destinationFiles = Glob.ALL_FILES;

    options.general.setFileSystemForTest(FileSystems.getDefault());
    workdir = options.general.getDirFactory().newTempDir("workdir");

    console = new TestingConsole();

    hgDestPath = Files.createTempDirectory("HgDestinationTest-hgDestRepo");
    url = "file://" + hgDestPath;
    remoteRepo = new HgRepository(hgDestPath, /*verbose*/ false);
    remoteRepo.init();

    Files.write(hgDestPath.resolve("file.txt"), "first write".getBytes());
    remoteRepo.hg(hgDestPath, "add");
    remoteRepo.hg(hgDestPath, "commit", "-m", "first commit");
  }

  @Test
  public void testWrite() throws Exception {
    fetch = "tip";
    push = "default";
    destination = HgDestination.newHgDestination(url, fetch, push, options.general);

    remoteRepo.archive(workdir.toAbsolutePath().toString());
    Files.write(workdir.resolve("test.txt"), "test".getBytes());

    ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(
        Instant.ofEpochMilli(1496333940000L), ZoneId.of("-04:00"));

    DummyRevision originRef = new DummyRevision("origin_ref")
        .withAuthor(new Author("Copy Bara", "copy@bara.com"))
        .withTimestamp(zonedDateTime);
    TransformResult result = TransformResults.of(workdir, originRef);

    Writer<HgRevision> writer = newWriter();

    ImmutableList<DestinationEffect> destinationResult = writer.write(result, console);
    assertThat(destinationResult).hasSize(1);
    assertThat(destinationResult.get(0).getErrors()).isEmpty();
    assertThat(destinationResult.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(destinationResult.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(destinationResult.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");

    ImmutableList<HgLogEntry> commits = remoteRepo.log().run();
    assertThat(commits).hasSize(2);
    assertThat(commits.get(0).getDescription()).isEqualTo(""
        + "test summary\n"
        + "\n"
        + "DummyOrigin-RevId: origin_ref");
    assertThat(commits).hasSize(2);
    assertThat(commits.get(0).getZonedDate()).isEqualTo(zonedDateTime);
    assertThat(commits.get(0).getFiles()).hasSize(2);
    assertThat(commits.get(0).getFiles().get(0)).isEqualTo(".hg_archival.txt");
    assertThat(commits.get(0).getFiles().get(1)).isEqualTo("test.txt");
    assertThat(commits.get(1).getFiles()).hasSize(1);
    assertThat(commits.get(1).getFiles().get(0)).isEqualTo("file.txt");
  }

  @Test
  public void testEmptyChange() throws Exception {
    fetch = "tip";
    push = "default";
    destination = HgDestination.newHgDestination(url, fetch, push, options.general);
    Writer<HgRevision> writer = newWriter();

    DummyRevision originRef = new DummyRevision("origin_ref");
    TransformResult result = TransformResults.of(workdir, originRef);

    try {
      writer.write(result, console);
      fail("Should have failed");
    } catch (RepoException expected) {
      assertThat(expected.getMessage()).contains("Error executing hg");
      //TODO(jlliu): better exception for commits of empty changes
    }
  }

  private Writer<HgRevision> newWriter() {
    return destination.newWriter(destinationFiles, /*dryRun*/ false, /*groupId*/ null,
        /*oldWriter*/ null);
  }
}
