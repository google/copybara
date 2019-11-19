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

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.copybara.util.console.Message.MessageType;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * A {@link Console} capable of writing the output into a file and to a delegate console.
 *
 * <p>If any of the file operations fail, the console won't try to write to the file anymore and
 * will only call to the delegate console.
 *
 * <p>Caller is responsible for closing this console to free resources.
 *
 * <p> The console can be configured to flush on a fixed rate intervals.
 */
public class FileConsole extends DelegateConsole {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final DateTimeFormatter DATE_PREFIX_FMT =
      DateTimeFormatter.ofPattern("MMdd HH:mm:ss.SSS");

  protected final Path filePath;
  private final ListeningScheduledExecutorService flushingExecutor;
  private final ExecutorService loggingExecutor;

  private boolean failed = false;
  @Nullable private Writer writer;


  /**
   * Creates a new {@link FileConsole}.
   *
   * @param delegate A delegate console
   * @param filePath A file path to write to. The parent directories must be created in advance.
   * @param consoleFlushRate How often (in number of messages) to flush this file console.
   */
  public FileConsole(Console delegate, Path filePath, Duration consoleFlushRate) {
    super(delegate);
    this.filePath = Preconditions.checkNotNull(filePath);
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("File logging thread %d")
        .setDaemon(true)
        .setUncaughtExceptionHandler(
            (t, e) -> {
              logger.atSevere().withCause(e).log(
                  "Thread %s threw an unhandled exception: %s", t, e.getMessage());
              System.exit(31 /*ExitCode.INTERNAL_ERROR*/);
            })
        .build();
    loggingExecutor = Executors.newSingleThreadExecutor(threadFactory);
    if (consoleFlushRate.isNegative() || consoleFlushRate.isZero()) {
      flushingExecutor = null;
    } else {
      flushingExecutor = MoreExecutors.listeningDecorator(
          Executors.newSingleThreadScheduledExecutor(threadFactory));
      //noinspection unused : Ignored as this is best effort
      Future<?> ignored = flushingExecutor.scheduleAtFixedRate(() -> {
        logger.atInfo().log("Executing console flush");
        flush();
      }, consoleFlushRate.toMillis(), consoleFlushRate.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  @Override
  protected void handleMessage(MessageType type, String message) {
    Writer writer = getWriter();
    if (writer == null) {
      return;
    }
    // Submitting to a shut-down executor throws, so this has to be synchronized to avoid shutting
    // down in-between from another thread.
    synchronized (loggingExecutor) {
      if (loggingExecutor.isShutdown()) {
         return;
      }
      //noinspection unused : Ignored as this is best effort
      Future<?> ignored = loggingExecutor.submit(() -> doWrite(writer,
            String.format("%s %s: %s\n",
                ZonedDateTime.now(ZoneId.systemDefault()).format(DATE_PREFIX_FMT), type, message)));
    }
  }

  private void doWrite(Writer writer, String s) {
    if (failed) {
      return;
    }
    try {
      writer.append(s);
    } catch (IOException e) {
      failed = true;
      logger.atSevere().withCause(e).log(
          "Could not write to file: %s. Redirecting will be disabled.", filePath);
    }
  }

  private void flush() {
    if (failed) {
      return;
    }
    Writer writer = getWriter();
    if (writer != null) {
      try {
        writer.flush();
      } catch (IOException e) {
        failed = true;
        logger.atSevere().withCause(e).log(
            "Could not write to file: %s. Redirecting will be disabled.", filePath);
      }
    }
  }

  private synchronized Writer getWriter() {
    if (writer == null) {
      writer = initWriter();
    }
    return writer;
  }

  @Nullable
  private BufferedWriter initWriter() {
    try {
      StandardOpenOption openOption =
          Files.exists(filePath)
              ? StandardOpenOption.TRUNCATE_EXISTING
              : StandardOpenOption.CREATE_NEW;
      return Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, openOption);
    } catch (IOException e) {
      failed = true;
      logger.atSevere().withCause(e).log(
          "Could not open file: %s. Redirecting will be disabled.", filePath);
    }
    return null;
  }

  @Override
  public void close() {
    super.close();
    synchronized (loggingExecutor) {
      loggingExecutor.shutdown();
      if (flushingExecutor != null) {
        flushingExecutor.shutdown();
      }
    }
    try {
      loggingExecutor.awaitTermination(10, TimeUnit.SECONDS);
      if (flushingExecutor != null) {
        flushingExecutor.awaitTermination(10, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      logger.atSevere().withCause(e).log("Exception while shutting down.");
    }
    try {
      if (writer == null) {
        return;
      }
      writer.close();
      logger.atInfo().log("Closed file %s", filePath);
    } catch (IOException e) {
      failed = true;
      logger.atSevere().withCause(e).log(
          "Could not close file: %s. Redirecting will be disabled.", filePath);
    }
    loggingExecutor.shutdownNow();
    if (flushingExecutor != null) {
      flushingExecutor.shutdownNow();
    }
  }
}
