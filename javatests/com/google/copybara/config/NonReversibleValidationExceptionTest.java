package com.google.copybara.config;

import com.google.copybara.transform.Replace;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NonReversibleValidationExceptionTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void canIncludeMessage() throws NonReversibleValidationException {
    thrown.expect(NonReversibleValidationException.class);
    thrown.expectMessage("fuizzubazzu");
    throw new NonReversibleValidationException(new Replace.Yaml(), "fuizzubazzu");
  }
}
