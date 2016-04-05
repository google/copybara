// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitDestinationTest {
  @Test
  public void errorIfPushToRefMissing() {
    GitDestination.Yaml yaml = new GitDestination.Yaml();
    yaml.setUrl("file:///foo");
    try {
      yaml.withOptions(new Options(new GitOptions(), new GeneralOptions()));
    } catch (ConfigValidationException expected) {
      assertThat(expected.getMessage()).contains("pushToRef");
    }
  }
}
