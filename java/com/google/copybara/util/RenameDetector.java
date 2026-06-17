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

import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
  private final boolean ignoreCarriageReturn;
  private final boolean ignoreWhitespace;
  private final boolean skipNewlinesInHash;
  private final boolean considerFilenames;
  private final ImmutableSet<String> filenameExceptions;

  private final List<PriorFile<I>> priorFiles = new ArrayList<>();

  public RenameDetector(boolean ignoreCarriageReturn, boolean ignoreWhitespace) {
    this(ignoreCarriageReturn, ignoreWhitespace, false);
  }

  public RenameDetector(
      boolean ignoreCarriageReturn, boolean ignoreWhitespace, boolean skipNewlinesInHash) {
    this(ignoreCarriageReturn, ignoreWhitespace, skipNewlinesInHash, false);
  }

  public RenameDetector(
      boolean ignoreCarriageReturn,
      boolean ignoreWhitespace,
      boolean skipNewlinesInHash,
      boolean considerFilenames) {
    this(
        ignoreCarriageReturn,
        ignoreWhitespace,
        skipNewlinesInHash,
        considerFilenames,
        ImmutableSet.of());
  }

  public RenameDetector(
      boolean ignoreCarriageReturn,
      boolean ignoreWhitespace,
      boolean skipNewlinesInHash,
      boolean considerFilenames,
      ImmutableSet<String> filenameExceptions) {
    this.ignoreCarriageReturn = ignoreCarriageReturn;
    this.ignoreWhitespace = ignoreWhitespace;
    this.skipNewlinesInHash = skipNewlinesInHash;
    this.considerFilenames = considerFilenames;
    this.filenameExceptions = filenameExceptions;
  }

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
    final boolean ignoreCarriageReturn;
    final boolean ignoreWhitespace;
    final boolean skipNewlinesInHash;
    final HashSet<Integer> hashes = new HashSet<>();
    boolean hasPendingContent = false;

    HashingByteProcessor(
        boolean ignoreCarriageReturn, boolean ignoreWhitespace, boolean skipNewlinesInHash) {
      this.ignoreCarriageReturn = ignoreCarriageReturn;
      this.ignoreWhitespace = ignoreWhitespace;
      this.skipNewlinesInHash = skipNewlinesInHash;
    }

    @Override
    public boolean processBytes(byte[] buf, int off, int len) {
      while (off != len) {
        byte b = buf[off++];
        if (ignoreCarriageReturn && b == '\r') {
          // Skip carriage return in Windows-style line endings when hashing.
          continue;
        }
        if (ignoreWhitespace && (b == ' ' || b == '\t')) {
          continue;
        }
        if (b == '\n') {
          if (!skipNewlinesInHash) {
            hash *= 31;
            hash += b;
            hashes.add(hash);
          } else if (hasPendingContent) {
            hashes.add(hash);
          }
          hash = 0;
          hasPendingContent = false;
        } else {
          hash *= 31;
          hash += b;
          hasPendingContent = true;
        }
      }
      return true;
    }

    @Override
    public int[] getResult() {
      if (!skipNewlinesInHash || hasPendingContent) {
        hashes.add(hash);
      }
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
      return ByteStreams.readBytes(
          input,
          new HashingByteProcessor(ignoreCarriageReturn, ignoreWhitespace, skipNewlinesInHash));
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
   * <p>The algorithm used is based on, but not equivalent to, the Git algorithm implemented in <a
   * href="https://github.com/git/git/blob/master/diffcore-rename.c">diffcore-rename.c</a>. Both
   * algorithms hash every line of every file, store a list of the hash-codes for each file, and
   * then check the number of shared hash-codes between files to estimate their similarity.
   *
   * <p>The Git algorithm has a concept of a minimum score. i.e. if two files have < X% similarity,
   * they will not be returned in the results. This allows some file comparisons to be skipped
   * because of large differences in size. That is not implemented here: all similarities greater
   * than 0% (one or more shared lines) are returned.
   *
   * <p>When calling this method, the later file is checked against all the prior files added with
   * {@link #addPriorFile(Object,InputStream)}, scored based on the number of shared hashes, and
   * files with a score greater than 0 are returned.
   */
  public ImmutableList<Score<I>> scoresForLaterFile(InputStream input) throws IOException {
    if (considerFilenames) {
      throw new IllegalStateException(
          "Cannot call scoresForLaterFile without laterKey when considerFilenames is true");
    }
    return scoresForLaterFile(null, input);
  }

  public ImmutableList<Score<I>> scoresForLaterFile(I laterKey, InputStream input)
      throws IOException {
    List<Score<I>> results = new ArrayList<>();
    int[] laterHashes = hashes(input);
    if (isEmpty(laterHashes)) {
      return ImmutableList.of();
    }
    String laterFilename = considerFilenames ? getFilename(laterKey) : null;

    for (PriorFile<I> priorFile : priorFiles) {
      if (considerFilenames) {
        String priorFilename = getFilename(priorFile.key);
        boolean isException =
            filenameExceptions.stream().anyMatch(e -> Ascii.equalsIgnoreCase(e, priorFilename))
                || (laterFilename != null
                    && filenameExceptions.stream()
                        .anyMatch(e -> Ascii.equalsIgnoreCase(e, laterFilename)));
        if (!isException && isTooFar(priorFilename, laterFilename)) {
          continue;
        }
      }
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

    return ImmutableList.copyOf(results);
  }

  private static boolean isEmpty(int[] hashes) {
    return hashes.length == 0 || (hashes.length == 1 && hashes[0] == 0);
  }

  private boolean isTooFar(String name1, String name2) {
    int maxLen = Math.max(name1.length(), name2.length());
    if (maxLen == 0) {
      return false;
    }
    int distance = levenshteinDistance(name1, name2);
    return distance * 2 > maxLen;
  }

  private static int levenshteinDistance(String s, String t) {
    int[][] distance = new int[s.length() + 1][t.length() + 1];

    for (int i = 0; i <= s.length(); i++) {
      distance[i][0] = i;
    }
    for (int j = 1; j <= t.length(); j++) {
      distance[0][j] = j;
    }

    for (int i = 1; i <= s.length(); i++) {
      for (int j = 1; j <= t.length(); j++) {
        distance[i][j] =
            Ints.min(
                distance[i - 1][j] + 1,
                distance[i][j - 1] + 1,
                distance[i - 1][j - 1] + ((s.charAt(i - 1) == t.charAt(j - 1)) ? 0 : 1));
      }
    }

    return distance[s.length()][t.length()];
  }

  private static String getFilename(Object key) {
    if (key == null) {
      return "";
    }
    String s = key.toString();
    int lastSlash = s.lastIndexOf('/');
    if (lastSlash >= 0) {
      return s.substring(lastSlash + 1);
    }
    return s;
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
