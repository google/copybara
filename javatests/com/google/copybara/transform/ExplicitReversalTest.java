// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ExplicitReversalTest {

  private class MockTransform implements Transformation {

    String name;

    @Override
    public void transform(TransformWork work, Console console) {
      invokedTransforms.add(name);
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

  private void transform(Transformation transformation) throws IOException, ValidationException {
    transformation.transform(
        new TransformWork(Paths.get("/foo"), "test msg"),
        new TestingConsole());
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
