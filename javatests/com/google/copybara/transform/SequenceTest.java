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
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.exception.VoidOperationException;
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
  OptionsBuilder options;

  private static class MockTransform implements Transformation {

    String name;
    boolean useTreeState = false;
    Boolean expectCacheHit;
    boolean wasRun = false;
    boolean isNoop = false;

    MockTransform(String name) {
      this.name = name;
    }

    @Override
    public TransformationStatus transform(TransformWork work) throws IOException {
      if (useTreeState) {
        if (expectCacheHit != null) {
          assertWithMessage(name + "'s cache usage was incorrect")
              .that(work.getTreeState().isCached())
              .isEqualTo(expectCacheHit);
        }
        work.getTreeState().find(Glob.ALL_FILES.relativeTo(work.getCheckoutDir()));
        work.getTreeState().notifyNoChange();
      }
      this.wasRun = true;
      return this.isNoop ? TransformationStatus.noop("") : TransformationStatus.success();
    }

    public MockTransform setUseTreeState(boolean b) {
      this.useTreeState = b;
      return this;
    }

    /**
     * If set, the test will fail unless the cache is hit/miss in accordance with the value.
     */
    public MockTransform setExpectCacheHit(boolean useCache) {
      this.expectCacheHit = useCache;
      return this;
    }

    /** If true, this Transformation will return {@code TransformationStatus.noop()} when run. */
    public MockTransform setNoop(boolean isNoop) {
      this.isNoop = isNoop;
      return this;
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

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    checkoutDir = fs.getPath("/test-checkoutDir");
    Files.createDirectories(checkoutDir);
    options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
  }

  /**
   * A Sequence should automatically validate the cache in between each child Transformation that it
   * runs.
   */

  @Test
  public void testSequence_treeStateBeginsUncached() throws Exception {
    TransformWork work = uncachedTreeStateTransformWork();

    // No cache for the first transform to use...
    Transformation t1 = new MockTransform("t1").setUseTreeState(true).setExpectCacheHit(false);
    // ...but it should have created one for the second transform
    Transformation t2 = new MockTransform("t2").setUseTreeState(true).setExpectCacheHit(true);

    Transformation t = sequence(t1, t2);
    t.transform(work);
  }

  @Test
  public void testSequence_treeStateMultipleCacheHits() throws Exception {
    TransformWork work = cachedTreeStateTransformWork();

    // The first transform uses the pre-existing cache...
    Transformation t1 = new MockTransform("t1").setUseTreeState(true).setExpectCacheHit(true);
    // ...and the second transform does as well
    Transformation t2 = new MockTransform("t2").setUseTreeState(true).setExpectCacheHit(true);

    Transformation t = sequence(t1, t2);
    t.transform(work);
  }

  @Test
  public void testSequence_treeStateCacheInvalidation() throws Exception {
    TransformWork work = cachedTreeStateTransformWork();

    // The first transform does not use/notify the TreeState...
    Transformation t1 = new MockTransform("t1").setUseTreeState(false);
    // ...so the second transform should not have a cache available
    Transformation t2 = new MockTransform("t2").setUseTreeState(true).setExpectCacheHit(false);

    Transformation t = sequence(t1, t2);
    t.transform(work);
  }

  @Test
  public void testSequence_nestedSequences_allCachesHit() throws Exception {
    TransformWork work = cachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1").setUseTreeState(true).setExpectCacheHit(true);
    MockTransform t2 = new MockTransform("t2").setUseTreeState(true).setExpectCacheHit(true);
    MockTransform t3 = new MockTransform("t3").setUseTreeState(true).setExpectCacheHit(true);
    MockTransform t4 = new MockTransform("t4").setUseTreeState(true).setExpectCacheHit(true);

    Transformation t = sequence(sequence(t1, t2), sequence(t3, t4));
    t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(t2.wasRun).isTrue();
    assertThat(t3.wasRun).isTrue();
    assertThat(t4.wasRun).isTrue();
  }

  @Test
  public void testSequence_nestedSequences_missOnFirstCache() throws Exception {
    TransformWork work = uncachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1").setUseTreeState(true).setExpectCacheHit(false);
    MockTransform t2 = new MockTransform("t2").setUseTreeState(true).setExpectCacheHit(true);
    MockTransform t3 = new MockTransform("t3").setUseTreeState(true).setExpectCacheHit(true);
    MockTransform t4 = new MockTransform("t4").setUseTreeState(true).setExpectCacheHit(true);

    Transformation t = sequence(sequence(t1, t2), sequence(t3, t4));
    t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(t2.wasRun).isTrue();
    assertThat(t3.wasRun).isTrue();
    assertThat(t4.wasRun).isTrue();
  }

  @Test
  public void testSequence_nestedSequences_missOnSecondCache() throws Exception {
    TransformWork work = cachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1").setUseTreeState(false);
    MockTransform t2 = new MockTransform("t2").setUseTreeState(true).setExpectCacheHit(false);
    MockTransform t3 = new MockTransform("t3").setUseTreeState(true).setExpectCacheHit(true);
    MockTransform t4 = new MockTransform("t4").setUseTreeState(true).setExpectCacheHit(true);

    Transformation t = sequence(sequence(t1, t2), sequence(t3, t4));
    t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(t2.wasRun).isTrue();
    assertThat(t3.wasRun).isTrue();
    assertThat(t4.wasRun).isTrue();
  }

  @Test
  public void testSequence_nestedSequences_missOnThirdCache() throws Exception {
    TransformWork work = cachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1").setUseTreeState(true).setExpectCacheHit(true);
    MockTransform t2 = new MockTransform("t2").setUseTreeState(false);
    MockTransform t3 = new MockTransform("t3").setUseTreeState(true).setExpectCacheHit(false);
    MockTransform t4 = new MockTransform("t4").setUseTreeState(true).setExpectCacheHit(true);

    Transformation t = sequence(sequence(t1, t2), sequence(t3, t4));
    t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(t2.wasRun).isTrue();
    assertThat(t3.wasRun).isTrue();
    assertThat(t4.wasRun).isTrue();
  }

  @Test
  public void testSequence_nestedSequences_missOnFourthCache() throws Exception {
    TransformWork work = cachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1").setUseTreeState(true).setExpectCacheHit(true);
    MockTransform t2 = new MockTransform("t2").setUseTreeState(true).setExpectCacheHit(true);
    MockTransform t3 = new MockTransform("t3").setUseTreeState(false);
    MockTransform t4 = new MockTransform("t4").setUseTreeState(true).setExpectCacheHit(false);

    Transformation t = sequence(sequence(t1, t2), sequence(t3, t4));
    t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(t2.wasRun).isTrue();
    assertThat(t3.wasRun).isTrue();
    assertThat(t4.wasRun).isTrue();
  }

  @Test
  public void testSequence_noopIfAnyNoop_noChildNoops() throws Exception {
    TransformWork work = uncachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1");
    MockTransform t2 = new MockTransform("t2");

    Transformation t = sequence(Sequence.NoopBehavior.NOOP_IF_ANY_NOOP, t1, t2);
    TransformationStatus status = t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(t2.wasRun).isTrue();
    assertThat(status.isSuccess()).isTrue();
  }

  @Test
  public void testSequence_noopIfAnyNoop_someChildNoops() throws Exception {
    TransformWork work = uncachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1");
    MockTransform t2 = new MockTransform("t2").setNoop(true);
    MockTransform t3 = new MockTransform("t3");

    Transformation t = sequence(Sequence.NoopBehavior.NOOP_IF_ANY_NOOP, t1, t2, t3);
    TransformationStatus status = t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(t2.wasRun).isTrue();
    assertThat(t3.wasRun).isFalse();
    assertThat(status.isNoop()).isTrue();
  }

  @Test
  public void testSequence_ignoreNoop_someChildNoops() throws Exception {
    TransformWork work = uncachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1");
    MockTransform t2 = new MockTransform("t2").setNoop(true);
    MockTransform t3 = new MockTransform("t3");

    Transformation t = sequence(Sequence.NoopBehavior.IGNORE_NOOP, t1, t2, t3);
    TransformationStatus status = t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(t2.wasRun).isTrue();
    assertThat(t3.wasRun).isTrue();
    assertThat(status.isSuccess()).isTrue();
  }

  @Test
  public void testSequence_noopIfAnyNoop_insideIgnoreNoop() throws Exception {
    TransformWork work = uncachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1").setNoop(true);

    Transformation t =
        sequence(
            Sequence.NoopBehavior.IGNORE_NOOP,
            sequence(Sequence.NoopBehavior.NOOP_IF_ANY_NOOP, t1));
    TransformationStatus status = t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(status.isSuccess()).isTrue();
  }

  @Test
  public void testSequence_failIfAnyNoop_causesFailure() throws Exception {
    TransformWork work = uncachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1").setNoop(true);

    Transformation t = sequence(Sequence.NoopBehavior.FAIL_IF_ANY_NOOP, t1);
    assertThrows(VoidOperationException.class, () -> t.transform(work));

    assertThat(t1.wasRun).isTrue();
  }

  @Test
  public void testSequence_failIfAnyNoop_insideIgnoreNoop() throws Exception {
    TransformWork work = uncachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1").setNoop(true);

    Transformation t =
        sequence(
            Sequence.NoopBehavior.IGNORE_NOOP,
            sequence(Sequence.NoopBehavior.FAIL_IF_ANY_NOOP, t1));
    assertThrows(VoidOperationException.class, () -> t.transform(work));

    assertThat(t1.wasRun).isTrue();
  }

  @Test
  public void testSequence_ignoreNoop_insideFailIfAnyNoop() throws Exception {
    TransformWork work = uncachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1").setNoop(true);

    Transformation t =
        sequence(
            Sequence.NoopBehavior.FAIL_IF_ANY_NOOP,
            sequence(Sequence.NoopBehavior.IGNORE_NOOP, t1));
    TransformationStatus status = t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(status.isSuccess()).isTrue();
  }

  @Test
  public void testSequence_noopIfAllNoop_someChildNoops() throws Exception {
    TransformWork work = uncachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1");
    MockTransform t2 = new MockTransform("t2").setNoop(true);
    MockTransform t3 = new MockTransform("t3");

    Transformation t = sequence(Sequence.NoopBehavior.NOOP_IF_ALL_NOOP, t1, t2, t3);
    TransformationStatus status = t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(t2.wasRun).isTrue();
    assertThat(t3.wasRun).isTrue();
    assertThat(status.isSuccess()).isTrue();
  }

  @Test
  public void testSequence_noopIfAllNoop_allChildNoops() throws Exception {
    TransformWork work = uncachedTreeStateTransformWork();

    MockTransform t1 = new MockTransform("t1").setNoop(true);
    MockTransform t2 = new MockTransform("t2").setNoop(true);
    MockTransform t3 = new MockTransform("t3").setNoop(true);

    Transformation t = sequence(Sequence.NoopBehavior.NOOP_IF_ALL_NOOP, t1, t2, t3);
    TransformationStatus status = t.transform(work);

    assertThat(t1.wasRun).isTrue();
    assertThat(t2.wasRun).isTrue();
    assertThat(t3.wasRun).isTrue();
    assertThat(status.isNoop()).isTrue();
  }

  private TransformWork uncachedTreeStateTransformWork() throws IOException {
    return TransformWorks.of(checkoutDir, "foo", console);
  }

  private TransformWork cachedTreeStateTransformWork() throws IOException {
    TransformWork work = TransformWorks.of(checkoutDir, "foo", console);
    // Force a cached tree-state
    work.getTreeState().find(Glob.ALL_FILES.relativeTo(checkoutDir));
    assertThat(work.getTreeState().isCached()).isTrue();
    return work;
  }

  private Sequence sequence(Transformation... childTransforms) {
    return new Sequence(
        options.general.profiler(),
        options.workflowOptions,
        ImmutableList.copyOf(childTransforms),
        Sequence.NoopBehavior.IGNORE_NOOP);
  }

  private Sequence sequence(Sequence.NoopBehavior noopBehavior, Transformation... childTransforms) {
    return new Sequence(
        options.general.profiler(),
        options.workflowOptions,
        ImmutableList.copyOf(childTransforms),
        noopBehavior);
  }
}
