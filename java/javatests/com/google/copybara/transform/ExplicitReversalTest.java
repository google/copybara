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
import static java.nio.file.Paths.get;

import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ExplicitReversalTest {
  private final TestingConsole console = new TestingConsole();

  private class MockTransform implements Transformation {

    String name;

    @Override
    public TransformationStatus transform(TransformWork work) {
      invokedTransforms.add(name);
      return TransformationStatus.success();
    }

    @Override
    public Transformation reverse() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String describe() {
      return name;
    }
  }

  private List<String> invokedTransforms = new ArrayList<>();
  private MockTransform transform1 = new MockTransform();
  private MockTransform transform2 = new MockTransform();
  private ExplicitReversal explicit = new ExplicitReversal(transform1, transform2);

  @Before
  public void setup() {
    transform1.name = "t1-foo";
    transform2.name = "t2-bar";
  }

  @Test
  public void describeMatchesForwardDescribe() {
    assertThat(explicit.describe()).isEqualTo("t1-foo");
  }

  private void transform(Transformation transformation) throws Exception {
    transformation.transform(
        TransformWorks.of(get("/foo"), "test msg", console)
    );
  }

  @Test
  public void reverseToRunReverseTransform() throws Exception {
    transform(explicit.reverse());
    assertThat(invokedTransforms)
        .containsExactly("t2-bar");
  }

  @Test
  public void delegatesToForward() throws Exception {
    transform(explicit);
    assertThat(invokedTransforms)
        .containsExactly("t1-foo");
  }

  @Test
  public void reverseTwiceForSameOperation() throws Exception {
    transform(explicit.reverse().reverse());
    assertThat(invokedTransforms)
        .containsExactly("t1-foo");
  }
}
