/*
 * Copyright (C) 2019z Google Inc.
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

package com.google.copybara.profiler;

import static com.google.copybara.util.console.Message.MessageType.VERBOSE;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.FakeTicker;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.console.testing.TestingConsole;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConsoleProfilerListenerTest {

  private Profiler profiler;
  private FakeTicker ticker;

  @Before
  public void setUp() {
    ticker = new FakeTicker().setAutoIncrementStep(1, TimeUnit.MILLISECONDS);
    profiler = new Profiler(ticker);
  }

  @Test
  public void testConsoleProfilerListener() {
    TestingConsole console = new TestingConsole();
    profiler.init(ImmutableList.of(new ConsoleProfilerListener(console)));

    try (ProfilerTask ignore = profiler.start("iterative")) {
      ticker.advance(10, TimeUnit.MILLISECONDS);
      try (ProfilerTask ignore2 = profiler.start("origin.checkout")) {
        ticker.advance(5, TimeUnit.MILLISECONDS);
      }
      try (ProfilerTask ignore3 = profiler.start("transforms")) {
        ticker.advance(20, TimeUnit.MILLISECONDS);
      }
      try (ProfilerTask ignore4 = profiler.start("destination.write")) {
        ticker.advance(3, TimeUnit.MILLISECONDS);
      }
    }
    profiler.stop();

    console.assertThat()
        .matchesNextSkipAhead(VERBOSE, "PROFILE:.*6 //copybara/iterative/origin.checkout")
        .matchesNextSkipAhead(VERBOSE, "PROFILE:.*21 //copybara/iterative/transforms")
        .matchesNextSkipAhead(VERBOSE, "PROFILE:.*4 //copybara/iterative/destination.write")
        .matchesNextSkipAhead(VERBOSE, "PROFILE:.*45 //copybara/iterative")
        .matchesNextSkipAhead(VERBOSE, "PROFILE:.*47 //copybara");
  }

}