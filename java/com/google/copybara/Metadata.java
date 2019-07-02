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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.authoring.Author;

/**
 * Metadata associated with a change: Change message, author, etc.
 */
public final class Metadata {

  private final String message;
  private final Author author;
  private final ImmutableSetMultimap<String, String> hiddenLabels;

  public Metadata(String message, Author author,
      ImmutableSetMultimap<String, String> hiddenLabels) {
    this.message = checkNotNull(message);
    this.author = checkNotNull(author);
    this.hiddenLabels = checkNotNull(hiddenLabels);
  }

  public final Metadata withAuthor(Author author) {
    return new Metadata(message, checkNotNull(author, "Author cannot be null"), hiddenLabels);
  }

  public final Metadata withMessage(String message) {
    return new Metadata(checkNotNull(message, "Message cannot be null"), author, hiddenLabels);
  }

  /**
   * We never allow deleting hidden labels. Use a different name if you want to rename one.
   */
  public final Metadata addHiddenLabels(ImmutableMultimap<String, String> hiddenLabels) {
    checkNotNull(hiddenLabels, "hidden labels cannot be null");
    return new Metadata(message, author,
        ImmutableSetMultimap.<String, String>builder()
            .putAll(this.hiddenLabels)
            .putAll(hiddenLabels).build());
  }

  /**
   * Description to be used for the change
   */
  public String getMessage() {
    return message;
  }

  /**
   * Author to be used for the change
   */
  public Author getAuthor() {
    return author;
  }

  /**
   * Hidden labels are labels added by transformations during transformations but that they are
   * not visible in the message.
   */
  public ImmutableSetMultimap<String, String> getHiddenLabels() {
    return hiddenLabels;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", message)
        .add("author", author)
        .add("hiddenLabels", hiddenLabels)
        .toString();
  }
}
