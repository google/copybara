package com.google.copybara.localdir;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.localdir.FolderDestination.Yaml;

import com.beust.jcommander.internal.Lists;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FolderDestinationTest {

  private LocalDestinationOptions localOptions;
  private Yaml yaml;
  private GeneralOptions generalOptions;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException, RepoException {
    localOptions = new LocalDestinationOptions();
    yaml = new Yaml();
    generalOptions = new GeneralOptions();
    generalOptions.init();
    Path workdir = generalOptions.getWorkdir();
    Files.createDirectory(workdir.resolve("dir"));
    Files.write(workdir.resolve("test.txt"), new byte[]{});
    Files.write(workdir.resolve("dir/file.txt"), new byte[]{});
  }

  @Test
  public void testCopyWithExcludes() throws IOException, RepoException {
    Path localFolder = Files.createTempDirectory("local_folder");

    Files.createDirectory(localFolder.resolve("one"));
    Files.createDirectory(localFolder.resolve("two"));
    Files.write(localFolder.resolve("root_file"), new byte[]{});
    Files.write(localFolder.resolve("root_file2"), new byte[]{});
    Files.write(localFolder.resolve("one/file.txt"), new byte[]{});
    Files.write(localFolder.resolve("one/file.java"), new byte[]{});
    Files.write(localFolder.resolve("two/file.java"), new byte[]{});

    localOptions.localFolder = localFolder.toString();
    yaml.excludePathsForDeletion = Lists.newArrayList("root_file", "**\\.java");
    Destination destination = yaml.withOptions(new Options(localOptions, generalOptions));
    destination.process(generalOptions.getWorkdir());
    assertFilesExist(localFolder, "one", "two", "root_file",
        "one/file.java", "two/file.java", "test.txt", "dir/file.txt");
    assertFilesDontExist(localFolder, "root_file2", "one/file.txt");
  }

  @Test
  public void testFolderDirRequired() throws IOException, RepoException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("--folder-dir is required");
    yaml.withOptions(new Options(localOptions, generalOptions));
  }

  private void assertFilesExist(Path base, String... paths) {
    for (String path : paths) {
      assertThat(Files.exists(base.resolve(path))).named(path).isTrue();
    }
  }

  private void assertFilesDontExist(Path base, String... paths) {
    for (String path : paths) {
      assertThat(Files.exists(base.resolve(path))).named(path).isFalse();
    }
  }
}
