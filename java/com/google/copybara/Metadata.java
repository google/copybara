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

package com.google.copybara;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.authoring.Author;

/**
 * Metadata associated with a change: Change message, author, etc.
 *
 * @param message Description to be used for the change
 * @param author Author to be used for the change
 * @param hiddenLabels Hidden labels are labels added by transformations during transformations but
 *     that they are not visible in the message.
 */
public record Metadata(
    String message, Author author, ImmutableSetMultimap<String, String> hiddenLabels) {

  public Metadata {
    checkNotNull(message, "Message cannot be null");
    checkNotNull(author, "Author cannot be null");
    checkNotNull(hiddenLabels, "hidden labels cannot be null");
  }

  public Metadata withAuthor(Author author) {
    return new Metadata(message, author, hiddenLabels);
  }

  public Metadata withMessage(String message) {
    return new Metadata(message, author, hiddenLabels);
  }

  /**
   * We never allow deleting hidden labels. Use a different name if you want to rename one.
   */
  public Metadata withHiddenLabels(ImmutableMultimap<String, String> hiddenLabels) {
    checkNotNull(hiddenLabels, "hidden labels cannot be null");
    return new Metadata(
        message,
        author,
        ImmutableSetMultimap.<String, String>builder()
            .putAll(this.hiddenLabels)
            .putAll(hiddenLabels)
            .build());
  }
}
