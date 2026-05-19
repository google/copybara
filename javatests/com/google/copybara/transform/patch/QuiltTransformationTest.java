/*
 * Copyright (C) 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.transform.patch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.GeneralOptions;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Optional;
import net.starlark.java.syntax.Location;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class QuiltTransformationTest {

  private static final String OLDDIFF =
      """
      --- a/file1.txt
      +++ b/file1.txt
      @@ -1,3 +1,3 @@
       line1
      -foo
      +bar
       line3
      \\ No newline at end of file
      --- a/file2.txt
      +++ b/file2.txt
      @@ -1 +1 @@
      -bar
      +new bar
      """;

  private static final String SERIES =
      """
      # Comment line
      diff.patch
      """;

  private OptionsBuilder options;
  private PatchingOptions patchingOptions;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  private ConfigFile patchFile;
  private ConfigFile seriesFile;

  @Before
  public void setUp() throws IOException {
    checkoutDir = Files.createTempDirectory("workdir");
    Files.createDirectories(checkoutDir);
    console = new TestingConsole();
    // QuiltTransformation needs to write and read from to real temp directory.
    options = new OptionsBuilder().setWorkdirToRealTempDir();
    // GeneralOptions.getDirFactory() requires $HOME to be set. We set --output-root instead.
    options.setOutputRootToTmpDir();
    patchingOptions = options.build().get(PatchingOptions.class);
    patchingOptions.quiltBin = setUpQuiltBin(patchingOptions.getGeneralOptions());
    skylark = new SkylarkTestExecutor(options);
    ImmutableMap<String, byte[]> configFiles =
        ImmutableMap.of(
            "patches/diff.patch", OLDDIFF.getBytes(UTF_8),
            "patches/series", SERIES.getBytes(UTF_8));
    patchFile = new MapConfigFile(configFiles, "patches/diff.patch");
    seriesFile = new MapConfigFile(configFiles, "patches/series");
  }

  // Returns "quilt" if the "quilt" command can execute successfully. Otherwise, returns the path
  // to the quilt binary from build-time data dependency.
  private static String setUpQuiltBin(GeneralOptions options) {
    // Try executing "quilt --version" to see if "quilt" command can run.
    Command cmd =
        new Command(new String[]{"quilt", "--version"}, options.getEnvironment(), null);
    boolean success = false;
    try {
      options.newCommandRunner(cmd).execute();
      success = true;
    } catch (CommandException e) {
      // "success" remains false.
    }
    if (success) {
      return "quilt";
    }
    Path runtime = Paths.get(System.getenv("TEST_SRCDIR"))
        .resolve(System.getenv("TEST_WORKSPACE"))
        .resolve("third_party/quilt");
    return runtime.resolve("quilt").toAbsolutePath().toString();
  }

  @Test
  public void transformationZeroChangeTest() throws Exception {
    Files.write(checkoutDir.resolve("file1.txt"), "line1\nfoo\nline3".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));
    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.of(seriesFile),
            ImmutableList.of(patchFile),
            patchingOptions,
            /* reverse= */ false,
            /* directory= */ "",
            Location.BUILTIN,
            "patches");

    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "line1\nbar\nline3")
        .containsFile("file2.txt", "new bar\n")
        .containsFile("patches/diff.patch", OLDDIFF)
        .containsFile("patches/series", SERIES)
        .containsNoMoreFiles();
  }

  @Test
  public void transformationUpdatePatchTest() throws Exception {
    String expectedNewDiff =
        """
        --- a/file1.txt
        +++ b/file1.txt
        @@ -1,5 +1,5 @@
         new line
        \s
         line1
        -foo
        +bar
         line3
        \\ No newline at end of file
        --- a/file2.txt
        +++ b/file2.txt
        @@ -1 +1 @@
        -bar
        +new bar
        """;
    Files.write(checkoutDir.resolve("file1.txt"), "new line\n\nline1\nfoo\nline3".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));
    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.of(seriesFile),
            ImmutableList.of(patchFile),
            patchingOptions,
            /* reverse= */ false,
            /* directory= */ "",
            Location.BUILTIN,
            "patches");

    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "new line\n\nline1\nbar\nline3")
        .containsFile("file2.txt", "new bar\n")
        .containsFile("patches/diff.patch", expectedNewDiff)
        .containsFile("patches/series", SERIES)
        .containsNoMoreFiles();
  }

  @Test
  public void transformationWarnsAndReplacesDestinationPatches() throws Exception {
    Files.write(checkoutDir.resolve("file1.txt"), "new line\n\nline1\nfoo\nline3".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));

    Path patchesDir = checkoutDir.resolve("patches");
    Files.createDirectories(patchesDir);
    Files.write(patchesDir.resolve("series"), "stale_series".getBytes(UTF_8));
    Files.write(patchesDir.resolve("stale.patch"), "stale_content".getBytes(UTF_8));
    Files.write(patchesDir.resolve("diff.patch"), "old_diff_content".getBytes(UTF_8));

    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.of(seriesFile),
            ImmutableList.of(patchFile),
            patchingOptions,
            /* reverse= */ false,
            /* directory= */ "",
            Location.BUILTIN,
            "patches");
    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));

    console
        .assertThat()
        .onceInLog(
            com.google.copybara.util.console.Message.MessageType.WARNING,
            ".*Destination already has a 'patches' directory.*");

    // Verify that stale files are NOT deleted
    assertThat(Files.exists(patchesDir.resolve("stale.patch"))).isTrue();
    assertThat(new String(Files.readAllBytes(patchesDir.resolve("stale.patch")), UTF_8))
        .isEqualTo("stale_content");

    // Verify that new files are written
    assertThat(Files.exists(patchesDir.resolve("series"))).isTrue();
    String seriesContent = new String(Files.readAllBytes(patchesDir.resolve("series")), UTF_8);
    assertThat(seriesContent).contains("diff.patch");
    assertThat(seriesContent).doesNotContain("stale_series");
    assertThat(seriesContent).isEqualTo(SERIES);

    // Verify that diff.patch is replaced with a new version
    assertThat(new String(Files.readAllBytes(patchesDir.resolve("diff.patch")), UTF_8))
        .isNotEqualTo("old_diff_content");
    assertThat(new String(Files.readAllBytes(patchesDir.resolve("diff.patch")), UTF_8))
        .contains("file1.txt");
  }

  @Test
  public void transformationUpdateDoesNotApplyError_validationException() throws Exception {
    String diff =
        """
        --- a/file.txt
        +++ b/file.txt
        @@ -1 +1 @@
        -foo
        +new foo
        """;
    Files.write(checkoutDir.resolve("file.txt"), "bar\n".getBytes(UTF_8));
    ImmutableMap<String, byte[]> configFiles =
        ImmutableMap.of(
            "patches/diff.patch", diff.getBytes(UTF_8),
            "patches/series", SERIES.getBytes(UTF_8));
    patchFile = new MapConfigFile(configFiles, "patches/diff.patch");
    seriesFile = new MapConfigFile(configFiles, "patches/series");
    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.of(seriesFile),
            ImmutableList.of(patchFile),
            patchingOptions,
            /* reverse= */ false,
            /* directory= */ "",
            Location.BUILTIN,
            "patches");

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () -> transform.transform(TransformWorks.of(checkoutDir, "testmsg", console)));

    assertThat(e).hasMessageThat().contains("Patch file does not apply.");
  }

  @Test
  public void parseSkylarkTest() throws Exception {
    Files.write(checkoutDir.resolve("file1.txt"), "line1\nfoo\nline3".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));
    skylark.addConfigFile("patches/diff.patch", patchFile.readContent());
    skylark.addConfigFile("patches/series", seriesFile.readContent());
    QuiltTransformation transformation =
        skylark.eval(
            "r",
            """
            r = patch.quilt_apply(
              series = 'patches/series',
            )
            """);

    transformation.transform(TransformWorks.of(checkoutDir, "testmsg", console));

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "line1\nbar\nline3")
        .containsFile("file2.txt", "new bar\n")
        .containsFile("patches/diff.patch", OLDDIFF)
        .containsFile("patches/series", SERIES)
        .containsNoMoreFiles();
  }

  @Test
  public void parseSkylarkTestNotFoundRelaxedCheck() throws Exception {
    patchingOptions.validateOnLoad = false;
    Files.write(checkoutDir.resolve("file1.txt"), "line1\nfoo\nline3".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));
    skylark.addConfigFile("patches/series", seriesFile.readContent());

    QuiltTransformation transformation =
        skylark.eval(
            "r",
            """
            r = patch.quilt_apply(
              series = 'patches/series',
            )
            """);

    assertThat(transformation).isNotNull();
  }

  @Test
  public void parseSkylarkTestNotFoundStrictCheck() throws Exception {
    Files.write(checkoutDir.resolve("file1.txt"), "line1\nfoo\nline3".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));
    skylark.addConfigFile("patches/series", seriesFile.readContent());

    skylark.evalFails(
        """
        patch.quilt_apply(
          series = 'patches/series',
        )
        """,
        "Cannot resolve 'patches");
  }

  @Test
  public void parseSkylarkTest_emptySeries() throws Exception {
    Files.write(checkoutDir.resolve("file1.txt"), "line1\nfoo\nline3".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));
    skylark.addConfigFile("patches/series", "");
    assertThrows(
        ValidationException.class,
        () -> {
          QuiltTransformation unused =
              skylark.eval(
                  "r",
                  """
                  r = patch.quilt_apply(
                    series = 'patches/series',
                  )
                  """);
        });
  }

  @Test
  public void quiltApplyAbsoluteSeriesPathFails() throws Exception {
    skylark.evalFails(
        """
        patch.quilt_apply(
          series = '/tmp/series',
        )
        """,
        "path must be relative");
  }

  @Test
  public void quiltApplyNonNormalizedSeriesPathFails() throws Exception {
    skylark.evalFails(
        """
        patch.quilt_apply(
          series = 'patches/../series',
        )
        """,
        "unexpected . or .. components");
  }

  @Test
  public void quiltApplyIgnoresUserQuiltConfigurationViaEnvironmentVariableTest() throws Exception {
    Files.write(checkoutDir.resolve("file1.txt"), "line1\nfoo\nline3".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));
    skylark.addConfigFile("patches/diff.patch", patchFile.readContent());
    skylark.addConfigFile("patches/series", seriesFile.readContent());
    QuiltTransformation transformation =
        skylark.eval(
            "r",
            """
            r = patch.quilt_apply(
              series = 'patches/series',
            )
            """);

    // Add some configuration to the environment that will cause quilt to fail. Copybara needs to
    // strip that configuration from the environment before it invokes quilt.
    HashMap<String, String> environment = new HashMap<>(options.general.getEnvironment());
    environment.put("QUILT_PC", "/dev/null");
    options.setEnvironment(environment);

    transformation.transform(TransformWorks.of(checkoutDir, "testmsg", console));
  }

  @Test
  public void quiltApplyWithDirectoryTest() throws Exception {
    Path subdir = Files.createDirectories(checkoutDir.resolve("sub/dir"));
    Files.write(subdir.resolve("file1.txt"), "line1\nfoo\nline3".getBytes(UTF_8));
    Files.write(subdir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));

    // Patch for file in subdirectory
    String subDiff =
        """
        --- a/file1.txt
        +++ b/file1.txt
        @@ -1,3 +1,3 @@
         line1
        -foo
        +bar
         line3
        \\ No newline at end of file
        --- a/file2.txt
        +++ b/file2.txt
        @@ -1 +1 @@
        -bar
        +new bar
        """;

    ImmutableMap<String, byte[]> configFiles =
        ImmutableMap.of(
            "patches/diff.patch", subDiff.getBytes(UTF_8),
            "patches/series", SERIES.getBytes(UTF_8));
    ConfigFile subPatchFile = new MapConfigFile(configFiles, "patches/diff.patch");
    ConfigFile subSeriesFile = new MapConfigFile(configFiles, "patches/series");

    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.of(subSeriesFile),
            ImmutableList.of(subPatchFile),
            patchingOptions,
            /* reverse= */ false,
            /* directory= */ "sub/dir",
            Location.BUILTIN,
            "patches");

    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));

    assertThatPath(checkoutDir)
        .containsFile("sub/dir/file1.txt", "line1\nbar\nline3")
        .containsFile("sub/dir/file2.txt", "new bar\n")
        .containsFile("sub/dir/patches/diff.patch", subDiff)
        .containsFile("sub/dir/patches/series", SERIES)
        .containsNoMoreFiles();
  }

  @Test
  public void testValidationLevelDefaultIsFull_missingSeriesThrows() throws Exception {
    skylark.evalFails(
        """
        patch.quilt_apply(
          series = 'patches/missing_series',
        )
        """,
        "Cannot resolve 'patches/missing_series'");
  }

  @Test
  public void testValidationLevelOptionalSeries_missingSeriesNoop() throws Exception {
    QuiltTransformation transformation =
        skylark.eval(
            "r",
            """
            r = patch.quilt_apply(
              series = 'patches/missing_series',
              validation_level = 'OPTIONAL_SERIES',
            )
            """);

    assertThat(transformation.describe())
        .isEqualTo("Patch.quilt_apply: using quilt to apply and update patches: ");
  }

  @Test
  public void testValidationLevelOptionalSeries_missingPatchThrows() throws Exception {
    skylark.addConfigFile("patches/series", "missing.patch\n");

    skylark.evalFails(
        """
        patch.quilt_apply(
          series = 'patches/series',
          validation_level = 'OPTIONAL_SERIES',
        )
        """,
        "Cannot resolve 'patches/missing.patch'");
  }

  @Test
  public void quiltApplyWithDirectoryAndUpdatedPatchTest() throws Exception {
    Path subdir = Files.createDirectories(checkoutDir.resolve("sub/dir"));
    Files.write(subdir.resolve("file1.txt"), "new line\n\nline1\nfoo\nline3".getBytes(UTF_8));
    Files.write(subdir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));

    String expectedNewDiff =
        """
        --- a/file1.txt
        +++ b/file1.txt
        @@ -1,5 +1,5 @@
         new line
        \s
         line1
        -foo
        +bar
         line3
        \\ No newline at end of file
        --- a/file2.txt
        +++ b/file2.txt
        @@ -1 +1 @@
        -bar
        +new bar
        """;

    ImmutableMap<String, byte[]> configFiles =
        ImmutableMap.of(
            "patches/diff.patch", OLDDIFF.getBytes(UTF_8),
            "patches/series", SERIES.getBytes(UTF_8));
    ConfigFile subPatchFile = new MapConfigFile(configFiles, "patches/diff.patch");
    ConfigFile subSeriesFile = new MapConfigFile(configFiles, "patches/series");

    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.of(subSeriesFile),
            ImmutableList.of(subPatchFile),
            patchingOptions,
            /* reverse= */ false,
            /* directory= */ "sub/dir",
            Location.BUILTIN,
            "patches");

    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));

    assertThatPath(checkoutDir)
        .containsFile("sub/dir/file1.txt", "new line\n\nline1\nbar\nline3")
        .containsFile("sub/dir/file2.txt", "new bar\n")
        .containsFile("sub/dir/patches/diff.patch", expectedNewDiff)
        .containsFile("sub/dir/patches/series", SERIES)
        .containsNoMoreFiles();
  }

  @Test
  public void transformationCustomPatchesDirTest() throws Exception {
    String expectedNewDiff =
        """
        --- a/file1.txt
        +++ b/file1.txt
        @@ -1,5 +1,5 @@
         new line
        \s
         line1
        -foo
        +bar
         line3
        \\ No newline at end of file
        --- a/file2.txt
        +++ b/file2.txt
        @@ -1 +1 @@
        -bar
        +new bar
        """;
    Files.write(checkoutDir.resolve("file1.txt"), "new line\n\nline1\nfoo\nline3".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));

    ImmutableMap<String, byte[]> configFiles =
        ImmutableMap.of(
            "_GOOGLE_PATCHES/diff.patch", OLDDIFF.getBytes(UTF_8),
            "_GOOGLE_PATCHES/series", "diff.patch\n".getBytes(UTF_8));
    ConfigFile customPatchFile = new MapConfigFile(configFiles, "_GOOGLE_PATCHES/diff.patch");
    ConfigFile customSeriesFile = new MapConfigFile(configFiles, "_GOOGLE_PATCHES/series");

    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.of(customSeriesFile),
            ImmutableList.of(customPatchFile),
            patchingOptions,
            /* reverse= */ false,
            /* directory= */ "",
            Location.BUILTIN,
            "_GOOGLE_PATCHES");

    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "new line\n\nline1\nbar\nline3")
        .containsFile("file2.txt", "new bar\n")
        .containsFile("_GOOGLE_PATCHES/diff.patch", expectedNewDiff)
        .containsFile("_GOOGLE_PATCHES/series", "diff.patch\n")
        .containsNoMoreFiles();
  }

  @Test
  public void quiltApplyWithDirectoryWarnsAndReplacesDestinationPatches() throws Exception {
    Path subdir = Files.createDirectories(checkoutDir.resolve("sub/dir"));
    Files.write(subdir.resolve("file1.txt"), "new line\n\nline1\nfoo\nline3".getBytes(UTF_8));
    Files.write(subdir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));

    Path patchesDir = subdir.resolve("patches");
    Files.createDirectories(patchesDir);
    Files.write(patchesDir.resolve("series"), "stale_series".getBytes(UTF_8));
    Files.write(patchesDir.resolve("stale.patch"), "stale_content".getBytes(UTF_8));
    Files.write(patchesDir.resolve("diff.patch"), "old_diff_content".getBytes(UTF_8));

    ImmutableMap<String, byte[]> configFiles =
        ImmutableMap.of(
            "patches/diff.patch", OLDDIFF.getBytes(UTF_8),
            "patches/series", SERIES.getBytes(UTF_8));
    ConfigFile subPatchFile = new MapConfigFile(configFiles, "patches/diff.patch");
    ConfigFile subSeriesFile = new MapConfigFile(configFiles, "patches/series");

    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.of(subSeriesFile),
            ImmutableList.of(subPatchFile),
            patchingOptions,
            /* reverse= */ false,
            /* directory= */ "sub/dir",
            Location.BUILTIN,
            "patches");
    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));

    console
        .assertThat()
        .onceInLog(
            com.google.copybara.util.console.Message.MessageType.WARNING,
            ".*Destination already has a 'patches' directory.*");

    // Verify that stale files are NOT deleted
    assertThat(Files.exists(patchesDir.resolve("stale.patch"))).isTrue();
    assertThat(new String(Files.readAllBytes(patchesDir.resolve("stale.patch")), UTF_8))
        .isEqualTo("stale_content");

    // Verify that new files are written
    assertThat(Files.exists(patchesDir.resolve("series"))).isTrue();
    String seriesContent = new String(Files.readAllBytes(patchesDir.resolve("series")), UTF_8);
    assertThat(seriesContent).contains("diff.patch");
    assertThat(seriesContent).doesNotContain("stale_series");
    assertThat(seriesContent).isEqualTo(SERIES);

    // Verify that diff.patch is replaced with a new version
    assertThat(new String(Files.readAllBytes(patchesDir.resolve("diff.patch")), UTF_8))
        .isNotEqualTo("old_diff_content");
    assertThat(new String(Files.readAllBytes(patchesDir.resolve("diff.patch")), UTF_8))
        .contains("file1.txt");
  }

  @Test
  public void transformationCustomPatchesDirWarnsAndReplacesDestinationPatches() throws Exception {
    Files.write(checkoutDir.resolve("file1.txt"), "new line\n\nline1\nfoo\nline3".getBytes(UTF_8));
    Files.write(checkoutDir.resolve("file2.txt"), "bar\n".getBytes(UTF_8));

    Path patchesDir = checkoutDir.resolve("_GOOGLE_PATCHES");
    Files.createDirectories(patchesDir);
    Files.write(patchesDir.resolve("series"), "stale_series".getBytes(UTF_8));
    Files.write(patchesDir.resolve("stale.patch"), "stale_content".getBytes(UTF_8));
    Files.write(patchesDir.resolve("diff.patch"), "old_diff_content".getBytes(UTF_8));

    ImmutableMap<String, byte[]> configFiles =
        ImmutableMap.of(
            "_GOOGLE_PATCHES/diff.patch", OLDDIFF.getBytes(UTF_8),
            "_GOOGLE_PATCHES/series", SERIES.getBytes(UTF_8));
    ConfigFile customPatchFile = new MapConfigFile(configFiles, "_GOOGLE_PATCHES/diff.patch");
    ConfigFile customSeriesFile = new MapConfigFile(configFiles, "_GOOGLE_PATCHES/series");

    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.of(customSeriesFile),
            ImmutableList.of(customPatchFile),
            patchingOptions,
            /* reverse= */ false,
            /* directory= */ "",
            Location.BUILTIN,
            "_GOOGLE_PATCHES");
    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));

    console
        .assertThat()
        .onceInLog(
            com.google.copybara.util.console.Message.MessageType.WARNING,
            ".*Destination already has a '_GOOGLE_PATCHES' directory.*");

    // Verify that stale files are NOT deleted
    assertThat(Files.exists(patchesDir.resolve("stale.patch"))).isTrue();
    assertThat(new String(Files.readAllBytes(patchesDir.resolve("stale.patch")), UTF_8))
        .isEqualTo("stale_content");

    // Verify that new files are written
    assertThat(Files.exists(patchesDir.resolve("series"))).isTrue();
    String seriesContent = new String(Files.readAllBytes(patchesDir.resolve("series")), UTF_8);
    assertThat(seriesContent).contains("diff.patch");
    assertThat(seriesContent).doesNotContain("stale_series");
    assertThat(seriesContent).isEqualTo(SERIES);

    // Verify that diff.patch is replaced with a new version
    assertThat(new String(Files.readAllBytes(patchesDir.resolve("diff.patch")), UTF_8))
        .isNotEqualTo("old_diff_content");
    assertThat(new String(Files.readAllBytes(patchesDir.resolve("diff.patch")), UTF_8))
        .contains("file1.txt");
  }

  @Test
  public void describeTest() {
    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.of(seriesFile),
            ImmutableList.of(patchFile),
            patchingOptions,
            /* reverse= */ false,
            "",
            Location.BUILTIN,
            "patches");
    assertThat(transform.describe())
        .isEqualTo(
            "Patch.quilt_apply: using quilt to apply and update patches: patches/diff.patch");
  }

  @Test
  public void transformationEmptySeriesNoopTest() throws Exception {
    Files.write(checkoutDir.resolve("file1.txt"), "line1\n".getBytes(UTF_8));
    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.empty(),
            ImmutableList.of(),
            patchingOptions,
            /* reverse= */ false,
            /* directory= */ "",
            Location.BUILTIN,
            "patches");

    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));

    assertThatPath(checkoutDir).containsFile("file1.txt", "line1\n").containsNoMoreFiles();
    console
        .assertThat()
        .timesInLog(
            0,
            com.google.copybara.util.console.Message.MessageType.INFO,
            ".*Applying and updating patches with quilt.*");
  }

  @Test
  public void transformationEmptyPatchesCopiesSeriesTest() throws Exception {
    Files.write(checkoutDir.resolve("file1.txt"), "line1\n".getBytes(UTF_8));
    QuiltTransformation transform =
        new QuiltTransformation(
            Optional.of(seriesFile),
            ImmutableList.of(),
            patchingOptions,
            /* reverse= */ false,
            /* directory= */ "",
            Location.BUILTIN,
            "patches");

    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));

    assertThatPath(checkoutDir)
        .containsFile("file1.txt", "line1\n")
        .containsFile("patches/series", SERIES)
        .containsNoMoreFiles();
    console
        .assertThat()
        .timesInLog(
            0,
            com.google.copybara.util.console.Message.MessageType.INFO,
            ".*Applying and updating patches with quilt.*");
  }

  @Test
  public void parseSkylarkTest_validateOnLoadOverridesValidationLevelNone() throws Exception {
    patchingOptions.validateOnLoad = true;
    skylark.evalFails(
        """
        patch.quilt_apply(
          series = 'patches/missing_series',
          validation_level = 'NONE',
        )
        """,
        "Cannot resolve 'patches/missing_series'");
  }
}
