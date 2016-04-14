// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.config.ConfigValidationException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TemplateTokensTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private void assertParse(String raw, String toStringValue) {
    assertThat(TemplateTokens.parse(raw).toString())
        .isEqualTo(toStringValue);
  }

  @Test
  public void empty() {
    assertParse("", "[]");
  }

  @Test
  public void simpleLiteral() {
    assertParse("asdf", "[{asdf, LITERAL}]");
  }

  @Test
  public void interpolationOnly() {
    assertParse("${asdf}", "[{asdf, INTERPOLATION}]");
  }

  @Test
  public void interpolationsAndLiterals() {
    assertParse("${foo}bar${baz}iru",
        "[{foo, INTERPOLATION}, {bar, LITERAL}, {baz, INTERPOLATION}, {iru, LITERAL}]");
  }

  @Test
  public void consecutiveInterpolations() {
    assertParse("${foo}${bar}${baz}",
        "[{foo, INTERPOLATION}, {bar, INTERPOLATION}, {baz, INTERPOLATION}]");
  }

  @Test
  public void literalDollarSigns() {
    assertParse("a$$b$$c$$d${foo}bar$$",
        "[{a$b$c$d, LITERAL}, {foo, INTERPOLATION}, {bar$, LITERAL}]");
  }

  @Test
  public void emptyInterpolatedName() {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Expect non-empty interpolated value name");
    TemplateTokens.parse("foo${}bar");
  }

  @Test
  public void unterminatedInterpolation() {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Unterminated '${'");
    TemplateTokens.parse("foo${bar");
  }

  @Test
  public void badCharacterFollowingDollar() {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Expect $ or { after every $");
    TemplateTokens.parse("foo$bar");
  }
}
