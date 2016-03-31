// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.config;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;

import org.junit.Test;

public final class ReplaceRegexTest {
  @Test
  public void invalidRegex() {
    ReplaceRegex.Yaml yaml = new ReplaceRegex.Yaml();
    yaml.setRegex("(unfinished group");
    yaml.setReplacement("asdf");
    try {
      yaml.withOptions(new Options(new GeneralOptions()));
      fail("should have thrown");
    } catch (ConfigValidationException e) {
      // Expected.
      assertThat(e.getMessage()).contains("not a valid regex");
    }
  }

  @Test
  public void missingReplacement() {
    ReplaceRegex.Yaml yaml = new ReplaceRegex.Yaml();
    yaml.setRegex("asdf");
    try {
      yaml.withOptions(new Options(new GeneralOptions()));
      fail("should have thrown");
    } catch (ConfigValidationException e) {
      // Expected.
      assertThat(e.getMessage()).contains("missing required field 'replacement'");
    }
  }
}
