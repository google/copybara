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

package com.google.copybara.util.console;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.common.testing.TestLogHandler;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FileConsoleTest {

  // Keep a strong reference so that the handler isn't lost if the Logger is garbage collected.
  private final Logger fileConsoleLogger = Logger.getLogger(FileConsole.class.getName());
  private final TestLogHandler logHandler = new TestLogHandler();

  private Path file;

  @Before
  public void setup() throws IOException {
    file = Jimfs.newFileSystem().getPath("/tmp/foo.txt");
    Files.createDirectories(file.getParent());
    fileConsoleLogger.addHandler(logHandler);
  }

  @After
  public void tearDown() {
    fileConsoleLogger.removeHandler(logHandler);
  }

  @Test
  public void testNewFile() throws IOException {
    runTest(file);
  }

  @Test
  public void testExistingFileIsTruncated() throws IOException {
    Files.write(file, "Previous content".getBytes(StandardCharsets.UTF_8));
    runTest(file);
  }

  private void runTest(Path file) throws IOException {
    TestingConsole delegate = new TestingConsole();
    try (FileConsole fileConsole = new FileConsole(delegate, file, Duration.ZERO)) {
      fileConsole.startupMessage("v1");
      fileConsole.info("This is info");
      fileConsole.warn("This is warning");
      fileConsole.error("This is error");
      fileConsole.verbose("This is verbose");
      fileConsole.progress("This is progress");
    }

    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(6);
    assertThat(lines.get(0)).contains("INFO: Copybara (Version: v1)");
    assertThat(lines.get(1)).contains("INFO: This is info");
    assertThat(lines.get(2)).contains("WARNING: This is warning");
    assertThat(lines.get(3)).contains("ERROR: This is error");
    assertThat(lines.get(4)).contains("VERBOSE: This is verbose");
    assertThat(lines.get(5)).contains("PROGRESS: This is progress");

    delegate
        .assertThat()
        .matchesNext(MessageType.INFO, "Copybara [(]Version: v1[)]")
        .matchesNext(MessageType.INFO, "This is info")
        .matchesNext(MessageType.WARNING, "This is warning")
        .matchesNext(MessageType.ERROR, "This is error")
        .matchesNext(MessageType.VERBOSE, "This is verbose")
        .matchesNext(MessageType.PROGRESS, "This is progress");
  }

  @Test
  public void testFlushingFrequency() throws Exception {
    TestingConsole delegate = new TestingConsole();
    try (FileConsole fileConsole = new FileConsole(delegate, file, Duration.ofSeconds(2))) {
      fileConsole.startupMessage("v1");
      fileConsole.info("This is info");
      fileConsole.warn("This is warning");
      assertThat(Files.readAllLines(file)).isEmpty();
      Thread.sleep(3000);
      fileConsole.error("This is error");
      fileConsole.verbose("This is verbose");
      List<String> lines = Files.readAllLines(file);
      assertThat(lines).hasSize(3);
      assertThat(lines.get(0)).contains("INFO: Copybara (Version: v1)");
      assertThat(lines.get(1)).contains("INFO: This is info");
      assertThat(lines.get(2)).contains("WARNING: This is warning");
      fileConsole.progress("This is progress");
    }

    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(6);
    assertThat(lines.get(0)).contains("INFO: Copybara (Version: v1)");
    assertThat(lines.get(1)).contains("INFO: This is info");
    assertThat(lines.get(2)).contains("WARNING: This is warning");
    assertThat(lines.get(3)).contains("ERROR: This is error");
    assertThat(lines.get(4)).contains("VERBOSE: This is verbose");
    assertThat(lines.get(5)).contains("PROGRESS: This is progress");
  }

  @Test
  public void testShutDownCase() throws Exception {
    TestingConsole delegate = new TestingConsole();
    FileConsole fileConsole = new FileConsole(delegate, file, Duration.ofSeconds(2));
    ExecutorService exec = Executors.newFixedThreadPool(2);
    Future<?> f1 = exec.submit(() -> {
      Thread.sleep(1000);
      fileConsole.close();
      return null;
    });
    Future<?> f2 = exec.submit(() -> {
      fileConsole.info("This is info");
      Thread.sleep(2000);
      fileConsole.warn("This is warning");
      return null;
    });
f1.get();
f2.get();
  }

  @Test
  public void testClose_writesAllPendingMessages() throws Exception {
    try (FileConsole fileConsole = new FileConsole(new TestingConsole(), file, Duration.ZERO)) {
      for (int i = 0; i < 1000; i++) {
        fileConsole.info("Message " + i);
      }
    }

    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(1000);
    assertThat(lines.get(0)).contains("INFO: Message 0");
    assertThat(lines.get(999)).contains("INFO: Message 999");
    assertThat(logRecordsAtOrAbove(Level.WARNING)).isEmpty();
  }

  @Test
  public void testClose_backloggedWriter_dropsPendingMessages() throws Exception {
    CountDownLatch writeStarted = new CountDownLatch(1);
    CountDownLatch releaseWrite = new CountDownLatch(1);
    // Unblocks the first write once close() has given up waiting for the pending messages.
    TestLogHandler releasingHandler =
        new TestLogHandler() {
          @Override
          public synchronized void publish(LogRecord record) {
            if (record.getLevel().equals(Level.WARNING)) {
              releaseWrite.countDown();
            }
          }
        };
    fileConsoleLogger.addHandler(releasingHandler);
    FileConsole fileConsole =
        new FileConsole(
            new TestingConsole(),
            file,
            Duration.ZERO,
            /* closeTimeout= */ Duration.ZERO,
            InstantSource.system()) {
          @Override
          Writer openWriter() throws IOException {
            return new BlockingWriter(super.openWriter(), writeStarted, releaseWrite);
          }
        };
    try {
      fileConsole.info("First");
      fileConsole.info("Second");
      fileConsole.info("Third");
      writeStarted.await();

      fileConsole.close();
    } finally {
      fileConsoleLogger.removeHandler(releasingHandler);
    }

    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(1);
    assertThat(lines.get(0)).contains("INFO: First");
    ImmutableList<LogRecord> records = logRecordsAtOrAbove(Level.WARNING);
    assertThat(records).hasSize(1);
    assertThat(records.get(0).getLevel()).isEqualTo(Level.WARNING);
    assertThat(records.get(0).getMessage()).contains("Dropped 2 pending messages");
  }

  @Test
  public void testTimestampIsWhenMessageIsLogged() throws Exception {
    Instant firstTime = Instant.parse("2026-01-02T03:04:05.006Z");
    Instant secondTime = firstTime.plusSeconds(3600);
    AtomicReference<Instant> now = new AtomicReference<>(firstTime);
    CountDownLatch writeStarted = new CountDownLatch(1);
    CountDownLatch releaseWrite = new CountDownLatch(1);
    try (FileConsole fileConsole =
        new FileConsole(
            new TestingConsole(),
            file,
            Duration.ZERO,
            /* closeTimeout= */ Duration.ofMinutes(1),
            now::get) {
          @Override
          Writer openWriter() throws IOException {
            return new BlockingWriter(super.openWriter(), writeStarted, releaseWrite);
          }
        }) {
      fileConsole.info("First");
      writeStarted.await();
      now.set(secondTime);
      fileConsole.info("Second");
      // The second message is written after this, but its timestamp is still secondTime.
      now.set(secondTime.plusSeconds(3600));
      releaseWrite.countDown();
    }

    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).startsWith(formatTimestamp(firstTime) + " INFO: First");
    assertThat(lines.get(1)).startsWith(formatTimestamp(secondTime) + " INFO: Second");
  }

  private static String formatTimestamp(Instant instant) {
    return instant
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMdd HH:mm:ss.SSS"));
  }

  private ImmutableList<LogRecord> logRecordsAtOrAbove(Level level) {
    return logHandler.getStoredLogRecords().stream()
        .filter(r -> r.getLevel().intValue() >= level.intValue())
        .collect(toImmutableList());
  }

  /** A {@link Writer} that blocks the first write until {@code release} is counted down. */
  private static class BlockingWriter extends FilterWriter {
    private final CountDownLatch started;
    private final CountDownLatch release;

    BlockingWriter(Writer out, CountDownLatch started, CountDownLatch release) {
      super(out);
      this.started = started;
      this.release = release;
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
      if (started.getCount() > 0) {
        started.countDown();
        try {
          if (!release.await(1, TimeUnit.MINUTES)) {
            throw new IOException("Timed out waiting for the write to be released");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new InterruptedIOException();
        }
      }
      super.write(str, off, len);
    }
  }

  /**
   * We only flush when the file is closed
   */
  @Test
  public void testFlushingFrequencyDeactivated() throws Exception {
    TestingConsole delegate = new TestingConsole();
    try (FileConsole fileConsole = new FileConsole(delegate, file, Duration.ZERO)) {
      fileConsole.startupMessage("v1");
      fileConsole.info("This is info");
      fileConsole.warn("This is warning");
      assertThat(Files.readAllLines(file)).isEmpty();
      Thread.sleep(3000);
      fileConsole.error("This is error");
      fileConsole.verbose("This is verbose");
      List<String> lines = Files.readAllLines(file);
      assertThat(lines).hasSize(0);
      fileConsole.progress("This is progress");
    }

    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(6);
    assertThat(lines.get(0)).contains("INFO: Copybara (Version: v1)");
    assertThat(lines.get(1)).contains("INFO: This is info");
    assertThat(lines.get(2)).contains("WARNING: This is warning");
    assertThat(lines.get(3)).contains("ERROR: This is error");
    assertThat(lines.get(4)).contains("VERBOSE: This is verbose");
    assertThat(lines.get(5)).contains("PROGRESS: This is progress");
  }

  @Test
  public void testFlushingFrequency_disabled() throws Exception {
    TestingConsole delegate = new TestingConsole();
    try (FileConsole fileConsole = new FileConsole(delegate, file, Duration.ZERO)) {
      fileConsole.startupMessage("v1");
      fileConsole.info("This is info");
      fileConsole.warn("This is warning");
      fileConsole.error("This is error");
      fileConsole.verbose("This is verbose");
      fileConsole.progress("This is progress");
      assertThat(Files.readAllLines(file)).isEmpty();
    }

    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(6);
    assertThat(lines.get(0)).contains("INFO: Copybara (Version: v1)");
    assertThat(lines.get(1)).contains("INFO: This is info");
    assertThat(lines.get(2)).contains("WARNING: This is warning");
    assertThat(lines.get(3)).contains("ERROR: This is error");
    assertThat(lines.get(4)).contains("VERBOSE: This is verbose");
    assertThat(lines.get(5)).contains("PROGRESS: This is progress");
  }
}
