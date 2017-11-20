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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.util.console.LogConsole;
import java.nio.file.FileSystems;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GerritOptionsTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private GerritOptions options;
  private JCommander jcommander;

  @Before
  public void setup() {
    Supplier<GeneralOptions> generalOptionsSupplier = Suppliers.ofInstance(new GeneralOptions(
        FileSystems.getDefault(), /*verbose=*/true,
        LogConsole.writeOnlyConsole(System.out, /*verbose=*/true)));
    options = new GerritOptions(generalOptionsSupplier, new GitOptions(generalOptionsSupplier));
    jcommander = new JCommander(ImmutableList.of(options));
  }

  @Test
  public void validChangeId() {
    jcommander.parse("--gerrit-change-id=I0123456789deadbeefbc0123456789deadbeefbc");

    assertThat(options.gerritChangeId)
        .isEqualTo("I0123456789deadbeefbc0123456789deadbeefbc");
  }

  @Test
  public void invalidTooShort() {
    thrown.expect(ParameterException.class);
    thrown.expectMessage("'I1111' does not match Gerrit Change ID pattern");
    jcommander.parse("--gerrit-change-id=I1111");
  }

  @Test
  public void invalidMissingIPrefix() {
    thrown.expect(ParameterException.class);
    thrown.expectMessage(
        "'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' does not match Gerrit Change ID pattern");
    jcommander.parse("--gerrit-change-id=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  }

  @Test
  public void invalidUsesCapitalHex() {
    thrown.expect(ParameterException.class);
    thrown.expectMessage(
        "'I0123456789DEADBEEFBC0123456789DEADBEEFBC' does not match Gerrit Change ID pattern");
    jcommander.parse("--gerrit-change-id=I0123456789DEADBEEFBC0123456789DEADBEEFBC");
  }
}
