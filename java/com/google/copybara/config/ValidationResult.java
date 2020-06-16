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

package com.google.copybara.config;

import static com.google.copybara.config.ValidationResult.Level.ERROR;
import static com.google.copybara.config.ValidationResult.Level.WARNING;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.FormatMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The immutable result of a validation performed by {@link ConfigValidator}.
 */
public class ValidationResult {

  public static final ValidationResult EMPTY = new ValidationResult(Collections.emptyList());

  private final ImmutableList<ValidationMessage> messages;

  private ValidationResult(List<ValidationMessage> messages) {
    this.messages = ImmutableList.copyOf(messages);
  }

  /** Returns all the {@link ValidationMessage}s in the order that were registered.*/
  public ImmutableList<ValidationMessage> getAllMessages() {
    return messages;
  }

  /** Returns true iff there was at least one warning message.*/
  public boolean hasWarnings() {
    return !getWarnings().isEmpty();
  }

  /** Returns true iff there was at least one error message.*/
  public boolean hasErrors() {
    return !getErrors().isEmpty();
  }

  /** Returns the text of the warning messages, in the order that were registered.*/
  public ImmutableList<String> getWarnings() {
    return messages.stream()
        .filter(v -> v.level == WARNING)
        .map(ValidationMessage::getMessage)
        .collect(ImmutableList.toImmutableList());
  }

  /** Returns the text of the error messages, in the order that were registered.*/
  public ImmutableList<String> getErrors() {
    return messages.stream()
          .filter(v -> v.level == ERROR)
          .map(ValidationMessage::getMessage)
          .collect(ImmutableList.toImmutableList());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("messages", messages)
      .toString();
  }

  /**
   * Levels of validation messages. Can only be warning or error, because it doesn't make sense
   * to have info here.
   */
  public enum Level {
    WARNING,
    ERROR
  }

  /** Encapsulates a validation message and a {@link Level}*/
  public static class ValidationMessage {
    private final Level level;
    private final String message;

    private ValidationMessage(Level level, String message) {
      this.level = Preconditions.checkNotNull(level);
      this.message = Preconditions.checkNotNull(message);
    }

    public Level getLevel() {
      return level;
    }

    public String getMessage() {
      return message;
    }

    /**
     * Generates a string from this validation message with padded level and
     * message text.
     */
    @Override
    public String toString() {
      return String.format("%-8s %s", level, message);
    }
  }

  /** A builder of {@link ValidationResult}. */
  public static class Builder {

    private final List<ValidationMessage> messages = new ArrayList<>();

    public Builder warning(String message) {
      messages.add(new ValidationMessage(WARNING, message));
      return this;
    }

    @FormatMethod
    public Builder warningFmt(String message, Object... args) {
      messages.add(new ValidationMessage(WARNING, String.format(message, args)));
      return this;
    }

    public Builder error(String message) {
      messages.add(new ValidationMessage(ERROR, message));
      return this;
    }

    @FormatMethod
    public Builder errorFmt(String message, Object... args) {
      messages.add(new ValidationMessage(ERROR, String.format(message, args)));
      return this;
    }

    public Builder append(ValidationResult result) {
      messages.addAll(result.messages);
      return this;
    }

    public ValidationResult build() {
      return new ValidationResult(messages);
    }
  }
}
