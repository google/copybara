package com.google.copybara;
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.copybara.StarlarkDateTimeModule.StarlarkTimeDelta;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.starlark.java.eval.StarlarkInt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class StarlarkDateTimeModuleTest {

  private SkylarkTestExecutor executor;

  @Before
  public void setUp() {
    TestingConsole console = new TestingConsole();
    OptionsBuilder optionsBuilder = new OptionsBuilder().setConsole(console);
    executor = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  @SuppressWarnings("ZonedDateTimeNowWithZone")
  public void testCreateCurrentTime_validZoneId() throws Exception {
    long start = ZonedDateTime.now(ZoneId.systemDefault()).toEpochSecond();
    StarlarkInt datetime =
        executor.eval("my_datetime", "my_datetime = datetime.now().in_epoch_seconds()");
    assertThat(datetime.toLong(null)).isAtLeast(start);
    assertThat(datetime.toLong(null))
        .isAtMost(ZonedDateTime.now(ZoneId.systemDefault()).toEpochSecond());
  }

  @Test
  @SuppressWarnings("ZonedDateTimeNowWithZone")
  public void testCreateCurrentTime_invalidZoneId() throws Exception {
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                executor.eval(
                    "datetime",
                    "my_datetime = datetime.now(tz = 'New_York/Google').in_epoch_seconds()"));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "An error was thrown creating StarlarkDateTime from zone id. Make sure your timezone is"
                + " available in");
  }

  @Test
  @SuppressWarnings("GoodTime")
  public void testCreateFromTimestamp(
      @TestParameter({"1599573661", "2147483647", "-2147483648", "0", "-1"}) long epochSeconds)
      throws Exception {
    StarlarkInt datetime =
        executor.eval(
            "my_datetime",
            String.format(
                "my_datetime = datetime.fromtimestamp(timestamp = %s).in_epoch_seconds()",
                epochSeconds));
    assertThat(datetime.toLong(null)).isEqualTo(epochSeconds);
  }

  @Test
  @TestParameters({
    "{leftEpoch: 1664663728, rightEpoch: 1664663728, expectedSeconds: 0}",
    "{leftEpoch: 1600000000, rightEpoch: 1600450305, expectedSeconds: -450305}",
    "{leftEpoch: 1600450305, rightEpoch: 1600000000, expectedSeconds: 450305}",
    "{leftEpoch: 1664808418, rightEpoch: -1, expectedSeconds: 1664808419}",
    "{leftEpoch: -1, rightEpoch: 1664750128, expectedSeconds: -1664750129}"
  })
  @SuppressWarnings("GoodTime")
  public void testSecondsBetweenStarlarkDateTime(
      long leftEpoch, long rightEpoch, long expectedSeconds) throws Exception {
    StarlarkTimeDelta timeDelta =
        executor.eval(
            "timedelta",
            String.format(
                "my_datetime = datetime.fromtimestamp(timestamp = %s) \n"
                    + "my_other_datetime = datetime.fromtimestamp(timestamp = %s,"
                    + " tz = 'Australia/Broken_Hill')\n"
                    + "timedelta = (my_datetime - my_other_datetime)",
                leftEpoch, rightEpoch));
    assertThat(timeDelta.totalSeconds()).isEqualTo(expectedSeconds);
  }

  @Test
  @TestParameters({
    "{formatString: 'dd-LLL-yyyy', expectedTimeString: '06-Oct-2022'}",
    "{formatString: 'MM-dd-yyyy HH:mm:ss a VV', expectedTimeString: '10-06-2022 11:23:49 AM"
        + " America/New_York'}"
  })
  @SuppressWarnings("GoodTime")
  public void testStarlarkDateTimeStrftime(String formatString, String expectedTimeString)
      throws Exception {
    long epochSeconds = 1665069829; // Thursday, October 6, 2022 11:23:49 AM in America/New_York
    String actualTimeString =
        executor.eval(
            "time_string",
            String.format(
                "time_string = datetime.fromtimestamp(timestamp = %s, tz ="
                    + " 'America/New_York').strftime(format = '%s')",
                epochSeconds, formatString));
    assertThat(actualTimeString).isEqualTo(expectedTimeString);
  }
}
