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

import com.google.devtools.build.lib.syntax.EvalException;
import java.nio.file.FileSystems;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RefspecTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testRefspec() throws EvalException {
    assertRefspec(refspec("refs/heads/master"),
        "refs/heads/master", "refs/heads/master", /*expectForce=*/false);
  }

  @Test
  public void testRefspecWithSemicolon() throws EvalException {
    assertRefspec(refspec("refs/heads/master:refs/heads/foo"),
        "refs/heads/master", "refs/heads/foo", /*expectForce=*/false);
  }

  @Test
  public void testRefspecWithSemicolonForce() throws EvalException {
    assertRefspec(refspec("+refs/heads/master:refs/heads/foo"),
        "refs/heads/master", "refs/heads/foo", /*expectForce=*/true);
  }

  @Test
  public void testWildcard() throws EvalException {
    assertRefspec(refspec("refs/heads/*:refs/heads/*"),
        "refs/heads/*", "refs/heads/*", /*expectForce=*/false);
  }

  @Test
  public void testNonValid() throws EvalException {
    thrown.expect(EvalException.class);
    thrown.expectMessage("Invalid refspec: 1234");
    refspec("1234");
  }

  @Test
  public void testTwoWildcards() throws EvalException {
    thrown.expect(EvalException.class);
    thrown.expectMessage("Invalid refspec: refs/foo/*/bar/*");
    refspec("refs/foo/*/bar/*");
  }

  @Test
  public void testOnlyWildcardInOrigin() throws EvalException {
    thrown.expect(EvalException.class);
    thrown.expectMessage("Wilcard only used in one part of the refspec");
    refspec("refs/*:refs/bar");
  }

  @Test
  public void testMultipleSemicolons() throws EvalException {
    thrown.expect(EvalException.class);
    thrown.expectMessage("Multiple ':' found");
    refspec("la:la:la");
  }

  @Test
  public void convertTest() throws EvalException {
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

  private void checkConvert(String refspec, String ref, String expectedDest) throws EvalException {
    assertThat(refspec(refspec).convert(ref)).isEqualTo(expectedDest);
  }

  private void assertRefspec(Refspec refspec, String expectedOrigin, String expectedDest,
      boolean expectForce) {
    assertThat(refspec.getOrigin()).isEqualTo(expectedOrigin);
    assertThat(refspec.getDestination()).isEqualTo(expectedDest);
    assertThat(refspec.isAllowNoFastForward()).isEqualTo(expectForce);
  }

  private Refspec refspec(String str) throws EvalException {
    return Refspec.create(System.getenv(), FileSystems.getDefault().getPath("/"), str,
        /*location=*/null);
  }
}
