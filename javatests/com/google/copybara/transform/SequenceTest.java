package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.copybara.testing.FileSubjects.path;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.Truth;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.console.Console;

import org.junit.Before;
import org.junit.Test;
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

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    workdir = fs.getPath("/");
    Files.createDirectories(workdir);
    options = new OptionsBuilder();
    console = options.general.console();
  }

  @Test
  public void testNoReversible() throws ConfigValidationException {
    Transformation seq = Sequence.of(ImmutableList.<Transformation>of(
        new NonReversibleTransform()));
    Truth.assertThat(seq instanceof ReversibleTransformation).isFalse();
  }

  @Test
  public void testReversible() throws ValidationException, IOException {
    Files.write(workdir.resolve("file.txt"), "foo".getBytes());

    Options opt = options.build();
    Replace.Yaml replaceOne = new Replace.Yaml();
    replaceOne.setBefore("foo");
    replaceOne.setAfter("bar");
    Replace.Yaml replaceTwo = new Replace.Yaml();
    replaceTwo.setBefore("bar");
    replaceTwo.setAfter("baz");

    Transformation seq = Sequence.of(ImmutableList.<Transformation>of(
        replaceOne.withOptions(opt), replaceTwo.withOptions(opt)));
    Truth.assertThat(seq.getClass()).isAssignableTo(ReversibleTransformation.class);
    ReversibleTransformation reveversibleSeq = (ReversibleTransformation) seq;

    reveversibleSeq.transform(workdir, console);

    assertAbout(path()).that(workdir).containsFile("file.txt", "baz");

    reveversibleSeq.reverse().transform(workdir, console);

    assertAbout(path()).that(workdir).containsFile("file.txt", "foo");
  }

  @Test
  public void testReverseEmpty() throws IOException, ValidationException {
    Files.write(workdir.resolve("file.txt"), "foo".getBytes());
    Transformation seq = Sequence.of(ImmutableList.<Transformation>of());
    Truth.assertThat(seq.getClass()).isAssignableTo(ReversibleTransformation.class);
    ReversibleTransformation reveversibleSeq = (ReversibleTransformation) seq;

    reveversibleSeq.transform(workdir, console);

    assertAbout(path()).that(workdir).containsFile("file.txt", "foo");

    reveversibleSeq.reverse().transform(workdir, console);

    assertAbout(path()).that(workdir).containsFile("file.txt", "foo");
  }

  private static class NonReversibleTransform implements Transformation {

    @Override
    public void transform(Path workdir, Console console) throws IOException, ValidationException {
      throw new IllegalStateException();
    }

    @Override
    public String describe() {
      throw new IllegalStateException();
    }
  }
}
