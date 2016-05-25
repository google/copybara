// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.OptionsBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public final class ReverseTest {

  private Reverse.Yaml yaml;
  private OptionsBuilder options;
  private Path workdir;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException {
    yaml = new Reverse.Yaml();
    options = new OptionsBuilder();
    workdir = Files.createTempDirectory("ReverseTest");
  }

  @Test
  public void reverseReplace() throws Exception {
    Replace.Yaml replace = new Replace.Yaml();
    replace.setBefore("asdf");
    replace.setAfter("jkl;");
    yaml.setOriginal(replace);

    writeFile(workdir.resolve("file"), "jkl;");
    yaml.withOptions(options.build()).transform(workdir);
    assertFileContents("file", "asdf");
  }

  @Test
  public void requiresReversibleTransform() throws Exception {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'original' transformation is not automatically reversible.");
    yaml.setOriginal(new Transformation.Yaml() {
      @Override
      public Transformation withOptions(Options options) {
        return null;
      }
    });
  }

  private void assertFileContents(String path, String expectedText) throws IOException {
    assertThat(new String(Files.readAllBytes(workdir.resolve(path)))).isEqualTo(expectedText);
  }

  private Path writeFile(Path path, String text) throws IOException {
    return Files.write(path, text.getBytes());
  }
}
