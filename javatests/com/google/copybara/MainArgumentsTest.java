package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.jimfs.Jimfs;

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
public class MainArgumentsTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private MainArguments mainArguments;
  private FileSystem fs;

  @Before
  public void setup() {
    mainArguments = new MainArguments();
    fs = Jimfs.newFileSystem();
  }

  @Test
  public void getWorkdirNormalized() throws Exception {
    mainArguments.baseWorkdir = "/some/../path/..";
    Path baseWorkdir = mainArguments.getBaseWorkdir(fs);
    assertThat(baseWorkdir.toString()).isEqualTo("/");
  }

  @Test
  public void getWorkdirIsNotDirectory() throws Exception {
    Files.write(fs.getPath("file"), "hello".getBytes());

    mainArguments.baseWorkdir = "file";
    thrown.expect(IOException.class);
    thrown.expectMessage("'file' exists and is not a directory");
    mainArguments.getBaseWorkdir(fs);
  }
}
