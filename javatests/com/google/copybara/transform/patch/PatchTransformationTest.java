/*
 * Copyright (C) 2016 Google Inc.
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
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitRepository;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.starlark.java.syntax.Location;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PatchTransformation} and {@link PatchModule}.
 */
@RunWith(JUnit4.class)
public class PatchTransformationTest {

  private static final String DIFF =
      ""
          + "diff --git a/test.txt b/test.txt\n"
          + "index 257cc56..5716ca5 100644\n"
          + "--- a/test.txt\n"
          + "+++ b/test.txt\n"
          + "@@ -1 +1 @@\n"
          + "-foo\n"
          + "+bar\n"
          + "diff --git a/excluded/file2.txt b/excluded/file2.txt\n"
          + "index ba0e162..9c08216 100644\n"
          + "--- a/excluded/file2.txt\n"
          + "+++ b/excluded/file2.txt\n"
          + "@@ -1 +1 @@\n"
          + "-bar\n"
          + "\\ No newline at end of file\n"
          + "+new bar\n"
          + "\\ No newline at end of file";

  private OptionsBuilder options;
  private PatchingOptions patchingOptions;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  private ConfigFile patchFile;
  private ConfigFile seriesFile;
  private final ImmutableList<String> excludedFromPatch = ImmutableList.of("excluded/*");

  @Before
  public void setup() throws IOException {
    checkoutDir =  Files.createTempDirectory("workdir");
    Files.createDirectories(checkoutDir);
    console = new TestingConsole();
    options = new OptionsBuilder().setConsole(console);
    patchingOptions = options.build().get(PatchingOptions.class);
    skylark = new SkylarkTestExecutor(options);
    ImmutableMap<String, byte[]> configFiles =
        ImmutableMap.of(
            "diff.patch", (DIFF).getBytes(UTF_8),
            "series", ("diff.patch\n").getBytes(UTF_8));
    patchFile = new MapConfigFile(configFiles , "diff.patch");
    seriesFile = new MapConfigFile(configFiles, "series");
    options.setEnvironment(GitTestUtil.getGitEnv().getEnvironment());
    // In preparation to switch to the new default. PatchingOptionsTest has more coverage on this.
    options.patch.useGitApply = false;
    options.patch.skipVersionCheck = false;
  }

  @Test
  public void applyTransformationTest() throws Exception {
    Files.write(checkoutDir.resolve("test.txt"), "foo\n".getBytes(UTF_8));
    PatchTransformation transform =
        new PatchTransformation(ImmutableList.of(patchFile), excludedFromPatch, patchingOptions,
            /*reverse=*/ false, /*strip=*/1, Location.BUILTIN);
    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));
    assertThatPath(checkoutDir)
        .containsFile("test.txt", "bar\n")
        .containsNoMoreFiles();
  }

  @Test
  public void insideGitFolderTest() throws Exception {
    GitRepository.newRepo(/*verbose*/ false, checkoutDir, GitTestUtil.getGitEnv()).init();

    Path foo = Files.createDirectories(checkoutDir.resolve("foo"));
    Files.write(foo.resolve("test.txt"), "foo\n".getBytes(UTF_8));
    PatchTransformation transform =
        new PatchTransformation(ImmutableList.of(patchFile), excludedFromPatch, patchingOptions,
            /*reverse=*/ false, /*strip=*/1, Location.BUILTIN);
    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () -> transform.transform(TransformWorks.of(foo, "testmsg", console)));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Cannot use patch.apply because Copybara temporary directory");
  }

  @Test
  public void reverseTransformationTest() throws Exception {
    Files.write(checkoutDir.resolve("test.txt"), "bar\n".getBytes(UTF_8));
    PatchTransformation transform =
        new PatchTransformation(ImmutableList.of(patchFile), excludedFromPatch, patchingOptions,
            /*reverse=*/ true, /*strip=*/1, Location.BUILTIN);
    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));
    assertThatPath(checkoutDir)
        .containsFile("test.txt", "foo\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testParseSkylark() throws Exception {
    Files.write(checkoutDir.resolve("test.txt"), "foo\n".getBytes(UTF_8));
    skylark.addConfigFile("diff.patch", patchFile.readContent());
    PatchTransformation transformation =
        skylark.eval("r",
            "r = patch.apply(\n"
                + "  patches = ['diff.patch'],\n"
                + "  excluded_patch_paths = ['excluded/*'],\n"
                + ")\n");
    transformation.transform(TransformWorks.of(checkoutDir, "testmsg", console));
    assertThatPath(checkoutDir)
        .containsFile("test.txt", "bar\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testPathStrip() throws Exception {
    patchingOptions.skipVersionCheck = true;
    Files.write(checkoutDir.resolve("test.txt"), "foo\n".getBytes(UTF_8));
    skylark.addConfigFile("diff.patch",       ""
        + "diff --git test.txt test.txt\n"
        + "index 257cc56..5716ca5 100644\n"
        + "--- test.txt\n"
        + "+++ test.txt\n"
        + "@@ -1 +1 @@\n"
        + "-foo\n"
        + "+bar\n");
    PatchTransformation transformation =
        skylark.eval("r",
            "r = patch.apply(\n"
                + "  patches = ['diff.patch'],\n"
                + "  strip = 0,\n"
                + ")\n");
    transformation.transform(TransformWorks.of(checkoutDir, "testmsg", console));
    assertThatPath(checkoutDir)
        .containsFile("test.txt", "bar\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testParseSkylarkSeries() throws Exception {
    Files.write(checkoutDir.resolve("test.txt"), "foo\n".getBytes(UTF_8));
    skylark.addConfigFile("diff.patch", patchFile.readContent());
    skylark.addConfigFile("series", seriesFile.readContent());
    PatchTransformation transformation =
        skylark.eval("r",
            "r = patch.apply(\n"
                + "  series = 'series',\n"
                + "  excluded_patch_paths = ['excluded/*'],\n"
                + ")\n");
    transformation.transform(TransformWorks.of(checkoutDir, "testmsg", console));
    assertThatPath(checkoutDir)
        .containsFile("test.txt", "bar\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testDescribe() {
    PatchTransformation transform = new PatchTransformation(
        ImmutableList.of(patchFile, patchFile), excludedFromPatch, patchingOptions,
        /*reverse=*/ false, /*strip=*/1, Location.BUILTIN);
    assertThat(transform.describe()).isEqualTo("Patch.apply: diff.patch, diff.patch");
  }
}
