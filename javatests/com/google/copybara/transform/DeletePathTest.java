package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.copybara.testing.OptionsBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class DeletePathTest {

  private DeletePath.Yaml yaml;
  private OptionsBuilder options;

  @Before
  public void setup() throws IOException {
    yaml = new DeletePath.Yaml();
    options = new OptionsBuilder();

    Path folder = workdir().resolve("folder");
    Files.createDirectories(folder);
    touchFile("folder/file.txt");
    touchFile("folder/subfolder/file.txt");
    touchFile("folder/subfolder/file.java");
    touchFile("folder2/file.txt");
    touchFile("folder2/subfolder/file.txt");
    touchFile("folder2/subfolder/file.java");
  }

  private Path workdir() {
    return options.general.getWorkdir();
  }

  @Test
  public void invalidPath() throws Exception {
    String outsideFolder = "../../../file";
    Files.createDirectories(workdir().resolve(outsideFolder));
    yaml.setPath(outsideFolder);
    try {
      Transformation delete = yaml.withOptions(options.build());
      delete.transform(workdir());
      fail("should have thrown");
    } catch (IllegalStateException e) {
      // Expected.
      assertThat(e.getMessage()).contains("Nothing was deleted");
    }
    assertFilesExist(outsideFolder);
  }

  @Test
  public void deleteDoesntDeleteDirectories() throws Exception {
    yaml.setPath("folder");
    Transformation delete = yaml.withOptions(options.build());
    try {
      delete.transform(workdir());
      fail("Should fail because it could not delete anything.");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("Nothing was deleted. Did you mean 'folder/**'?");
    }
    assertFilesExist("folder", "folder2");
  }

  @Test
  public void recursiveDeleteNoFolder() throws Exception {
    yaml.setPath("folder/**");
    Transformation delete = yaml.withOptions(options.build());
    delete.transform(workdir());
    assertFilesExist("folder", "folder2");
    assertFilesDontExist("folder/file.txt", "folder/subfolder/file.txt",
        "folder/subfolder/file.java");
  }


  @Test
  public void recursiveByType() throws Exception {
    yaml.setPath("folder/**/*.java");
    Transformation delete = yaml.withOptions(options.build());
    delete.transform(workdir());
    assertFilesExist("folder", "folder2", "folder/subfolder", "folder/subfolder/file.txt");
    assertFilesDontExist("folder/subfolder/file.java");
  }

  @Test
  public void testToString() throws Exception {
    yaml.setPath("foo/**/bar.htm");
    String string = yaml.withOptions(options.build()).toString();
    assertThat(string).contains("path=foo/**/bar.htm");
  }

  private Path touchFile(String path) throws IOException {
    Files.createDirectories(workdir().resolve(path).getParent());
    return Files.write(workdir().resolve(path), new byte[]{});
  }

  private void assertFilesExist(String... paths) {
    for (String path : paths) {
      assertThat(Files.exists(workdir().resolve(path))).named(path).isTrue();
    }
  }

  private void assertFilesDontExist(String... paths) {
    for (String path : paths) {
      assertThat(Files.exists(workdir().resolve(path))).named(path).isFalse();
    }
  }
}
