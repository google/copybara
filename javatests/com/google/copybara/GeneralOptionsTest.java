package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

import com.google.common.jimfs.Jimfs;
import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;

@RunWith(JUnit4.class)
public class GeneralOptionsTest {

  private FileSystem fs;
  private GeneralOptions generalOptions;

  @Before
  public void setup() {
    fs = Jimfs.newFileSystem();
    generalOptions = new GeneralOptions(fs);
  }
  @Test
  public void getWorkdirNormalized() throws Exception {
    generalOptions.workdir = "/some/../path/..";
    generalOptions.init();
    assertThat(generalOptions.getWorkdir().toString()).isEqualTo("/");
  }

  @Test
  public void getWorkdirIsNotDirectory() throws Exception {
    Files.write(fs.getPath("file"), "hello".getBytes());

    generalOptions.workdir = "file";
    try {
      generalOptions.init();
      fail("Should fail because file is not a directory");
    } catch (IOException e) {
      assertThat(e).hasMessage("'file' exists and is not a directory");
    }
  }
}