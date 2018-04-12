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

package com.google.copybara.util.console;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * Represents a message registered in a console.
 */
public final class Message {

  /**
   * The type of messages registered in a console.
   */
  public enum MessageType {
    ERROR, WARNING, INFO, VERBOSE, PROGRESS, PROMPT
  }

  private final MessageType type;
  private final String text;

  public static Message error(String text) {
    return new Message(MessageType.ERROR, text);
  }

  public static Message warning(String text) {
    return new Message(MessageType.WARNING, text);
  }

  public static Message info(String text) {
    return new Message(MessageType.INFO, text);
  }

  public Message(MessageType type, String text) {
    this.type = Preconditions.checkNotNull(type);
    this.text = Preconditions.checkNotNull(text);
  }

  public MessageType getType() {
    return type;
  }

  public String getText() {
    return text;
  }

  @Override
  public String toString() {
    return type + ": " + text;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Message message = (Message) o;
    return type == message.type &&
        Objects.equal(text, message.text);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type, text);
  }
}
