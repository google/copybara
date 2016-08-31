package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LabelFinderTest {

  @Test
  public void testGoodLabels() {
    checkIsLabel("foo:bar baz", "foo", "bar baz");
    checkIsLabel("foo: bar baz", "foo", "bar baz");
    checkIsLabel("foo= bar baz", "foo", "bar baz");
    checkIsLabel("foo=bar baz", "foo", "bar baz");
    // We only trim spaces between =|: and the value.
    checkIsLabel("foo=bar baz   ", "foo", "bar baz   ");
    checkIsLabel("foo:http://bar", "foo", "http://bar");
  }

  @Test
  public void testNoLabels() {
    assertThat(new LabelFinder("").isLabel()).isFalse();
    assertThat(new LabelFinder("foo").isLabel()).isFalse();
    assertThat(new LabelFinder("foo://").isLabel()).isFalse();
    assertThat(new LabelFinder("foo://aaa").isLabel()).isFalse();
    assertThat(new LabelFinder("foo bar baz=baz").isLabel()).isFalse();
  }

  private void checkIsLabel(String string, String labelName, String labelValue) {
    LabelFinder finder = new LabelFinder(string);
    assertThat(finder.isLabel()).isTrue();
    assertThat(finder.getName()).isEqualTo(labelName);
    assertThat(finder.getValue()).isEqualTo(labelValue);
  }
}
