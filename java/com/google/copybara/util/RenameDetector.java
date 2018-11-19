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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Class for detecting renames between two repo versions. This is intended to be used when
 * implementing {@code Destination} for repositories that don't automatically detect renames
 * (e.g. Mercurial).
 *
 * @param <I> type of key to use for referencing files in the prior revision
 */
public final class RenameDetector<I> {

  private final List<PriorFile<I>> priorFiles = new ArrayList<>();

  private static final class PriorFile<I> {
    /**
     * Key associated with the name of the file in the prior revision. For instance, this can just
     * be a String of the relative path from the repo root.
     */
    I key;

    /**
     * A list of hashes corresponding with chunks of content in the file.
     *
     * TODO(copybara-team): Store hash counts rather than just the hash itself.
     */
    int[] hashes;
  }

  private static final class HashingByteProcessor implements ByteProcessor<int[]> {

    int hash;
    final HashSet<Integer> hashes = new HashSet<>();

    @Override
    public boolean processBytes(byte[] buf, int off, int len) {
      while (off != len) {
        hash *= 31;
        byte b = buf[off++];
        hash += b;
        if (b == '\n') {
          hashes.add(hash);
          hash = 0;
        }
      }
      return true;
    }

    @Override
    public int[] getResult() {
      hashes.add(hash);
      int[] hashesArray = Ints.toArray(hashes);
      Arrays.sort(hashesArray);
      return hashesArray;
    }
  }

  /**
   * Hashes a single file until the end of the stream.
   */
  private int[] hashes(InputStream input) throws IOException {
    try {
      return ByteStreams.readBytes(input, new HashingByteProcessor());
    } finally {
      input.close();
    }
  }

  /**
   * Hashes a single file in the prior revision so it can be checked for similarities with files in
   * the later revision. Closes {@code input} before returning.
   */
  public void addPriorFile(I key, InputStream input) throws IOException {
    PriorFile<I> hash = new PriorFile<>();
    hash.key = key;
    hash.hashes = hashes(input);
    priorFiles.add(hash);
  }

  /**
   * The maximum score that can be returned. This value gives high-enough resolution for reasonably
   * sized files and eliminates the risk of overflow for source files with fewer than
   * 2,000,000 lines (roughly {@code Integer.MAX_VALUE / MAX_SCORE}).
   */
  public static final int MAX_SCORE = 1000;

  /**
   * Hashes a single file in the later revision so it can be checked for similarities with all files
   * in the prior revision added previously. Closes {@code input} before returning.
   *
   * <p>The algorithm used is based on, but not equivalent to, the Git algorithm implemented in
   * <a href="https://github.com/git/git/blob/master/diffcore-rename.c">diffcore-rename.c</a>. Both
   * algorithms hash every line of every file, store a list of the hash-codes for each file, and
   * then check the number of shared hash-codes between files to estimate their similarity.
   *
   * <p>The Git algorithm has a concept of a minimum score. i.e. if two files have < X%
   * similarity, they will not be returned in the results. This allows some file comparisons to be
   * skipped because of large differences in size. That is not implemented here: all similarities
   * greater than 0% (one or more shared lines) are returned.
   *
   * <p>When calling this method, the later file is checked against all the prior files added with
   * {@link #addPriorFile(Object,InputStream)}, scored based on the number of shared hashes, and
   * files with a score greater than 0 are returned.
   */
  public List<Score<I>> scoresForLaterFile(InputStream input) throws IOException {
    List<Score<I>> results = new ArrayList<>();
    int[] laterHashes = hashes(input);
    if (isEmpty(laterHashes)) {
      return ImmutableList.of();
    }
    for (PriorFile<I> priorFile : priorFiles) {
      // Determine the number of hashes that priorFile.hashes and laterHashes have in common.
      int matchCount = 0;
      int priorIndex = 0;
      int laterIndex = 0;
      while (priorIndex < priorFile.hashes.length && laterIndex < laterHashes.length) {
        int priorHash = priorFile.hashes[priorIndex];
        int laterHash = laterHashes[laterIndex];
        if (laterHash > priorHash) {
          priorIndex++;
        } else {
          laterIndex++;
          if (priorHash == laterHash) {
            matchCount++;
          }
        }
      }
      if (matchCount != 0 && !isEmpty(priorFile.hashes)) {
        int size =
            (laterHashes.length > priorFile.hashes.length)
                ? laterHashes.length
                : priorFile.hashes.length;
        results.add(new Score<>(priorFile.key, matchCount * MAX_SCORE / size));
      }
    }

    results.sort((a, b) -> Integer.compare(b.score, a.score));

    return results;
  }

  private static boolean isEmpty(int[] hashes) {
    return hashes.length == 0 || (hashes.length == 1 && hashes[0] == 0);
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

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("key", key)
          .add("score", score)
          .toString();
    }
  }
}
