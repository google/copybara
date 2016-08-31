package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.testing.TransformWorks;
import java.nio.file.FileSystems;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TransformWorkTest {

  @Test
  public void testAddLabel() {
    checkAddLabel("foo", "foo\n\nTEST=VALUE\n");
  }

  @Test
  public void testAddLabelToGroup() {
    checkAddLabel("foo\n\nA=B\n\n", "foo\n\nA=B\nTEST=VALUE\n\n");
  }

  @Test
  public void testAddLabelNoEmptyLineBeforeGroup() {
    checkAddLabel("foo\nA=B\n\n", "foo\nA=B\n\nTEST=VALUE\n");
  }

  @Test
  public void testAddLabelNoGroupNoEndLine() {
    checkAddLabel("foo\nA=B", "foo\nA=B\n\nTEST=VALUE\n");
  }

  @Test
  public void testReplaceLabel() {
    TransformWork work = create("Foo\n\nSOME=TEST\n");
    work.replaceLabel("SOME", "REPLACED");
    assertThat(work.getMessage()).isEqualTo("Foo\n\nSOME=REPLACED\n");
  }

  @Test
  public void testReplaceNonExistentLabel() {
    TransformWork work = create("Foo\n\nFOO=TEST\n");
    work.replaceLabel("SOME", "REPLACED");
    assertThat(work.getMessage()).isEqualTo("Foo\n\nFOO=TEST\n");
  }

  @Test
  public void testsDeleteLabel() {
    TransformWork work = create("Foo\n\nSOME=TEST\n");
    work.removeLabel("SOME");
    assertThat(work.getMessage()).isEqualTo("Foo\n\n");
  }

  @Test
  public void testsDeleteNonExistentLabel() {
    TransformWork work = create("Foo\n\nSOME=TEST\n");
    work.removeLabel("FOO");
    assertThat(work.getMessage()).isEqualTo("Foo\n\nSOME=TEST\n");
  }

  @Test
  public void testGetLabel() {
    TransformWork work = create("Foo\n\nSOME=TEST\n");
    assertThat(work.getLabel("SOME")).isEqualTo("TEST");
    assertThat(work.getLabel("FOO")).isEqualTo(null);
  }

  @Test
  public void testReversable() {
    TransformWork work = create("Foo\n\nSOME=TEST\nOTHER=FOO\n");
    work.addLabel("EXAMPLE", "VALUE");
    work.replaceLabel("EXAMPLE", "OTHER VALUE");
    assertThat(work.getMessage()).isEqualTo("Foo\n\nSOME=TEST\nOTHER=FOO\nEXAMPLE=OTHER VALUE\n");
    work.removeLabel("EXAMPLE");
    assertThat(work.getMessage()).isEqualTo("Foo\n\nSOME=TEST\nOTHER=FOO\n");
  }


  private void checkAddLabel(String originalMsg, String expected) {
    TransformWork work = create(originalMsg);
    work.addLabel("TEST", "VALUE");
    assertThat(work.getMessage()).isEqualTo(expected);
  }

  private TransformWork create(String msg) {
    return TransformWorks.of(FileSystems.getDefault().getPath("/"), msg);
  }
}
