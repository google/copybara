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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
  private final ListeningScheduledExecutorService executor;

  private boolean failed = false;
  @Nullable private Writer writer;


  /**
   * Creates a new {@link FileConsole}.
   *
   *  @param delegate A delegate console
   * @param filePath A file path to write to. The parent directories must be created in advance.
   * @param consoleFlushRate How often (in number of messages) to flush this file console.
   */
  public FileConsole(Console delegate, Path filePath, Duration consoleFlushRate) {
    super(delegate);
    this.filePath = Preconditions.checkNotNull(filePath);
    if (consoleFlushRate.isNegative() || consoleFlushRate.isZero()) {
      executor = null;
    } else {
      executor = MoreExecutors.listeningDecorator(
          Executors.newSingleThreadScheduledExecutor(
              new ThreadFactoryBuilder()
                  .setNameFormat("Console flush thread %d")
                  .setDaemon(true)
                  .setUncaughtExceptionHandler(
                      (t, e) -> {
                        logger.atSevere().withCause(e).log(
                            "Thread %s threw an unhandled exception: %s", t, e.getMessage());
                        System.exit(31 /*ExitCode.INTERNAL_ERROR*/);
                      })
                  .build()));

      //noinspection unused : Ignored as this is best effort
      Future<?> ignored = executor.scheduleAtFixedRate(() -> {
        logger.atInfo().log("Executing console flush");
        flush();
      }, consoleFlushRate.toMillis(), consoleFlushRate.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  @Override
  protected synchronized void handleMessage(MessageType type, String message) {
    if (failed) {
      return;
    }
    Writer writer = getWriter();
    if (writer == null) {
      return;
    }
    try {
      writer.append(
          String.format(
              "%s %s: %s\n",
              ZonedDateTime.now(ZoneId.systemDefault()).format(DATE_PREFIX_FMT), type, message));
    } catch (IOException e) {
      failed = true;
      logger.atSevere().withCause(e).log(
          "Could not write to file: %s. Redirecting will be disabled.", filePath);
    }
  }
  private synchronized void flush() {
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

  private Writer getWriter() {
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
    if (writer == null) {
      return;
    }
    try {
      writer.close();
      logger.atInfo().log("Closed file %s", filePath);
    } catch (IOException e) {
      failed = true;
      logger.atSevere().withCause(e).log(
          "Could not close file: %s. Redirecting will be disabled.", filePath);
    }

    if (executor != null) {
      executor.shutdownNow();
    }
  }
}
