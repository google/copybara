package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitAuthorTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void testAuthorNameEmail() throws Exception {
    GitAuthor author = new GitAuthor("Foo Bar <foo@bar.com>");

    assertThat(author.getId()).isEqualTo("Foo Bar <foo@bar.com>");
    assertThat(author.getName()).isEqualTo("Foo Bar");
    assertThat(author.getEmail()).isEqualTo("foo@bar.com");
  }

  @Test
  public void testWrongFormat() throws Exception {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Invalid author 'foo-bar'. Must be in the form of 'Name <email@domain>'");

    new GitAuthor("foo-bar");
  }
}
