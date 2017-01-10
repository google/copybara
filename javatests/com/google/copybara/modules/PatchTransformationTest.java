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

package com.google.copybara.modules;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.GeneralOptions;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.MapConfigFile;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
  private GeneralOptions general;
  private Path checkoutDir;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  private ConfigFile<String> patchFile;
  private ImmutableList<String> excludedFromPatch = ImmutableList.of("excluded/*");

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException {
    checkoutDir =  Files.createTempDirectory("workdir");
    Files.createDirectories(checkoutDir);
    console = new TestingConsole();
    options = new OptionsBuilder().setConsole(console);
    general = options.build().get(GeneralOptions.class);
    skylark = new SkylarkTestExecutor(options, PatchModule.class);
    patchFile = new MapConfigFile(
        ImmutableMap.of("diff.patch", (DIFF).getBytes(UTF_8)), "diff.patch");
  }

  @Test
  public void applyTransformationTest() throws Exception {
    Files.write(checkoutDir.resolve("test.txt"), "foo\n".getBytes(UTF_8));
    PatchTransformation transform =
        new PatchTransformation(ImmutableList.of(patchFile), excludedFromPatch, general, false);
    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));
    assertThatPath(checkoutDir)
        .containsFile("test.txt", "bar\n")
        .containsNoMoreFiles();
  }

  @Test
  public void reverseTransformationTest() throws Exception {
    Files.write(checkoutDir.resolve("test.txt"), "bar\n".getBytes(UTF_8));
    PatchTransformation transform =
        new PatchTransformation(ImmutableList.of(patchFile), excludedFromPatch, general, true);
    transform.transform(TransformWorks.of(checkoutDir, "testmsg", console));
    assertThatPath(checkoutDir)
        .containsFile("test.txt", "foo\n")
        .containsNoMoreFiles();
  }

  @Test
  public void testParseSkylark() throws Exception {
    Files.write(checkoutDir.resolve("test.txt"), "foo\n".getBytes(UTF_8));
    skylark.addExtraConfigFile("diff.patch", new String(patchFile.content(), UTF_8));
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
  public void testDescribe() throws Exception {
    PatchTransformation transform = new PatchTransformation(
        ImmutableList.of(patchFile, patchFile), excludedFromPatch, general, false);
    assertThat(transform.describe()).isEqualTo("Patch.apply: diff.patch, diff.patch");
  }
}