/*
 * Copyright (C) 2024 Google LLC.
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

import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.RepoException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import net.starlark.java.eval.StarlarkList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SequenceGlobTest {
  private Path workdir;

  @Before
  public void setup() throws IOException, RepoException {
    workdir = Files.createTempDirectory("workdir");
  }

  @Test
  public void testRoots() throws Exception {
    Glob glob =
        SequenceGlob.ofStarlarkList(getAtomList(ImmutableList.of("foo/bar.jar", "foo/baz")));
    assertThat(glob.roots()).containsExactly("foo");
  }

  @Test
  public void testTips() throws Exception {
    Glob glob =
        SequenceGlob.ofStarlarkList(getAtomList(ImmutableList.of("foo/bar", "foo/bar/baz")));
    assertThat(glob.tips()).containsExactly("foo/bar");
  }

  @Test
  public void testPathMatcherEquivalenceWithGlob() throws Exception {
    // A SequenceGlob's PathMatcher should behave like a regular Glob PathMatcher.
    PathMatcher sequencePathMatcher =
        SequenceGlob.ofStarlarkList(getAtomList(ImmutableList.of("foo/bar", "foo/baz")))
            .relativeTo(workdir);
    PathMatcher globPathMatcher =
        Glob.createGlob(ImmutableList.of("foo/bar", "foo/baz")).relativeTo(workdir);
    assertThat(sequencePathMatcher.matches(Path.of("foo/bar")))
        .isEqualTo(globPathMatcher.matches(Path.of("foo/bar")));
    assertThat(sequencePathMatcher.matches(Path.of("foo/baz")))
        .isEqualTo(globPathMatcher.matches(Path.of("foo/baz")));
    assertThat(sequencePathMatcher.matches(Path.of("foo/bat")))
        .isEqualTo(globPathMatcher.matches(Path.of("foo/bat")));
  }

  @Test
  public void testPathMatcherEquivalenceWithGlob_emptyRoot() throws Exception {
    // A SequenceGlob's PathMatcher should behave like a regular Glob PathMatcher.
    PathMatcher sequencePathMatcher =
        SequenceGlob.ofStarlarkList(getAtomList(ImmutableList.of("foo/bar", "foo/baz")))
            .relativeTo(Path.of(""));
    PathMatcher globPathMatcher =
        Glob.createGlob(ImmutableList.of("foo/bar", "foo/baz")).relativeTo(Path.of(""));
    assertThat(sequencePathMatcher.matches(Path.of("/foo/bar")))
        .isEqualTo(globPathMatcher.matches(Path.of("/foo/bar")));
    assertThat(sequencePathMatcher.matches(Path.of("/foo/baz")))
        .isEqualTo(globPathMatcher.matches(Path.of("/foo/baz")));
    assertThat(sequencePathMatcher.matches(Path.of("foo/bar")))
        .isEqualTo(globPathMatcher.matches(Path.of("foo/bar")));
    assertThat(sequencePathMatcher.matches(Path.of("foo/baz")))
        .isEqualTo(globPathMatcher.matches(Path.of("foo/baz")));
  }

  @Test
  public void testRelativeTo() throws Exception {
    PathMatcher matcher =
        SequenceGlob.ofStarlarkList(getAtomList(ImmutableList.of("foo", "bar")))
            .relativeTo(workdir);
    assertThat(matcher.matches(Path.of(workdir.toString(), "foo"))).isTrue();
    assertThat(matcher.matches(Path.of(workdir.toString(), "bar"))).isTrue();
    assertThat(matcher.matches(Path.of(workdir.toString(), "baz"))).isFalse();
    assertThat(matcher.matches(Path.of("foo"))).isFalse();
  }

  private StarlarkList<String> getAtomList(ImmutableList<String> paths) {
    return StarlarkList.immutableCopyOf(paths);
  }
}
