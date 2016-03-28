// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.copybara.config.Config;

import org.junit.Test;

public class ExampleTest {

  @Test
  public void doNothing() {
    new Copybara().runForSourceRef(
        new Config("test", "http://www.example.com", "src/copybara"),
        "some_sha1");
  }
}
