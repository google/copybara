package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.Options;

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
    generalOptions = new GeneralOptions(fs.getPath("/workdir"), /*verbose=*/true);
    workdir = generalOptions.getWorkdir();

    Path folder = workdir.resolve("folder");
    Files.createDirectories(folder);
    touchFile("folder/file.txt");
    touchFile("folder/subfolder/file.txt");
    touchFile("folder/subfolder/file.java");
    touchFile("folder2/file.txt");
    touchFile("folder2/subfolder/file.txt");
    touchFile("folder2/subfolder/file.java");
  }

  @Test
  public void invalidPath() throws IOException {
    String outsideFolder = "../../../file";
    Files.createDirectories(workdir.resolve(outsideFolder));
    yaml.setPath(outsideFolder);
    try {
      Transformation delete = yaml.withOptions(new Options(ImmutableList.of(generalOptions)));
      delete.transform(workdir);
      fail("should have thrown");
    } catch (IllegalStateException e) {
      // Expected.
      assertThat(e.getMessage()).contains("Nothing was deleted");
    }
    assertFilesExist(outsideFolder);
  }

  @Test
  public void deleteDoesntDeleteDirectories() throws IOException {
    yaml.setPath("folder");
    Transformation delete = yaml.withOptions(new Options(ImmutableList.of(generalOptions)));
    try {
      delete.transform(workdir);
      fail("Should fail because it could not delete anything.");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("Nothing was deleted. Did you mean 'folder/**'?");
    }
    assertFilesExist("folder", "folder2");
  }

  @Test
  public void recursiveDeleteNoFolder() throws IOException {
    yaml.setPath("folder/**");
    Transformation delete = yaml.withOptions(new Options(ImmutableList.of(generalOptions)));
    delete.transform(workdir);
    assertFilesExist("folder", "folder2");
    assertFilesDontExist("folder/file.txt", "folder/subfolder/file.txt",
        "folder/subfolder/file.java");
  }


  @Test
  public void recursiveByType() throws IOException {
    yaml.setPath("folder/**/*.java");
    Transformation delete = yaml.withOptions(new Options(ImmutableList.of(generalOptions)));
    delete.transform(workdir);
    assertFilesExist("folder", "folder2", "folder/subfolder", "folder/subfolder/file.txt");
    assertFilesDontExist("folder/subfolder/file.java");
  }

  @Test
  public void testToString() {
    yaml.setPath("foo/**/bar.htm");
    String string = yaml.withOptions(new Options(ImmutableList.of(generalOptions))).toString();
    assertThat(string).contains("path=foo/**/bar.htm");
  }

  private Path touchFile(String path) throws IOException {
    Files.createDirectories(workdir.resolve(path).getParent());
    return Files.write(workdir.resolve(path), new byte[]{});
  }

  private void assertFilesExist(String... paths) {
    for (String path : paths) {
      assertThat(Files.exists(workdir.resolve(path))).named(path).isTrue();
    }
  }

  private void assertFilesDontExist(String... paths) {
    for (String path : paths) {
      assertThat(Files.exists(workdir.resolve(path))).named(path).isFalse();
    }
  }
}
