package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.util.console.LogConsole;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;

@RunWith(JUnit4.class)
public class GeneralOptionsTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private GeneralOptions.Args generalOptionsArgs;
  private FileSystem fs;

  @Before
  public void setup() {
    generalOptionsArgs = new GeneralOptions.Args();
    fs = Jimfs.newFileSystem();
  }

  private GeneralOptions init() throws IOException {
    return generalOptionsArgs.init(fs, new LogConsole(System.err));
  }

  @Test
  public void getWorkdirNormalized() throws Exception {
    generalOptionsArgs.workdir = "/some/../path/..";
    assertThat(init().getWorkdir().toString())
        .isEqualTo("/");
  }

  @Test
  public void getWorkdirIsNotDirectory() throws Exception {
    Files.write(fs.getPath("file"), "hello".getBytes());

    generalOptionsArgs.workdir = "file";
    thrown.expect(IOException.class);
    thrown.expectMessage("'file' exists and is not a directory");
    init();
  }
}
