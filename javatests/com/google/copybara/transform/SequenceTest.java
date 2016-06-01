package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.path;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.EnvironmentException;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.NonReversibleValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.transform.Transformation.Yaml;
import com.google.copybara.util.console.Console;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class SequenceTest {

  private Path workdir;
  private Console console;
  private OptionsBuilder options;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    workdir = fs.getPath("/");
    Files.createDirectories(workdir);
    options = new OptionsBuilder();
    console = options.general.console();
  }

  @Test
  public void testNoReversible() throws ValidationException, IOException, EnvironmentException {
    Sequence.Yaml seq = new Sequence.Yaml();
    seq.setTransformations(ImmutableList.<Yaml>of(new NonReversibleTransform()));
    thrown.expect(NonReversibleValidationException.class);
    seq.checkReversible();
  }

  @Test
  public void testNestedSequence() throws ValidationException, IOException,
      EnvironmentException {
    Sequence.Yaml topLevel = new Sequence.Yaml();
    Sequence.Yaml inner = new Sequence.Yaml();
    NonReversibleTransform nonReversible = new NonReversibleTransform();

    inner.setTransformations(ImmutableList.<Transformation.Yaml>of(nonReversible));
    topLevel.setTransformations(ImmutableList.<Transformation.Yaml>of(inner));

    Sequence seq = topLevel.withOptions(options.build());

    assertThat(seq.getSequence()).hasSize(1);
    assertThat(seq.getSequence()).containsExactly(nonReversible);
  }

  @Test
  public void testReversible() throws ValidationException, IOException, EnvironmentException {
    Files.write(workdir.resolve("file.txt"), "foo".getBytes());
    Replace.Yaml replaceOne = new Replace.Yaml();
    replaceOne.setBefore("foo");
    replaceOne.setAfter("bar");
    Replace.Yaml replaceTwo = new Replace.Yaml();
    replaceTwo.setBefore("bar");
    replaceTwo.setAfter("baz");

    Sequence.Yaml yaml = new Sequence.Yaml();
    yaml.setTransformations(ImmutableList.<Transformation.Yaml>of(replaceOne, replaceTwo));
    Transformation seq = yaml.withOptions(options.build());
    seq.transform(workdir, console);

    assertAbout(path()).that(workdir).containsFile("file.txt", "baz");

    seq.reverse().transform(workdir, console);

    assertAbout(path()).that(workdir).containsFile("file.txt", "foo");
  }

  @Test
  public void testReverseEmpty() throws IOException, ValidationException, EnvironmentException {
    Files.write(workdir.resolve("file.txt"), "foo".getBytes());
    Transformation seq = new Sequence.Yaml().withOptions(options.build());

    seq.transform(workdir, console);

    assertAbout(path()).that(workdir).containsFile("file.txt", "foo");

    seq.reverse().transform(workdir, console);

    assertAbout(path()).that(workdir).containsFile("file.txt", "foo");
  }

  @DocElement(yamlName = "!NonReversibleTransform", description = "NonReversibleTransform", elementKind = Transformation.class)
  private static class NonReversibleTransform implements Transformation, Transformation.Yaml {

    @Override
    public void transform(Path workdir, Console console) throws IOException, ValidationException {
      throw new IllegalStateException();
    }

    @Override
    public Transformation reverse() {
      throw new UnsupportedOperationException("Non reversible!");
    }

    @Override
    public String describe() {
      return "NonReversible";
    }

    @Override
    public Transformation withOptions(Options options)
        throws ConfigValidationException, EnvironmentException {
      return this;
    }

    @Override
    public void checkReversible() throws ConfigValidationException {
      throw new NonReversibleValidationException(this);
    }
  }
}
