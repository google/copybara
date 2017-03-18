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

package com.google.copybara.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.util.Glob.createGlob;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GlobTest {

  private Path workdir;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException, RepoException {
    workdir = Files.createTempDirectory("workdir");
    OptionsBuilder options = new OptionsBuilder()
        .setWorkdirToRealTempDir()
        .setConsole(new TestingConsole());
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void emptyIncludeExcludesEverything() throws Exception {
    PathMatcher matcher = createPathMatcher("glob([], exclude = ['foo'])");
    assertThat(matcher.matches(workdir.resolve("foo"))).isFalse();
    assertThat(matcher.matches(workdir.resolve("bar/foo"))).isFalse();
    assertThat(matcher.matches(workdir.resolve("baz"))).isFalse();
  }

  @Test
  public void unionTest() throws Exception {
    Glob glob = parseGlob("glob(['foo/**', 'bar/**']) + glob(['baz/**'])");

    assertThat(glob.roots()).containsExactly("foo", "bar", "baz");

    PathMatcher matcher = glob.relativeTo(workdir);

    assertThat(matcher.matches(workdir.resolve("foo/a"))).isTrue();
    assertThat(matcher.matches(workdir.resolve("bar/a"))).isTrue();
    assertThat(matcher.matches(workdir.resolve("baz/a"))).isTrue();
    assertThat(matcher.matches(workdir.resolve("other"))).isFalse();
  }

  @Test
  public void unionSameParent() throws Exception {
    Glob glob = parseGlob("glob(['foo/**']) + glob(['foo/bar/**', 'baz/**'])");
    // 'foo/bar' is not a root as it is not in glob(['foo/**'], 'foo/bar/**', 'baz/**'])
    assertThat(glob.roots()).containsExactly("foo", "baz");
  }

  @Test
  public void unionWithExcludeAndInclude() throws Exception {
    Glob glob = parseGlob("glob(['foo/**'], exclude = ['foo/bar/**'])"
        + " + glob(['foo/bar/baz/**'])");

    assertThat(glob.roots()).containsExactly("foo");

    PathMatcher matcher = glob.relativeTo(workdir);

    assertThat(matcher.matches(workdir.resolve("foo/a"))).isTrue();
    assertThat(matcher.matches(workdir.resolve("foo/bar/a"))).isFalse();
    assertThat(matcher.matches(workdir.resolve("foo/bar/baz/a"))).isTrue();
  }

  @Test
  public void errorForMissingInclude() throws Exception {
    skylark.evalFails("glob(exclude = ['foo'])", "missing mandatory positional argument 'include'");
    skylark.evalFails("glob()", "missing mandatory positional argument 'include'");
  }

  @Test
  public void errorForNotNamingExclude() throws Exception {
    skylark.evalFails("glob(['bar/*'], ['bar/foo'])", "too many [(]2[)] positional arguments");
  }

  @Test
  public void errorForEmptyIncludePath() throws Exception {
    skylark.evalFails("glob([''])", "unexpected empty string in glob list");
  }

  @Test
  public void errorForEmptyExcludePath() throws Exception {
    skylark.evalFails("glob(['foo'], exclude = [''])", "unexpected empty string in glob list");
  }

  @Test
  public void testSimpleInclude()
      throws IOException, ValidationException, RepoException {
    PathMatcher pathMatcher = createPathMatcher("glob(['foo','bar'])");
    assertThat(pathMatcher.matches(workdir.resolve("foo"))).isTrue();
    assertThat(pathMatcher.matches(workdir.resolve("bar"))).isTrue();
    assertThat(pathMatcher.matches(workdir.resolve("baz"))).isFalse();
  }

  @Test
  public void testSimpleIncludeWithExclusion()
      throws IOException, ValidationException, RepoException {
    PathMatcher pathMatcher = createPathMatcher("glob(['b*','foo'], exclude =['baz'])");
    assertThat(pathMatcher.matches(workdir.resolve("foo"))).isTrue();
    assertThat(pathMatcher.matches(workdir.resolve("bar"))).isTrue();
    assertThat(pathMatcher.matches(workdir.resolve("baz"))).isFalse();
  }

  @Test
  public void testWithDirs()
      throws IOException, ValidationException, RepoException {
    PathMatcher pathMatcher = createPathMatcher(""
        + "glob(\n"
        + "  include = ['**/*.java'], \n"
        + "  exclude = ['**/Generated*.java']\n"
        + ")");
    assertThat(pathMatcher.matches(workdir.resolve("foo/Some.java"))).isTrue();
    assertThat(pathMatcher.matches(workdir.resolve("foo/GeneratedSome.java"))).isFalse();
    // Doesn't match because '**/' matches when there is already one directory segment.
    assertThat(pathMatcher.matches(workdir.resolve("Some.java"))).isFalse();
    assertThat(pathMatcher.matches(workdir.resolve("GeneratedSome.java"))).isFalse();
  }

  @Test
  public void testRoots() {
    assertThat(createGlob(ImmutableList.<String>of()).roots())
        .isEmpty();
    assertThat(createGlob(ImmutableList.of("**")).roots())
        .containsExactly("");
    assertThat(createGlob(ImmutableList.of("foo/**")).roots())
        .containsExactly("foo");
    assertThat(createGlob(ImmutableList.of("foo/**", "**")).roots())
        .containsExactly("");
    assertThat(createGlob(ImmutableList.of("foo/*.java")).roots())
        .containsExactly("foo");

    // If we include a single file in root, then the all-encompassing root list must include the
    // repo root.
    assertThat(createGlob(ImmutableList.of("foo")).roots())
        .containsExactly("");
  }

  @Test
  public void testRoots_prunesMultipleSegments() {
    assertThat(createGlob(ImmutableList.of("foo/*/bar")).roots())
        .containsExactly("foo");
  }

  @Test
  public void testRoots_understandsEscaping() {
    assertThat(createGlob(ImmutableList.of("foo\\*/*.java")).roots())
        .containsExactly("foo*");
    assertThat(createGlob(ImmutableList.of("foo\\*/bar")).roots())
        .containsExactly("foo*");
    assertThat(createGlob(ImmutableList.of("foo\\{/bar")).roots())
        .containsExactly("foo{");
  }

  @Test
  public void testRoots_obscureMeta() {
    assertThat(createGlob(ImmutableList.of("foo/bar{baz/baz}")).roots())
        .containsExactly("foo");
    assertThat(createGlob(ImmutableList.of("baz/bar[az]/etc")).roots())
        .containsExactly("baz");
    assertThat(createGlob(ImmutableList.of("baz/bar.???/etc")).roots())
        .containsExactly("baz");
  }

  @Test
  public void testRoots_mergeRedundant() {
    assertThat(createGlob(ImmutableList.of("foo/bar/baz", "foo/bar")).roots())
        .containsExactly("foo");
    assertThat(createGlob(ImmutableList.of("foo/bar/baz", "foo/bar/mer")).roots())
        .containsExactly("foo/bar");
    assertThat(createGlob(ImmutableList.of("foo/bar/bag", "foo/bar/baz", "foo/bar")).roots())
        .containsExactly("foo");
    assertThat(createGlob(ImmutableList.of("foo/barbar/mer", "foo/bar/mer")).roots())
        .containsExactly("foo/bar", "foo/barbar");
  }

  @Test
  public void windowsGlobWorks() throws Exception {
    FileSystem workFs = Jimfs.newFileSystem(Configuration.windows());
    workdir = workFs.getPath("c:/tmp");
    Path folder = workdir.resolve("foo");
    Files.createDirectories(folder);
    Files.write(folder.resolve("bar"), new byte[0]);

    PathMatcher matcher = createPathMatcher("glob(['foo/**'])");
    assertThat(matcher.matches(workdir.resolve("foo/bar"))).isTrue();
  }

  private PathMatcher createPathMatcher(final String expression)
      throws ValidationException {
    return parseGlob(expression).relativeTo(workdir);
  }

  private Glob parseGlob(String expression) throws ValidationException {
    return skylark.eval("result", "result=" + expression);
  }
}
