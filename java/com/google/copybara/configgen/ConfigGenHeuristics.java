/*
 * Copyright (C) 2022 Google Inc.
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
package com.google.copybara.configgen;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.Math.max;
import static java.util.Comparator.comparingInt;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.copybara.util.Glob;
import com.google.copybara.util.RenameDetector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Given a set of files from the origin and a set of files from the destination, it generates
 * origin_globs, destination_globs and core.moves to minimize the number of transformations for
 * converting code from origin to destintion.
 *
 * <p>Note that the generation is not perfect and should be reviewed by a human.
 */
public class ConfigGenHeuristics {

  private final Path origin;
  private final Path destination;
  private final ImmutableSet<Path> destinationOnlyPaths;
  private final int percentSimilar;

  /**
   * Creates the Generator object
   *
   * @param origin the root folder for the files of the origin repository
   * @param destination the root folder for the files of the repository
   * @param destinationOnlyPaths paths known to be only in the destination so they are skipped in
   *     the similarity check.
   * @param percentSimilar percentage of similar lines to consider two files the same.
   */
  public ConfigGenHeuristics(
      Path origin, Path destination, ImmutableSet<Path> destinationOnlyPaths, int percentSimilar) {
    this.origin = checkNotNull(origin);
    this.destination = checkNotNull(destination);
    this.destinationOnlyPaths = checkNotNull(destinationOnlyPaths);
    this.percentSimilar = percentSimilar;
  }

  /** Result of the config generation */
  public static class Result {
    private final Glob originGlob;

    public Result(Glob originFiles) {
      this.originGlob = originFiles;
    }

    public Glob getOriginGlob() {
      return originGlob;
    }
  }

  /**
   * Run the config generation to find a good origin_files, destination_files and core.moves needed
   * to convert the code form {@code origin} to {@code destination}.
   *
   * @return an object containing all the heuristic results.
   */
  public ConfigGenHeuristics.Result run() throws IOException {
    ImmutableSet<Path> gitFiles = listFiles(origin);
    ImmutableSet<Path> g3Files = listFiles(destination);
    SimilarityDetector similarityDetector =
        SimilarityDetector.create(origin, gitFiles, destinationOnlyPaths, percentSimilar);
    Map<Path, Path> similarFiles = new TreeMap<>();

    for (Path file : g3Files) {
      Optional<Path> originPath = similarityDetector.find(destination.resolve(file));
      if (originPath.isPresent()) {
        similarFiles.put(originPath.get(), file);
      }
    }
    HashSet<Path> originOnly = new HashSet<>(gitFiles);
    originOnly.removeAll(similarFiles.keySet());

    HashSet<Path> destinationOnly = new HashSet<>(g3Files);
    destinationOnly.removeAll(similarFiles.values());

    IncludesGlob originGlob =
        new IncludesGlob(ImmutableSet.of("**"), ImmutableSet.of())
            .minimizeScore(similarFiles.keySet(), originOnly, 0);

    originGlob =
        consolidateCommonPattern(originGlob, similarFiles.keySet(), p -> p.startsWith("."), ".**");

    // Enable to debug what is being generated:
    // debug(similarFiles, destinationOnly, originGlob);

    return new ConfigGenHeuristics.Result(originGlob.glob);
  }

  /** TODO(malcon): Used for debugging what is going on. Can be removed in the future */
  private void debug(
      Map<Path, Path> similarFiles, HashSet<Path> destinationOnly, IncludesGlob originGlob) {
    for (Map.Entry<Path, Path> e : similarFiles.entrySet()) {
      System.err.println(e.getKey() + " -> " + e.getValue());
    }

    System.err.println();
    System.err.println("git_files = " + originGlob.glob);

    System.err.println();
    for (Path path : destinationOnly) {
      System.err.println("G3 Only: " + path);
    }
  }

  /**
   * Consolidate more than one pattern that matches the predicate with a single replacement pattern
   * when there is no file being migrated that matches the predicate.
   *
   * <p>Example: if we have several patterns filtering ".foo" like files and we don't migrate any
   * hidden file, we replace them with a single ".**". Could be used for filtering tests, etc.
   */
  private IncludesGlob consolidateCommonPattern(
      IncludesGlob originGlob,
      Set<Path> migratedFiles,
      Predicate<String> filePredicate,
      String replacement) {
    if (originGlob.excludes.stream().filter(filePredicate).count() <= 1
        || migratedFiles.stream().anyMatch(p -> filePredicate.test(p.toString()))) {
      return originGlob;
    }
    ImmutableSet.Builder<String> newExcludes = ImmutableSet.builder();
    newExcludes.add(replacement);
    for (String pattern : originGlob.excludes) {
      if (filePredicate.test(pattern)) {
        continue;
      }
      newExcludes.add(pattern);
    }
    return new IncludesGlob(originGlob.includes, newExcludes.build());
  }

  private static class ExcludesGlob extends IncludesGlob {

    private ExcludesGlob(Set<String> includes, Set<String> excludes) {
      super(includes, excludes);
    }

    @Override
    protected int score() {
      // If excludes needs excludes, it is not a good excludes.
      return super.excludes.isEmpty() ? super.score() : Integer.MAX_VALUE;
    }

    @Override
    protected IncludesGlob withExcludes(Collection<Path> toBeIncluded, Set<Path> toBeExcluded) {
      ImmutableSet<Path> matchingExcludes = findMatchingExcludes(toBeExcluded);
      return create(
          includes, matchingExcludes.stream().map(Path::toString).collect(toImmutableSet()));
    }

    @Override
    protected IncludesGlob create(Set<String> includes, Set<String> excludes) {
      return new ExcludesGlob(includes, excludes);
    }
  }

  private static class IncludesGlob implements Comparable<IncludesGlob> {
    protected final Set<String> includes;
    protected final Set<String> excludes;
    private final Glob glob;

    private IncludesGlob(Set<String> includes, Set<String> excludes) {
      this.includes = includes;
      this.excludes = excludes;
      this.glob = Glob.createGlob(includes, excludes);
    }

    protected IncludesGlob create(Set<String> includes, Set<String> excludes) {
      // Use treeset to have it sorted
      return new IncludesGlob(new TreeSet<>(includes), new TreeSet<>(excludes));
    }

    protected int score() {
      return max(includes.size(), 1) * max(excludes.size(), 1);
    }

    @Override
    public int compareTo(IncludesGlob o) {
      return Integer.compare(this.score(), o.score());
    }

    IncludesGlob minimizeScore(Collection<Path> toBeIncluded, Set<Path> toBeExcluded, int level) {
      IncludesGlob globAndScore = withExcludes(toBeIncluded, toBeExcluded);

      HashMultimap<String, Path> recursiveIncludes = HashMultimap.create();
      Set<String> newIncludes = new HashSet<>();
      Set<String> newExcludes = new HashSet<>();
      for (Path p : toBeIncluded) {
        if (p.getNameCount() <= level + 1) {
          newIncludes.add(p.toString());
        } else {
          recursiveIncludes.put(p.subpath(0, level + 1) + "/**", p);
        }
      }
      // For each recursive pattern, lets try to optimize for fewer entries and see if the
      // combination has fewer entries than {@code globAndScore}
      for (String pattern : recursiveIncludes.keySet()) {
        IncludesGlob newGlob =
            create(ImmutableSet.of(pattern), ImmutableSet.of())
                .minimizeScore(recursiveIncludes.get(pattern), toBeExcluded, level + 1);
        newIncludes.addAll(newGlob.includes);
        newExcludes.addAll(newGlob.excludes);
      }
      IncludesGlob comboGlob = create(newIncludes, newExcludes);

      return comboGlob.score() < globAndScore.score() ? comboGlob : globAndScore;
    }

    protected IncludesGlob withExcludes(Collection<Path> toBeIncluded, Set<Path> toBeExcluded) {
      ImmutableSet<Path> excludedPaths = findMatchingExcludes(toBeExcluded);
      IncludesGlob optimizedExcludes =
          new ExcludesGlob(ImmutableSet.of("**"), ImmutableSet.of())
              .minimizeScore(excludedPaths, ImmutableSet.copyOf(toBeIncluded), 0);

      // Found a better excludes than the naive approach of listing all excludes!
      if (optimizedExcludes.excludes.isEmpty()) {
        return create(includes, optimizedExcludes.includes);
      }

      return create(includes, excludedPaths.stream().map(Path::toString).collect(toImmutableSet()));
    }

    protected ImmutableSet<Path> findMatchingExcludes(Set<Path> toBeExcluded) {
      Path root = Paths.get("/");
      PathMatcher pathMatcher = Glob.createGlob(includes).relativeTo(root);
      ImmutableSet.Builder<Path> excludedPaths = ImmutableSet.builder();
      for (Path ex : toBeExcluded) {
        if (pathMatcher.matches(root.resolve(ex))) {
          excludedPaths.add(ex);
        }
      }
      return excludedPaths.build();
    }

    @Override
    public String toString() {
      return this.getClass().getSimpleName() + "(score: " + score() + ", " + glob + ")";
    }
  }

  private static Path commonSuffix(Path a, Path b) {
    ArrayDeque<String> paths = new ArrayDeque<>();
    for (int aIndex = a.getNameCount() - 1, bIndex = b.getNameCount() - 1;
        aIndex >= 0 && bIndex >= 0;
        aIndex--, bIndex--) {
      if (!a.getName(aIndex).equals(b.getName(bIndex))) {
        break;
      }
      paths.addFirst(a.getName(aIndex).toString());
    }
    return Paths.get(Joiner.on("/").join(paths));
  }

  private static class SimilarityDetector {
    // Useful for binaries
    private final HashMultimap<String, Path> hashBased;
    private final RenameDetector<Path> similarLines;
    private final ImmutableSet<Path> destinationOnlyPaths;
    private final int percentSimilar;

    private SimilarityDetector(
        HashMultimap<String, Path> hashBased,
        RenameDetector<Path> similarLines,
        ImmutableSet<Path> destinationOnlyPaths,
        int percentSimilar) {
      this.hashBased = hashBased;
      this.similarLines = similarLines;
      this.destinationOnlyPaths = destinationOnlyPaths;
      this.percentSimilar = percentSimilar;
    }

    private Optional<Path> find(Path path) throws IOException {
      if (destinationOnlyPaths.contains(path.getFileName())) {
        return Optional.empty();
      }

      byte[] content = Files.readAllBytes(path);

      // Highest priority same hash. RenameDetector fails for small files

      Optional<Path> hashFinding =
          hashBased.get(hash(content)).stream()
              .max(comparingInt(o -> commonSuffix(path, o).getNameCount()));
      if (hashFinding.isPresent()) {
        return hashFinding;
      }

      // Second priority similarity
      RenameDetector.Score<Path> score =
          Iterables.getFirst(
              similarLines.scoresForLaterFile(new ByteArrayInputStream(content)), null);
      if (score != null && score.getScore() > RenameDetector.MAX_SCORE * percentSimilar / 100) {
        return Optional.ofNullable(score.getKey());
      }
      return Optional.empty();
    }

    private static SimilarityDetector create(
        Path parent,
        ImmutableSet<Path> files,
        ImmutableSet<Path> destinationOnlyPaths,
        int percentSimilar)
        throws IOException {
      RenameDetector<Path> similarLines = new RenameDetector<>();
      HashMultimap<String, Path> hashes = HashMultimap.create(files.size(), 1);
      for (Path file : files) {
        byte[] bytes = Files.readAllBytes(parent.resolve(file));
        hashes.put(hash(bytes), file);
        similarLines.addPriorFile(file, new ByteArrayInputStream(bytes));
      }
      return new SimilarityDetector(hashes, similarLines, destinationOnlyPaths, percentSimilar);
    }

    private static String hash(byte[] bytes) {
      return Hashing.sha256().hashBytes(bytes).toString();
    }
  }

  private ImmutableSet<Path> listFiles(Path path) throws IOException {
    ImmutableSet.Builder<Path> result = ImmutableSet.builder();
    Files.walkFileTree(
        path,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (!Files.isSymbolicLink(file)) {
              result.add(path.relativize(file));
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return result.build();
  }
}
