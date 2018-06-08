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

import java.nio.file.Files;
import java.nio.file.Path;
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
}
