package com.google.copybara.localdir;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.Lists;
import com.google.copybara.Destination;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.localdir.FolderDestination.Yaml;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.MockReference;
import com.google.copybara.testing.OptionsBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class FolderDestinationTest {

  private static final String CONFIG_NAME = "copybara_project";

  private Yaml yaml;
  private OptionsBuilder options;

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private Path workdir;

  @Before
  public void setup() throws IOException, RepoException {
    yaml = new Yaml();
    workdir = Files.createTempDirectory("workdir");
    options = new OptionsBuilder().setWorkdirToRealTempDir();
    Files.createDirectory(workdir.resolve("dir"));
    Files.write(workdir.resolve("test.txt"), new byte[]{});
    Files.write(workdir.resolve("dir/file.txt"), new byte[]{});
  }

  @Test
  public void testCopyWithExcludes() throws Exception {
    Path localFolder = Files.createTempDirectory("local_folder");

    Files.createDirectory(localFolder.resolve("one"));
    Files.createDirectory(localFolder.resolve("two"));
    Files.write(localFolder.resolve("root_file"), new byte[]{});
    Files.write(localFolder.resolve("root_file2"), new byte[]{});
    Files.write(localFolder.resolve("one/file.txt"), new byte[]{});
    Files.write(localFolder.resolve("one/file.java"), new byte[]{});
    Files.write(localFolder.resolve("two/file.java"), new byte[]{});

    options.localDestination.localFolder = localFolder.toString();
    yaml.excludePathsForDeletion = Lists.newArrayList("root_file", "**\\.java");
    Destination destination = yaml.withOptions(options.build(), CONFIG_NAME);
    destination.process(workdir, new MockReference("origin_ref"),
        /*timestamp=*/424242420, "Not relevant", options.general.console());

    assertAbout(FileSubjects.path())
        .that(localFolder)
        .containsFiles("one", "two", "root_file",
            "one/file.java", "two/file.java", "test.txt", "dir/file.txt")
        .containsNoMoreFiles();
  }

  @Test
  public void testFolderDirRequired() throws Exception {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("--folder-dir is required");
    yaml.withOptions(options.build(), CONFIG_NAME);
  }
}
