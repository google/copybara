// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.config.Config;

import org.junit.Test;

public class ExampleTest {

  @Test
  public void doNothing() {
    Config.Builder config = new Config.Builder();
    config.setName("name");
    config.setRepository("http://www.example.com");
    config.setDestinationPath("src/copybara");
    new Copybara().runForSourceRef(config.build(), "some_sha1");
  }
}
