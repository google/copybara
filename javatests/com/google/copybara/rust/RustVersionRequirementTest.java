/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.rust;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.rust.RustVersionRequirement.SemanticVersion;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RustVersionRequirementTest {

  @Test
  public void testSemanticVersion_simple() throws Exception{
    SemanticVersion version = SemanticVersion.createFromVersionString("1.2.3");
    assertThat(version.majorVersion()).isEqualTo(1);
    assertThat(version.minorVersion()).isEqualTo(Optional.of(2));
    assertThat(version.patchVersion()).isEqualTo(Optional.of(3));
  }

  @Test
  public void testSemanticVersion_onlyMajorVersion() throws Exception{
    SemanticVersion version = SemanticVersion.createFromVersionString("1");
    assertThat(version.majorVersion()).isEqualTo(1);
    assertThat(version.minorVersion()).isEqualTo(Optional.empty());
    assertThat(version.patchVersion()).isEqualTo(Optional.empty());
  }

  @Test
  public void testSemanticVersion_missingPatchVersion() throws Exception{
    SemanticVersion version = SemanticVersion.createFromVersionString("1.2");
    assertThat(version.majorVersion()).isEqualTo(1);
    assertThat(version.minorVersion()).isEqualTo(Optional.of(2));
    assertThat(version.patchVersion()).isEqualTo(Optional.empty());
  }

  @Test
  public void testSemanticVersion_withBuildMetadata() throws Exception{
    // The build metadata after the + sign can be safely ignored.
    SemanticVersion version = SemanticVersion.createFromVersionString("1.2.3+ignore.me.plz");
    assertThat(version.majorVersion()).isEqualTo(1);
    assertThat(version.minorVersion()).isEqualTo(Optional.of(2));
    assertThat(version.patchVersion()).isEqualTo(Optional.of(3));
  }
}
