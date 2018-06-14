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

import com.google.common.base.Splitter;
import com.google.copybara.util.CommandOutput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HgRepositoryTest {

  private Path workDir;
  private HgRepository repository;

  @Before
  public void setup() throws Exception {
    workDir = Files.createTempDirectory("workdir");
    repository = new HgRepository(workDir);
  }

  @Test
  public void testInit() throws Exception {
    repository.init();
    Path newFile = Files.createTempFile(workDir, "foo", ".txt");
    String fileName = newFile.toString();
    repository.hg(workDir, "add", fileName);
    repository.hg(workDir, "commit", "-m", "bar");
  }

  @Test
  public void testPullSingleRef() throws Exception {
    repository.init();
    Path newFile = Files.createTempFile(workDir, "foo", ".txt");
    String fileName = newFile.toString();
    repository.hg(workDir, "add", fileName);
    repository.hg(workDir, "commit", "-m", "bar");

    CommandOutput commandOutput = repository.hg(workDir, "log", "--template",
        "{rev}:{node}\n");
    List<String> before = Splitter.on("\n").splitToList(commandOutput.getStdout());
    List<String> revIds = Splitter.on(":").splitToList(before.get(0));

    HgRevision beforeRev = new HgRevision(repository, revIds.get(1));

    Path remoteDir = Files.createTempDirectory("remotedir");
    HgRepository remoteRepo = new HgRepository(remoteDir);
    remoteRepo.init();
    Path newFile2 = Files.createTempFile(remoteDir, "bar", ".txt");
    String fileName2 = newFile2.toString();
    remoteRepo.hg(remoteDir, "add", fileName2);
    remoteRepo.hg(remoteDir, "commit", "-m", "foo");

    CommandOutput remoteCommandOutput = remoteRepo.hg(remoteDir, "log", "--template",
        "{rev}:{node}\n");
    List<String> remoteBefore = Splitter.on("\n").splitToList(remoteCommandOutput.getStdout());
    List<String> remoteRevIds = Splitter.on(":").splitToList(remoteBefore.get(0));

    HgRevision remoteRev = new HgRevision(remoteRepo, remoteRevIds.get(1));

    repository.pull(remoteDir.toString());

    CommandOutput newCommandOutput = repository.hg(workDir, "log", "--template",
        "{rev}:{node}\n");
    List<String> afterRev = Splitter.on("\n").splitToList(newCommandOutput.getStdout().trim());
    assertThat(afterRev).hasSize(2);

    List<String> globalIds = new ArrayList<>();
    for (String rev : afterRev) {
      List<String> afterRevIds = Splitter.on(":").splitToList(rev);
      globalIds.add(afterRevIds.get(1));
    }

    assertThat(globalIds).hasSize(2);
    assertThat(globalIds.get(1)).isEqualTo(beforeRev.getGlobalId());
    assertThat(globalIds.get(0)).isEqualTo(remoteRev.getGlobalId());
  }

}
