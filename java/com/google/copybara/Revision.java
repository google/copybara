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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.copybara.exception.RepoException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/**
 * A revision of {@link Origin}.
 *
 * <p>For example, in Git it would be a commit SHA-1.
 */
public interface Revision {

  /**
   * Reads the timestamp of this revision from the repository, or {@code null} if this repo type
   * does not support it. This is the {@link Instant} from the UNIX epoch when the revision was
   * submitted to the source repository.
   */
  @Nullable
  ZonedDateTime readTimestamp() throws RepoException;

  /**
   * String representation of the revision that can be parsed by {@link Origin#resolve(String)}.
   *
   * <p> Unlike {@link #toString()} method, this method is guaranteed to be stable.
   */
  String asString();

  /**
   * If not null, returns a stable name representing the reference from where this {@code Revision}
   * was created.
   *
   * <p>For example if the user passed 'master' in the command line, the {@link #asString()}  would
   * return the SHA-1 and this method would return 'master'. Note that it is a valid response
   * to return {@link #asString()} here if the implementation chooses to.
   */
  default @Nullable String contextReference() {
    return null;
  }

  /**
   * Return any associated label with the revision. Keys are the label name and values are the
   * content of the label.
   *
   * <p>Labels should only be set when the origin knows for sure that the reference is in the
   * context of the current migration. For example if origin resolves 'master' internally, it
   * should not put the labels of the commit at HEAD master, since the workflows might choose to
   * just migrate the affected changes (using origin_files) and not include that reference. This
   * could potentially make labels from HEAD master for unrelated commits to be used in a
   * migration.
   *
   * <p>On the other hand, a good usage of associated labels would be for a code review system
   * like Gerrit. The returned reference could have associated labels like the gerrit url for
   * the change.
   */
  default ImmutableListMultimap<String, String> associatedLabels() {
    return ImmutableListMultimap.of();
  }

  /**
   * Find a specific label
   */
  default ImmutableList<String> associatedLabel(String label) {
    return associatedLabels().get(label);
  }

  /**
   * The url of the revision repository .
   */
  @Nullable default String getUrl() {
    return null;
  }

  /**
   * Given a list of labels it adds new labels without repeating them
   */
  static ImmutableListMultimap<String, String> addNewLabels(
      ImmutableListMultimap<String, String> existingLabels,
      ImmutableListMultimap<String, String> newLabels) {
    ImmutableListMultimap.Builder<String, String> builder = ImmutableListMultimap.builder();
    builder.putAll(existingLabels);
    for (Entry<String, Collection<String>> k : newLabels.asMap().entrySet()) {
      for (String v : k.getValue()) {
        if (existingLabels.containsEntry(k.getKey(), v)) {
          continue;
        }
        builder.put(k.getKey(), v);
      }
    }
    return builder.build();
  }

}
