/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.configgen;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.configgen.ConfigGenHeuristics.GeneratorMove;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConfigGenHeuristicsTest {
  private Path origin;
  private Path destination;
  private ImmutableSet<Path> destinationOnlyPaths;
  private OptionsBuilder optionsBuilder;

  @Before
  public void setup() throws IOException {
    origin = Files.createTempDirectory("origin");
    destination = Files.createTempDirectory("destination");
    destinationOnlyPaths = ImmutableSet.of();
    optionsBuilder = new OptionsBuilder();
  }

  @Test
  public void testSimple() throws IOException {
    writeFile(origin, "a/b/c/fileA", "foo");
    writeFile(origin, "a/b/c/fileB", "ignore");
    writeFile(destination, "x/y/z/fileB", "foo");
    ConfigGenHeuristics.Result result = createHeuristics().run();

    globMatches(result.getOriginGlob(), "a/b/c/fileA");
    globNoMatch(result.getOriginGlob(), "a/b/c/fileB");

    // We generated glob(["**", exclude = "a/b/c/fileB"])
    globMatches(result.getOriginGlob(), "a/b/c/fileC");
    assertThat(result.getOriginGlob().roots()).containsExactly("");
  }

  @Test
  public void testRename() throws IOException {
    writeFile(origin, "fileA", "aaa\n".repeat(20));
    writeFile(destination, "fileB", "aaa\n".repeat(20) + "CHANGED");
    ConfigGenHeuristics.Result result = createHeuristics().run();

    globMatches(result.getOriginGlob(), "a/b/c/fileA");
  }

  @Test
  public void testIgnoreCarriageReturn() throws IOException {
    writeFile(origin, "fileA", "aaa\n".repeat(20));
    writeFile(destination, "fileB", "aaa\r\n".repeat(20) + "CHANGED");
    ConfigGenHeuristics.Result result =
        createHeuristics(
                /* ignoreCarriageReturn= */ true, /* ignoreWhitespace= */ false, ImmutableList.of())
            .run();

    globMatches(result.getOriginGlob(), "a/b/c/fileA");
  }

  @Test
  public void testIgnoreWhitespace() throws IOException {
    writeFile(origin, "fileA", "aaa                 \n".repeat(20));
    writeFile(destination, "fileB", "aaa \n".repeat(20) + "CHANGED");
    ConfigGenHeuristics.Result result =
        createHeuristics(
                /* ignoreCarriageReturn= */ false, /* ignoreWhitespace= */ true, ImmutableList.of())
            .run();

    globMatches(result.getOriginGlob(), "a/b/c/fileA");
  }

  @Test
  public void testLineDifference() throws IOException {
    writeFile(origin, "fileA", "aaa\n".repeat(20));
    writeFile(destination, "fileB", "aaa\n".repeat(20) + "CHANGED");
    ConfigGenHeuristics.Result result = createHeuristics().run();

    globMatches(result.getOriginGlob(), "a/b/c/fileA");
  }

  @Test
  public void testRenameDestinationOnlyPaths() throws IOException {
    writeFile(origin, "fileA", "aaa\n".repeat(20));
    writeFile(destination, "fileB", "aaa\n".repeat(20) + "CHANGED");
    destinationOnlyPaths = ImmutableSet.of(Paths.get("fileB"));
    ConfigGenHeuristics.Result result = createHeuristics().run();
    // Since the destination file is only destination, it means we don't move the file
    globNoMatch(result.getOriginGlob(), "a/b/c/fileA");
  }

  @Test
  public void testExcludes() throws IOException {
    writeFile(origin, "a/b/include/fileA", "foo1");
    writeFile(origin, "a/b/include/fileB", "foo2");
    writeFile(origin, "a/b/include/fileC", "foo3");
    writeFile(origin, "a/b/exclude/fileA", "bar1");
    writeFile(origin, "a/b/exclude/fileB", "bar2");
    writeFile(origin, "a/b/exclude/fileC", "bar3");

    writeFile(destination, "x/y/z/fileX", "foo1");
    writeFile(destination, "x/y/z/fileY", "foo2");
    writeFile(destination, "x/y/z/fileZ", "foo3");

    ConfigGenHeuristics.Result result = createHeuristics().run();

    globMatches(result.getOriginGlob(), "a/b/include/fileA");
    globMatches(result.getOriginGlob(), "a/b/include/fileB");
    globMatches(result.getOriginGlob(), "a/b/include/fileC");

    globNoMatch(result.getOriginGlob(), "a/b/exclude/fileA");
    globNoMatch(result.getOriginGlob(), "a/b/exclude/fileB");
    globNoMatch(result.getOriginGlob(), "a/b/exclude/fileC");

    globMatches(result.getOriginGlob(), "a/b/include/fileAnything");
    globNoMatch(result.getOriginGlob(), "a/b/exclude/fileAnything");

    assertThat(result.getOriginGlob().roots()).containsExactly("");
    globMatches(result.getOriginGlob(), "a/other");
  }

  @Test
  public void testMapDestinationFileToOriginFileHighestScore() throws IOException {
    writeFile(origin, "a/b/c/fileA", "foo\nbar\na");
    writeFile(origin, "a/b/c/fileB", "foo\nbar\nb");
    writeFile(origin, "a/b/c/fileC", "foo\nbar\nc");
    writeFile(destination, "x/y/z/fileD", "foo\nbar\nc");
    ConfigGenHeuristics.Result result = createHeuristics().run();

    // We generated glob(["**", exclude = ["a/b/c/fileA", "a/b/c/fileB"])
    globMatches(result.getOriginGlob(), "a/b/c/fileC");
    globNoMatch(result.getOriginGlob(), "a/b/c/fileB");
    globNoMatch(result.getOriginGlob(), "a/b/c/fileA");
  }

  @Test
  public void testMoves() throws IOException {
    writeFile(origin, "a/b/include/fileA", "foo1");
    writeFile(origin, "a/b/include/fileB", "foo2");
    writeFile(origin, "c/X", "XXX");

    writeFile(origin, "a/b/include2/test/fileA", "bar1");
    writeFile(origin, "a/b/include2/test/fileB", "bar2");
    writeFile(origin, "a/b/include2/fileC", "bar3");

    writeFile(destination, "x/y/z/fileA", "foo1");
    writeFile(destination, "x/y/z/fileB", "foo2");
    writeFile(destination, "c/Y", "XXX");

    writeFile(destination, "x/y/include2/test/fileA", "bar1");
    writeFile(destination, "x/y/include2/test/fileB", "bar2");
    writeFile(destination, "x/y/include3/fileC", "bar3");

    ConfigGenHeuristics.Result result = createHeuristics().run();
    assertThat(result.getTransformations().getMoves()).containsExactly(
        new GeneratorMove("a/b/include", "x/y/z"),
        new GeneratorMove("a/b/include2/test", "x/y/include2/test"),
        new GeneratorMove("a/b/include2/fileC", "x/y/include3/fileC"),
        new GeneratorMove("c/X", "c/Y")
    );
  }

  @Test
  public void moves_areReturnedInCorrectOrder() throws IOException {
    writeFile(origin, "fileA", "foo1");
    writeFile(origin, "fileB", "foo2");
    writeFile(origin, "package/fileC", "foo3");
    writeFile(origin, "res/LICENSE/fileD", "XXX");
    writeFile(destination, "src/main/fileA", "foo1");
    writeFile(destination, "src/main/fileB", "foo2");
    writeFile(destination, "src/main/package/fileC", "foo3");
    writeFile(destination, "LICENSE", "XXX");

    ConfigGenHeuristics.Result result = createHeuristics().run();

    assertThat(result.getTransformations().getMoves())
        .containsExactly(
            new GeneratorMove("res/LICENSE/fileD", "LICENSE"), new GeneratorMove("", "src/main"))
        .inOrder();
  }

  @Test
  public void testNonEmptyIncludes() throws IOException {
    writeFile(origin, "a/b/include/fileA", "foo1");
    writeFile(origin, "a/b/include/fileB", "foo2");
    writeFile(origin, "a/b/include/fileC", "foo3");

    for (int i = 0; i < 100; i++) {
      writeFile(origin, "a/excluded" + i, "exclude" + i);
    }

    writeFile(destination, "x/y/z/fileX", "foo1");
    writeFile(destination, "x/y/z/fileY", "foo2");
    writeFile(destination, "x/y/z/fileZ", "foo3");

    ConfigGenHeuristics.Result result = createHeuristics().run();

    globMatches(result.getOriginGlob(), "a/b/include/fileA");
    globMatches(result.getOriginGlob(), "a/b/include/fileB");
    globMatches(result.getOriginGlob(), "a/b/include/fileC");

    globNoMatch(result.getOriginGlob(), "anything");

    assertThat(result.getOriginGlob().roots()).containsExactly("a/b");
    globMatches(result.getOriginGlob(), "a/b/anything");
    globNoMatch(result.getOriginGlob(), "a/anything");
  }

  @Test
  public void testMoves_completeSuffixOverlap() throws IOException {
    writeFile(origin, "a/b/include/fileA", "foo1");
    writeFile(origin, "a/b/include/fileB", "foo2");
    writeFile(origin, "LICENSE", "foo3");

    writeFile(destination, "include/fileA", "foo1");
    writeFile(destination, "include/fileB", "foo2");
    writeFile(destination, "LICENSE", "foo3");

    ConfigGenHeuristics.Result result = createHeuristics().run();
    assertThat(result.getTransformations().getMoves())
        .containsExactly(new GeneratorMove("a/b", ""));
  }

  @Test
  public void testDestinationExcludePaths_populatedWithDestinationOnlyFile() throws IOException {
    writeFile(origin, "a/b/include/fileA", "foo1");
    writeFile(destination, "include/fileA", "foo1");
    writeFile(destination, "include/fileB", "foo2");

    ConfigGenHeuristics.Result result = createHeuristics().run();

    assertThat(result.getDestinationExcludePaths().getPaths())
        .containsExactly(Path.of("include/fileB"));
  }

  @Test
  public void testDestinationOnlyPaths_propagatedToDestinationExcludePaths() throws IOException {
    destinationOnlyPaths = ImmutableSet.of(Path.of("include/fileA"));

    ConfigGenHeuristics.Result result = createHeuristics().run();

    assertThat(result.getDestinationExcludePaths().getPaths())
        .containsExactly(Path.of("include/fileA"));
  }

  @Test
  public void getVersionStringSeparator_simple_worksCorrectly() throws IOException {
    ConfigGenHeuristics.Result result =
        createHeuristics(false, false, ImmutableList.of("1.0.1", "1.0.2")).run();

    assertThat(result.getVersionSeparator()).hasValue(".");
  }

  @Test
  public void getVersionStringSeparator_withPrefix_worksCorrectly() throws IOException {
    ConfigGenHeuristics.Result result =
        createHeuristics(false, false, ImmutableList.of("v1.0.1", "v1.0.2")).run();

    assertThat(result.getVersionSeparator()).hasValue(".");
  }

  @Test
  public void getVersionStringSeparator_underscore_worksCorrectly() throws IOException {
    ConfigGenHeuristics.Result result =
        createHeuristics(false, false, ImmutableList.of("1_0_1", "1_0_2")).run();

    assertThat(result.getVersionSeparator()).hasValue("_");
  }

  @Test
  public void getVersionStringSeparator_notFullSemVer_worksCorrectly() throws IOException {
    ConfigGenHeuristics.Result result =
        createHeuristics(false, false, ImmutableList.of("1.1", "1.2")).run();

    assertThat(result.getVersionSeparator()).hasValue(".");
  }

  @Test
  public void getVersionStringSeparator_mostCommonSeparator_worksCorrectly() throws IOException {
    ConfigGenHeuristics.Result result =
        createHeuristics(false, false, ImmutableList.of("1.0.0", "1.0.1", "main", "2_0_0", "1.0.3"))
            .run();

    assertThat(result.getVersionSeparator()).hasValue(".");
  }

  @Test
  public void getVersionStringSeparator_nonSemVer_noResult() throws IOException {
    ConfigGenHeuristics.Result result =
        createHeuristics(false, false, ImmutableList.of("versionone", "versiontwo")).run();

    assertThat(result.getVersionSeparator()).isEmpty();
  }

  @Test
  public void shouldUseVersionSelector_twoTagsWithSeparator_true() throws IOException {
    ConfigGenHeuristics.Result result =
        createHeuristics(false, false, ImmutableList.of("1.0.0", "1.0.1")).run();

    assertThat(result.getShouldUseVersionSelector()).isTrue();
  }

  @Test
  public void shouldUseVersionSelector_oneTagWithSeparator_true() throws IOException {
    ConfigGenHeuristics.Result result =
        createHeuristics(false, false, ImmutableList.of("1.0.0")).run();

    assertThat(result.getShouldUseVersionSelector()).isTrue();
  }

  @Test
  public void shouldUseVersionSelector_twoTagsWithNoSeparator_false() throws IOException {
    ConfigGenHeuristics.Result result =
        createHeuristics(false, false, ImmutableList.of("main", "stable", "experimental")).run();

    assertThat(result.getShouldUseVersionSelector()).isFalse();
  }

  @Test
  public void shouldUseVersionSelector_noTags_false() throws IOException {
    ConfigGenHeuristics.Result result = createHeuristics(false, false, ImmutableList.of()).run();

    assertThat(result.getShouldUseVersionSelector()).isFalse();
  }

  private void globMatches(Glob glob, String path) {
    Path root = Paths.get("/");
    PathMatcher matcher = glob.relativeTo(root);
    assertThat(matcher.matches(root.resolve(path))).isTrue();
  }

  private void globNoMatch(Glob glob, String path) {
    Path root = Paths.get("/");
    PathMatcher matcher = glob.relativeTo(root);
    assertThat(matcher.matches(root.resolve(path))).isFalse();
  }

  private static void writeFile(Path basePath, String relativePath, String content)
      throws IOException {
    Files.createDirectories(basePath.resolve(relativePath).getParent());
    Files.write(basePath.resolve(relativePath), content.getBytes(UTF_8));
  }

  private ConfigGenHeuristics createHeuristics() {
    return createHeuristics(
        /* ignoreCarriageReturn= */ false,
        /* ignoreWhitespace= */ false,
        /* tags= */ ImmutableList.of());
  }

  private ConfigGenHeuristics createHeuristics(
      boolean ignoreCarriageReturn, boolean ignoreWhitespace, ImmutableList<String> tags) {
    return new ConfigGenHeuristics(
        origin,
        destination,
        destinationOnlyPaths,
        30,
        ignoreCarriageReturn,
        ignoreWhitespace,
        optionsBuilder.general,
        tags);
  }
}
