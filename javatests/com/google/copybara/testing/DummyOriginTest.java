package com.google.copybara.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.copybara.Change;
import com.google.copybara.RepoException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DummyOriginTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testResolveNullReturnsHead() throws Exception {
    DummyOrigin origin = new DummyOrigin()
        .addSimpleChange(/*timestamp*/ 4242);

    assertThat(origin.resolve(null).readTimestamp())
        .isEqualTo((long) 4242);

    origin.addSimpleChange(/*timestamp*/ 42424242);
    assertThat(origin.resolve(null).readTimestamp())
        .isEqualTo((long) 42424242);
  }

  @Test
  public void testCanSpecifyMessage() throws Exception {
    DummyOrigin origin = new DummyOrigin()
        .addSimpleChange(/*timestamp*/ 4242, "foo msg");

    ImmutableList<Change<DummyReference>> changes =
        origin.changes(/*fromRef*/ null, /*toRef*/ origin.resolve("0"));
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).getMessage()).isEqualTo("foo msg");
  }

  @Test
  public void exceptionWhenParsingNonNumericReference() throws Exception {
    DummyOrigin origin = new DummyOrigin();

    thrown.expect(RepoException.class);
    thrown.expectMessage("Not a well-formatted reference");
    origin.resolve("foo");
  }

  @Test
  public void exceptionWhenParsingOutOfRangeReference() throws Exception {
    DummyOrigin origin = new DummyOrigin()
        .addSimpleChange(/*timestamp*/ 9)
        .addSimpleChange(/*timestamp*/ 98);

    thrown.expect(RepoException.class);
    thrown.expectMessage("Cannot find any change for 42. Only 2 changes exist");
    origin.resolve("42");
  }

  @Test
  public void canSetAuthorOfIndividualChanges() throws Exception {
    DummyOrigin origin = new DummyOrigin()
        .setOriginalAuthor(new DummyOriginalAuthor("Dummy Origin", "dummy_origin@google.com"))
        .addSimpleChange(/*timestamp*/ 42)
        .setOriginalAuthor(new DummyOriginalAuthor("Wise Origin", "wise_origin@google.com"))
        .addSimpleChange(/*timestamp*/ 999);

    ImmutableList<Change<DummyReference>> changes =
        origin.changes(/*fromRef*/ null, /*toRef*/ origin.resolve("1"));

    assertThat(changes).hasSize(2);
    assertThat(changes.get(0).getOriginalAuthor())
        .isEqualTo(new DummyOriginalAuthor("Dummy Origin", "dummy_origin@google.com"));
    assertThat(changes.get(1).getOriginalAuthor())
        .isEqualTo(new DummyOriginalAuthor("Wise Origin", "wise_origin@google.com"));
  }
}
