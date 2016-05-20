package com.google.copybara.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Change;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DummyOriginTest {
  @Test
  public void testResolveNullReturnsHead() throws Exception {
    DummyOrigin origin = new DummyOrigin();
    origin.addSimpleChange(/*timestamp*/ 4242);
    assertThat(origin.resolve(null).readTimestamp())
        .isEqualTo((long) 4242);

    origin.addSimpleChange(/*timestamp*/ 42424242);
    assertThat(origin.resolve(null).readTimestamp())
        .isEqualTo((long) 42424242);
  }

  @Test
  public void testCanSpecifyMessage() throws Exception {
    DummyOrigin origin = new DummyOrigin();
    origin.addSimpleChange(/*timestamp*/ 4242, "foo msg");
    ImmutableList<Change<DummyOrigin>> changes =
        origin.changes(/*fromRef*/ null, /*toRef*/ origin.resolve("0"));
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).getMessage()).isEqualTo("foo msg");
  }
}
