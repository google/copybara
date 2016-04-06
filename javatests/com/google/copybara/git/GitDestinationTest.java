// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitDestinationTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void errorIfPushToRefMissing() {
    GitDestination.Yaml yaml = new GitDestination.Yaml();
    yaml.setPullFromRef("master");
    yaml.setUrl("file:///foo");
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("pushToRef");
    yaml.withOptions(new Options(new GitOptions(), new GeneralOptions()));

  }
}
