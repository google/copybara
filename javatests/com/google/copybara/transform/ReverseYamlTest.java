// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static com.google.copybara.testing.FileSubjects.assertThatPath;

import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.NonReversibleValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.testing.OptionsBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ReverseYamlTest {

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
    yaml.withOptions(options.build()).transform(workdir, options.general.console());

    assertThatPath(workdir)
        .containsFile("file", "asdf");
  }

  @Test
  public void requiresReversibleTransform() throws Exception {
    thrown.expect(NonReversibleValidationException.class);
    thrown.expectMessage("'!NonReversible' transformation is not automatically reversible");
    yaml.setOriginal(new NonReversible());
  }

  @DocElement(yamlName = "!NonReversible", description = "non-reversible",
      elementKind = Transformation.class)
  private static class NonReversible implements Transformation.Yaml {

    @Override
    public Transformation withOptions(Options options) {
      return null;
    }

    @Override
    public void checkReversible() throws ConfigValidationException {
      throw new NonReversibleValidationException(this);
    }
  }
  private Path writeFile(Path path, String text) throws IOException {
    return Files.write(path, text.getBytes(StandardCharsets.UTF_8));
  }
}
