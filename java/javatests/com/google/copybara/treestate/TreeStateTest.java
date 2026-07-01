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

package com.google.copybara.treestate;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TreeStateTest {

  private Path checkoutDir;

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/test-checkoutDir");
    Files.createDirectories(checkoutDir);
  }

  @Test
  public void testNotNotified() {
    TreeState treeState = new TreeState(checkoutDir);
    treeState.maybeClearCache();

    assertThat(treeState.isCached()).isFalse();
  }

  @Test
  public void testNotifiedReturnsCached() throws IOException {
    TreeState treeState = new TreeState(checkoutDir);
    treeState.find(Glob.ALL_FILES.relativeTo(checkoutDir));
    treeState.notifyNoChange();
    treeState.maybeClearCache();

    assertThat(treeState.isCached()).isTrue();
  }

  @Test
  public void testNoUsedButNotified() throws IOException {
    TreeState treeState = new TreeState(checkoutDir);
    // FSTReeState should be used (find() call) to get a cached version.
    treeState.notifyNoChange();
    treeState.maybeClearCache();

    assertThat(treeState.isCached()).isFalse();
  }

  @Test
  public void testBackToFSIfNotNotified() throws IOException {
    TreeState treeState = new TreeState(checkoutDir);
    treeState.find(Glob.ALL_FILES.relativeTo(checkoutDir));
    treeState.notifyNoChange();
    treeState.maybeClearCache();

    assertThat(treeState.isCached()).isTrue();

    treeState.maybeClearCache();

    assertThat(treeState.isCached()).isFalse();
  }

  /**
   * Regression that checks that a cached TreeState doesn't keep the 'notified'
   * bit active between maybeClearCache calls.
   */
  @Test
  public void testCachedNotifiedIsNotSticky() throws IOException {
    TreeState treeState = new TreeState(checkoutDir);
    treeState.find(Glob.ALL_FILES.relativeTo(checkoutDir));
    treeState.notifyNoChange();

    treeState.maybeClearCache();

    assertThat(treeState.isCached()).isTrue();

    treeState.find(Glob.ALL_FILES.relativeTo(checkoutDir));
    treeState.notifyNoChange();

    treeState.maybeClearCache();
    treeState.maybeClearCache();
    assertThat(treeState.isCached()).isFalse();
  }
}
