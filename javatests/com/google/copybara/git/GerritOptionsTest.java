// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GerritOptionsTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private GerritOptions options;
  private JCommander jcommander;

  @Before
  public void setup() {
    options = new GerritOptions();
    jcommander = new JCommander(ImmutableList.of(options));
  }

  @Test
  public void validChangeId() {
    jcommander.parse("--gerrit-change-id=I0123456789deadbeefbc0123456789deadbeefbc");

    assertThat(options.gerritChangeId)
        .isEqualTo("I0123456789deadbeefbc0123456789deadbeefbc");
  }

  @Test
  public void invalidTooShort() {
    thrown.expect(ParameterException.class);
    thrown.expectMessage("'I1111' does not match Gerrit Change ID pattern");
    jcommander.parse("--gerrit-change-id=I1111");
  }

  @Test
  public void invalidMissingIPrefix() {
    thrown.expect(ParameterException.class);
    thrown.expectMessage(
        "'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' does not match Gerrit Change ID pattern");
    jcommander.parse("--gerrit-change-id=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  }

  @Test
  public void invalidUsesCapitalHex() {
    thrown.expect(ParameterException.class);
    thrown.expectMessage(
        "'I0123456789DEADBEEFBC0123456789DEADBEEFBC' does not match Gerrit Change ID pattern");
    jcommander.parse("--gerrit-change-id=I0123456789DEADBEEFBC0123456789DEADBEEFBC");
  }
}
