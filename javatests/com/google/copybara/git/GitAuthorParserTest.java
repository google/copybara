package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.Author;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitAuthorParserTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void testParseComplete() throws Exception {
    checkAuthorFormat("Foo Bar <foo@bar.com>", "Foo Bar", "foo@bar.com");
  }

  @Test
  public void testParseEmptyEmail() throws Exception {
    checkAuthorFormat("Foo Bar <>", "Foo Bar", "");
  }

  @Test
  public void testParseWrongFormat() throws Exception {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Invalid author 'Foo Bar'. Must be in the form of 'Name <email>'");
    GitAuthorParser.parse("Foo Bar");
  }

  @Test
  public void testSerialize() throws Exception {
    assertThat(GitAuthorParser.serialize(new Author("Foo Bar", "foo@bar.com")))
        .isEqualTo("Foo Bar <foo@bar.com>");
  }

  @Test
  public void testSerializeEmptyAuthor() throws Exception {
    expectedException.expect(NullPointerException.class);
    expectedException.expectMessage(
        "Author must have a name in order to generate a valid Git author: "
            + "Author{name=null, email=foo@bar.com}");
    GitAuthorParser.serialize(new Author(/*name*/ null, /*email*/"foo@bar.com"));
  }

  @Test
  public void testSerializeEmptyEmail() throws Exception {
    assertThat(GitAuthorParser.serialize(new Author("Foo Bar", /*email*/null)))
        .isEqualTo("Foo Bar <>");
  }

  private void checkAuthorFormat(String gitAuthor, String expectedName, String expectedEmail) {
    Author author = GitAuthorParser.parse(gitAuthor);
    assertThat(author.getName()).isEqualTo(expectedName);
    assertThat(author.getEmail()).isEqualTo(expectedEmail);
  }
}
