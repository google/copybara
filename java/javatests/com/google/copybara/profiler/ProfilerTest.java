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

package com.google.copybara.profiler;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.FakeTicker;
import com.google.common.testing.TestLogHandler;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.testing.profiler.RecordingListener;
import com.google.copybara.testing.profiler.RecordingListener.EventType;
import com.google.copybara.testing.profiler.RecordingListener.TaskWithType;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogRecord;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProfilerTest {

  private Profiler profiler;
  private RecordingListener recordingCallback;
  private FakeTicker ticker;

  @Before
  public void setUp() throws Exception {
    ticker = new FakeTicker().setAutoIncrementStep(1, TimeUnit.NANOSECONDS);
    profiler = new Profiler(ticker);
    recordingCallback = new RecordingListener();
    // We don't record anything  before start
    try (ProfilerTask ignore = profiler.start("bar")) {
      profiler.simpleTask("foo", 10, 20);
    }
    assertThat(recordingCallback.events).isEmpty();
    profiler.init(ImmutableList.of(recordingCallback));

  }

  @Test
  public void reentrantTest() {
    try (ProfilerTask ignore = profiler.start("task1")) {
      try (ProfilerTask ignore2 = profiler.start("task2")) {
        profiler.simpleTask("task3", ticker.read(), ticker.read());
        profiler.simpleTask("task4", ticker.read(), ticker.read());
      }
      try (ProfilerTask ignore2 = profiler.start("task5")) {
        profiler.simpleTask("task6", ticker.read(), ticker.read());
        profiler.simpleTask("task7", ticker.read(), ticker.read());
      }
    }
    profiler.stop();

    assertThat(recordingCallback.events).isEqualTo(ImmutableList.of(
        new TaskWithType(EventType.START, new Task("//copybara", 0, -1)),
        new TaskWithType(EventType.START, new Task("//copybara/task1", 1, -1)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task2", 2, -1)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task2/task3", 3, -1)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task2/task3", 3, 4)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task2/task4", 5, -1)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task2/task4", 5, 6)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task2", 2, 7)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task5", 8, -1)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task5/task6", 9, -1)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task5/task6", 9, 10)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task5/task7", 11, -1)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task5/task7", 11, 12)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task5", 8, 13)),
        new TaskWithType(EventType.END, new Task("//copybara/task1", 1, 14)),
        new TaskWithType(EventType.END, new Task("//copybara", 0, 15))));

    // We don't record events once stopped.
    recordingCallback.events.clear();
    try (ProfilerTask ignore = profiler.start("bar")) {
      profiler.simpleTask("foo", 10, 20);
    }
    assertThat(recordingCallback.events).isEmpty();
  }

  @Test
  public void testThreadCreatedInRootListener() {
    profiler = new Profiler(ticker);
    profiler.init(ImmutableList.of(new Listener() {
      @Override
      public void taskStarted(Task task) {

      }

      @Override
      public void taskFinished(Task task) {
        if (task.isFinished() && task.getDescription().equals("//copybara")) {
          // This thread inherits from the profiler thread.
          Thread unused = new Thread();
        }
      }
    }));
    profiler.simpleTask("task1", ticker.read(), ticker.read());
    profiler.stop();
  }

  @Test
  public void multiThreadTest() {
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    try (ProfilerTask ignore = profiler.start("task1")) {
      // Sequence the two invocations to have repetible tests
      CountDownLatch latch = new CountDownLatch(1);
      executorService.submit(() -> {
        try (ProfilerTask ignored = profiler.start("task2")) {
          profiler.simpleTask("task3", ticker.read(), ticker.read());
          profiler.simpleTask("task4", ticker.read(), ticker.read());
        }
        latch.countDown();
      });
      executorService.submit(() -> {
        try {
          latch.await();
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        }
        try (ProfilerTask ignored = profiler.start("task5")) {
          profiler.simpleTask("task6", ticker.read(), ticker.read());
          profiler.simpleTask("task7", ticker.read(), ticker.read());
        }
      });
      MoreExecutors.shutdownAndAwaitTermination(executorService, 20, TimeUnit.SECONDS);
    }

    profiler.stop();

    assertThat(recordingCallback.events).containsExactly(
        new TaskWithType(EventType.START, new Task("//copybara", 0, -1)),
        new TaskWithType(EventType.START, new Task("//copybara/task1", 1, -1)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task2", 2, -1)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task2/task3", 3, -1)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task2/task3", 3, 4)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task2/task4", 5, -1)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task2/task4", 5, 6)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task2", 2, 7)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task5", 8, -1)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task5/task6", 9, -1)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task5/task6", 9, 10)),
        new TaskWithType(EventType.START, new Task("//copybara/task1/task5/task7", 11, -1)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task5/task7", 11, 12)),
        new TaskWithType(EventType.END, new Task("//copybara/task1/task5", 8, 13)),
        new TaskWithType(EventType.END, new Task("//copybara/task1", 1, 14)),
        new TaskWithType(EventType.END, new Task("//copybara", 0, 15)));
  }

  @Test
  public void testNoCallback() {
    Profiler profiler = new Profiler(ticker);
    profiler.init(ImmutableList.of());
    try (ProfilerTask ignore = profiler.start("bar")) {
      profiler.simpleTask("foo", 10, 20);
    }
    profiler.stop();

     long time = ticker.advance(42).read();
    // Nothing was created in the stack. We cannot get the queue without forcing initialization
    // so we change the ticker and see that the value is a detached with start=42. IOW: Created
    // when we do tasQueue.get() here:
    assertThat(profiler.taskQueue.get()).containsExactly(
        new Task("//detached_thread", time + 1, -1));
  }

  @Test
  public void testListenerLogging() {
    // Keep a strong reference to the Logger we use to capture log records for the duration of the
    // test to avoid any potential race conditions with the weak referencing used by the JDK
    //
    // TODO: This could be migrated to use the package level logger name, which would be less
    // fragile against code being moved around.
    Logger loggerUnderTest = Logger.getLogger(LogProfilerListener.class.getCanonicalName());

    TestLogHandler assertingHandler = new TestLogHandler();
    assertingHandler.setLevel(Level.FINE);
    loggerUnderTest.addHandler(assertingHandler);

    profiler = new Profiler(ticker);
    profiler.init(ImmutableList.of(new LogProfilerListener()));
    try (ProfilerTask ignore = profiler.start("testListenerLogging")) {
      // do something
    }
    LogRecord record = assertingHandler.getStoredLogRecords().get(0);
    assertThat(record.getMessage()).contains("testListenerLogging");
    assertThat(record.getSourceClassName()).contains(this.getClass().getName());

    loggerUnderTest.removeHandler(assertingHandler);
  }

  @Test
  public void testTaskType() {
    assertThat(profiler.taskType("profiler_test"))
        .isEqualTo(ImmutableMap.of("type", "profiler_test"));
  }
}
