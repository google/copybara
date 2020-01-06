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

package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.Maps;
import java.nio.file.FileSystems;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RefspecTest {
  @Test
  public void testRefspec() throws Exception {
    assertRefspec(refspec("refs/heads/master"),
        "refs/heads/master", "refs/heads/master", /*expectForce=*/false);
  }

  @Test
  public void testRefspecWithSemicolon() throws Exception {
    assertRefspec(refspec("refs/heads/master:refs/heads/foo"),
        "refs/heads/master", "refs/heads/foo", /*expectForce=*/false);
  }

  @Test
  public void testRefspecWithSemicolonForce() throws Exception {
    assertRefspec(refspec("+refs/heads/master:refs/heads/foo"),
        "refs/heads/master", "refs/heads/foo", /*expectForce=*/true);
  }

  @Test
  public void testWildcard() throws Exception {
    assertRefspec(refspec("refs/heads/*:refs/heads/*"),
        "refs/heads/*", "refs/heads/*", /*expectForce=*/false);
  }

  @Test
  public void testCreateBuiltin() throws Exception {
    assertRefspec(Refspec.createBuiltin(
        getGitEnv(), FileSystems.getDefault().getPath("/"), "refs/heads/master"),
                  "refs/heads/master", "refs/heads/master", /*expectForce=*/false);
  }

  @Test
  public void testInvert() throws Exception {
    assertRefspec(refspec("refs/heads/master:refs/heads/foo").invert(),
        "refs/heads/foo", "refs/heads/master", /*expectForce=*/false);
  }

  @Test
  public void testOriginToOrigin() throws Exception {
    assertRefspec(refspec("refs/heads/master:refs/heads/foo").originToOrigin(),
        "refs/heads/master", "refs/heads/master", /*expectForce=*/false);
  }

  @Test
  public void testDestinationToDestination() throws Exception {
    assertRefspec(refspec("refs/heads/master:refs/heads/foo").destinationToDestination(),
        "refs/heads/foo", "refs/heads/foo", /*expectForce=*/false);
  }

  @Test
  public void testMatchesOrigin() throws Exception {
    assertThat(refspec("refs/heads/master").matchesOrigin("refs/heads/master")).isTrue();
    assertThat(refspec("refs/*/master").matchesOrigin("refs/heads/master")).isTrue();
    assertThat(refspec("refs/*/master").matchesOrigin("refs/heads/mistress")).isFalse();
    assertThat(refspec("refs/heads/*").matchesOrigin("refs/heads/master")).isTrue();
    assertThat(refspec("refs/heads/*").matchesOrigin("refs/tails/master")).isFalse();
  }

  @Test
  public void testNonValid() throws Exception {
    InvalidRefspecException thrown =
        assertThrows(InvalidRefspecException.class, () -> refspec("aa bb"));
    assertThat(thrown).hasMessageThat().contains("Invalid refspec: aa bb");
  }

  @Test
  public void testTwoWildcards() throws Exception {
    InvalidRefspecException thrown =
        assertThrows(InvalidRefspecException.class, () -> refspec("refs/foo/*/bar/*"));
    assertThat(thrown).hasMessageThat().contains("Invalid refspec: refs/foo/*/bar/*");
  }

  @Test
  public void testOnlyWildcardInOrigin() throws Exception {
    InvalidRefspecException thrown =
        assertThrows(InvalidRefspecException.class, () -> refspec("refs/*:refs/bar"));
    assertThat(thrown).hasMessageThat().contains("Wildcard only used in one part of the refspec");
  }

  @Test
  public void testMultipleSemicolons() throws Exception {
    InvalidRefspecException thrown =
        assertThrows(InvalidRefspecException.class, () -> refspec("la:la:la"));
    assertThat(thrown).hasMessageThat().contains("Multiple ':' found");
  }

  @Test
  public void convertTest() throws Exception {
    checkConvert("refs/foo/bar", "refs/foo/bar", "refs/foo/bar");
    checkConvert("refs/foo:refs/bar", "refs/foo", "refs/bar");
    checkConvert("refs/heads/*:refs/heads/*", "refs/heads/master", "refs/heads/master");
    checkConvert("refs/heads/*:refs/origin/heads/*", "refs/heads/master",
        "refs/origin/heads/master");
    checkConvert("*/heads/master:*/origin/heads/master", "refs/heads/master",
        "refs/origin/heads/master");
    checkConvert("refs/*/master:refs/origin/*/master", "refs/heads/master",
        "refs/origin/heads/master");
  }

  @Test
  public void testGitBinaryNotFound() {
    Map<String, String> env = Maps.newHashMap(getGitEnv().getEnvironment());
    env.put("GIT_EXEC_PATH", "some_non_existent_path");
    InvalidRefspecException e =
        assertThrows(
            InvalidRefspecException.class,
            () ->
                Refspec.create(
                    new GitEnvironment(env), FileSystems.getDefault().getPath("/"), "master"));
    assertThat(e.getMessage()).contains("Cannot find git binary at 'some_non_existent_path/git'");
  }

  private void checkConvert(String refspec, String ref, String expectedDest) throws Exception {
    assertThat(refspec(refspec).convert(ref)).isEqualTo(expectedDest);
  }

  private void assertRefspec(Refspec refspec, String expectedOrigin, String expectedDest,
      boolean expectForce) {
    assertThat(refspec.getOrigin()).isEqualTo(expectedOrigin);
    assertThat(refspec.getDestination()).isEqualTo(expectedDest);
    assertThat(refspec.isAllowNoFastForward()).isEqualTo(expectForce);
  }

  private Refspec refspec(String str) throws InvalidRefspecException {
    return Refspec.create(getGitEnv(), FileSystems.getDefault().getPath("/"), str
        /*location=*/);
  }
}
