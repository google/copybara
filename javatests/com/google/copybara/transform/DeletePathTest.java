package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class DeletePathTest {

  private DeletePath.Yaml yaml;
  private GeneralOptions generalOptions;
  private Path workdir;

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    yaml = new DeletePath.Yaml();
    generalOptions = new GeneralOptions(fs);
    generalOptions.init();
    workdir = generalOptions.getWorkdir();
  }

  @Test
  public void invalidPath() {
    yaml.setPath("../../../folder");
    try {
      yaml.withOptions(new Options(generalOptions));
      fail("should have thrown");
    } catch (ConfigValidationException e) {
      // Expected.
      assertThat(e.getMessage()).contains("Only relative paths to workdir are allowed");
    }
  }

  @Test
  public void deletePath() throws IOException {
    yaml.setPath("folder");
    Path folder = workdir.resolve("folder");
    Files.createDirectory(folder);
    Files.write(folder.resolve("file.txt"), "hello this is dog".getBytes());
    Files.createDirectory(folder.resolve("subfolder"));
    Files.write(folder.resolve("subfolder/file2.txt"), "hello this is cat".getBytes());

    Transformation delete = yaml.withOptions(new Options(generalOptions));
    delete.transform(workdir);

    assertThat(Files.exists(folder)).isFalse();
  }
}
