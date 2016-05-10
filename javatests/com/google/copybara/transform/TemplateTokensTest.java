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

  private void assertParse(String raw, String templateValue, String toStringValue)
      throws ConfigValidationException {
    TemplateTokens tokens = TemplateTokens.parse(raw);
    assertThat(tokens.toString()).isEqualTo(toStringValue);
    assertThat(tokens.template()).isEqualTo(templateValue);
  }

  @Test
  public void empty() throws ConfigValidationException {
    assertParse("", "", "[]");
  }

  @Test
  public void simpleLiteral() throws ConfigValidationException {
    assertParse("asdf", "asdf", "[{asdf, LITERAL}]");
  }

  @Test
  public void interpolationOnly() throws ConfigValidationException {
    assertParse("${asdf}", "${asdf}", "[{asdf, INTERPOLATION}]");
  }

  @Test
  public void interpolationsAndLiterals() throws ConfigValidationException {
    assertParse(
        "${foo}bar${baz}iru",
        "${foo}bar${baz}iru",
        "[{foo, INTERPOLATION}, {bar, LITERAL}, {baz, INTERPOLATION}, {iru, LITERAL}]");
  }

  @Test
  public void consecutiveInterpolations() throws ConfigValidationException {
    assertParse(
        "${foo}${bar}${baz}",
        "${foo}${bar}${baz}",
        "[{foo, INTERPOLATION}, {bar, INTERPOLATION}, {baz, INTERPOLATION}]");
  }

  @Test
  public void literalDollarSigns() throws ConfigValidationException {
    assertParse(
        "a$$b$$c$$d${foo}bar$$",
        "a\\$b\\$c\\$d${foo}bar\\$",
        "[{a$b$c$d, LITERAL}, {foo, INTERPOLATION}, {bar$, LITERAL}]");
  }

  @Test
  public void emptyInterpolatedName() throws ConfigValidationException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Expect non-empty interpolated value name");
    TemplateTokens.parse("foo${}bar");
  }

  @Test
  public void unterminatedInterpolation() throws ConfigValidationException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Unterminated '${'");
    TemplateTokens.parse("foo${bar");
  }

  @Test
  public void badCharacterFollowingDollar() throws ConfigValidationException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("Expect $ or { after every $");
    TemplateTokens.parse("foo$bar");
  }
}
