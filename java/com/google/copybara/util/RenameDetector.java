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

package com.google.copybara.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for detecting renames between two repo versions. This is intended to be used when
 * implementing {@code Destination} for repositories that don't automatically detect renames
 * (e.g. Mercurial).
 *
 * @param <I> type of key to use for referencing files in the prior revision
 */
public final class RenameDetector<I> {

  private final List<PriorHash<I>> priorHashes = new ArrayList<>();

  private static final class PriorHash<I> {
    /**
     * Key associated with the name of the file in the prior revision. For instance, this can just
     * be a String of the relative path from the repo root.
     */
    I key;

    /**
     * A single hash associated with the content at the start of the file.
     *
     * TODO(copybara-team): Store more detailed information about the file.
     */
    int hash;
  }

  /**
   * Hashes a single file until the end of the stream.
   */
  private int hash(InputStream input) throws IOException {
    try {
      int hash = 0;
      while (true) {
        // TODO(copybara-team): See if buffering the bytes improves performance.
        int b = input.read();
        if (b == -1) {
          break;
        }

        hash *= 31;
        hash += b;
      }
      return hash;
    } finally {
      input.close();
    }
  }

  /**
   * Hashes a single file in the prior revision so it can be checked for similarities with files in
   * the later revision. Closes {@code input} before returning.
   */
  public void addPriorFile(I key, InputStream input) throws IOException {
    PriorHash<I> hash = new PriorHash<I>();
    hash.key = key;
    hash.hash = hash(input);
    priorHashes.add(hash);
  }

  /**
   * Hashes a single file in the later revision so it can be checked for similarities with all files
   * in the prior revision added previously. Closes {@code input} before returning.
   */
  public List<Score<I>> scoresForLaterFile(InputStream input) throws IOException {
    List<Score<I>> results = new ArrayList<>();
    int hash = hash(input);

    for (PriorHash<I> priorHash : priorHashes) {
      if (priorHash.hash == hash) {
        // TODO(copybara-team): Decide on a reasonable score scale.
        results.add(new Score<>(priorHash.key, Integer.MAX_VALUE));
      }
    }

    return results;
  }

  public static final class Score<I> {

    private final I key;
    private final int score;

    Score(I key, int score) {
      this.key = key;
      this.score = score;
    }

    public I getKey() {
      return key;
    }

    public int getScore() {
      return score;
    }
  }
}
