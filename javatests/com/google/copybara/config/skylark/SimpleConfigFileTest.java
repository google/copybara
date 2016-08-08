package com.google.copybara.config.skylark;

import com.google.common.jimfs.Jimfs;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleConfigFileTest {

  private FileSystem fs;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() {
    fs = Jimfs.newFileSystem();
  }

  @Test
  public void testResolve() throws IOException, CannotResolveLabel {
    Path foo = Files.write(fs.getPath("/foo"), "foo".getBytes());
    Files.write(fs.getPath("/bar"), "bar".getBytes());
    Files.createDirectories(fs.getPath("/baz"));
    Files.write(fs.getPath("/baz/foo"), "bazfoo".getBytes());
    Files.write(fs.getPath("/baz/bar"), "bazbar".getBytes());

    ConfigFile fooConfig = new SimpleConfigFile(foo);
    Truth.assertThat(fooConfig.content()).isEqualTo("foo".getBytes());
    Truth.assertThat(fooConfig.path()).isEqualTo("/foo");
    Truth.assertThat(fooConfig.resolve("bar").content()).isEqualTo("bar".getBytes());

    ConfigFile bazFooConfig = fooConfig.resolve("baz/foo");
    Truth.assertThat(bazFooConfig.content()).isEqualTo("bazfoo".getBytes());
    // Checks that the correct bar is resolved.
    Truth.assertThat(bazFooConfig.resolve("bar").content()).isEqualTo("bazbar".getBytes());
  }

  @Test
  public void testResolveFailure() throws IOException, CannotResolveLabel {
    ConfigFile fooConfig = new SimpleConfigFile(Files.write(fs.getPath("/foo"), "foo".getBytes()));
    thrown.expect(CannotResolveLabel.class);
    thrown.expectMessage("Cannot find 'bar'. '/bar' does not exist.");
    fooConfig.resolve("bar");
  }
}
