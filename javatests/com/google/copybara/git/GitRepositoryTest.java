package com.google.copybara.git;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitRepositoryTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private GitRepository repository;

  @Before
  public void setup() throws Exception {
    this.repository = GitRepository.initScratchRepo(/*verbose=*/true, System.getenv());
  }

  @Test
  public void testCheckoutLocalBranch() throws Exception {
    thrown.expect(CannotFindReferenceException.class);
    thrown.expectMessage("Cannot find reference 'foo'");
    repository.simpleCommand("checkout", "foo");
  }
}
