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

package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.treestate.TreeStateUtil.isCachedTreeState;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.BooleanSubject;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SequenceTest {

  private TestingConsole console;
  private Path checkoutDir;

  private static class MockTransform implements Transformation {

    boolean useTreeState = false;

    @Override
    public void transform(TransformWork work) throws IOException {
      if (useTreeState) {
        work.getTreeState().find(Glob.ALL_FILES.relativeTo(work.getCheckoutDir()));
        work.getTreeState().notifyNoChange();
      }
    }

    @Override
    public Transformation reverse() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String describe() {
      return "mock";
    }
  }

  private final MockTransform t1 = new MockTransform();
  private final MockTransform t2 = new MockTransform();
  private Sequence sequence;

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/test-checkoutDir");
    Files.createDirectories(checkoutDir);
    OptionsBuilder options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
    sequence = new Sequence(options.general.profiler(), /*joinTransformations*/true,
                            ImmutableList.of(t1, t2));
  }

  /**
   * The TreeState passed to a Sequence shouldn't return a cached TreeState after invocation and
   * calling updateTreeState(). Since it could contain many non-cache-safe transforms and the
   * last one could be a safe one. But we shouldn't be able to reuse that cache.
   */
  @Test
  public void testSequenceTreeStateIsNotCached_allGood() throws IOException, ValidationException {
    t1.useTreeState = true;
    t2.useTreeState = true;
    TransformWork work = cachedTreeStateTranformWork();
    sequence.transform(work);
    assertCachedTreeState(work.withUpdatedTreeState()).isFalse();
  }

  private static BooleanSubject assertCachedTreeState(TransformWork work) {
    return assertThat(isCachedTreeState(work.getTreeState()));
  }

  @Test
  public void testSequenceTreeStateIsNotCached_firstBad() throws IOException, ValidationException {
    t1.useTreeState = false;
    t2.useTreeState = true;
    TransformWork work = cachedTreeStateTranformWork();
    sequence.transform(work);
    assertCachedTreeState(work.withUpdatedTreeState()).isFalse();
  }

  private TransformWork cachedTreeStateTranformWork() throws IOException {
    TransformWork work = TransformWorks.of(checkoutDir, "foo", console);
    // Force a map based tree-state
    work.getTreeState().find(Glob.ALL_FILES.relativeTo(checkoutDir));
    work.getTreeState().notifyNoChange();
    work = work.withUpdatedTreeState();
    assertCachedTreeState(work).isTrue();
    return work;
  }
}
