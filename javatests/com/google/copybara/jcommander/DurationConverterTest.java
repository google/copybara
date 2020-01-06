/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.jcommander;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.GeneralOptions;
import com.google.copybara.ModuleSupplier;
import com.google.copybara.Options;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DurationConverterTest {

  @Test
  public void testParsing() throws Exception {
    checkFetchTimeout("0s", Duration.ZERO);
    checkFetchTimeout("20s", Duration.ofSeconds(20));
    checkFetchTimeout("20m", Duration.ofMinutes(20));
    checkFetchTimeout("20h", Duration.ofHours(20));
    checkFetchTimeout("20d", Duration.ofDays(20));
    ParameterException e =
        assertThrows(ParameterException.class, () -> checkFetchTimeout("20a", Duration.ZERO));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Invalid value for duration '20a', " + "valid value examples: 10s, 10m, 10h or 10d");
  }

  private void checkFetchTimeout(String flagValue, Duration expected) throws IOException {
    ImmutableMap<String, String> envWithHome =
        ImmutableMap.of("HOME", Files.createTempDirectory("foo").toString());

    ModuleSupplier moduleSupplier = new ModuleSupplier(envWithHome, FileSystems.getDefault(),
        new TestingConsole());
    Options options = moduleSupplier.create().getOptions();
    JCommander jCommander = new JCommander(options.getAll());
    jCommander.parse("--fetch-timeout", flagValue);
    assertThat(options.get(GeneralOptions.class).fetchTimeout)
        .isEquivalentAccordingToCompareTo(expected);
  }
}
