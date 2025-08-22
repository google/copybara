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
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.copybara.GeneralOptions;
import com.google.copybara.util.Glob;
import com.google.copybara.util.RenameDetector;
import com.google.copybara.util.console.Console;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Given a set of files from the origin and a set of files from the destination, it generates
 * origin_globs, destination_globs and core.moves to minimize the number of transformations for
 * converting code from origin to destintion.
 *
 * <p>Note that the generation is not perfect and should be reviewed by a human.
 */
public class ConfigGenHeuristics {
  // Regex to parse upstream semver-like version refs. Group 3 is expected to capture the separator
  // character.
  private static final Pattern UPSTREAM_VERSION_REF_REGEX =
      Pattern.compile("^v?(\\d+)(([^\\d\\n])(?:\\d+)){0,2}$");

  private final Path origin;
  private final Path destination;
  private final ImmutableSet<Path> destinationOnlyPaths;
  private final int percentSimilar;
  private final boolean ignoreCarriageReturn;
  private final boolean ignoreWhitespace;
  private final GeneralOptions generalOptions;
  private final ImmutableList<String> versions;

  /**
   * Creates the Generator object
   *
   * @param origin the root folder for the files of the origin repository
   * @param destination the root folder for the files of the repository
   * @param destinationOnlyPaths paths known to be only in the destination so they are skipped in
   *     the similarity check.
   * @param percentSimilar percentage of similar lines to consider two files the same.
   * @param ignoreCarriageReturn Whether to ignore carriage return characters in file content
   *     comparisons.
   * @param ignoreWhitespace Whether to ignore whitespace characters in file content comparisons.
   * @param generalOptions the {@link GeneralOptions} object.
   * @param versions the list of version refs from the upstream
   */
  public ConfigGenHeuristics(
      Path origin,
      Path destination,
      ImmutableSet<Path> destinationOnlyPaths,
      int percentSimilar,
      boolean ignoreCarriageReturn,
      boolean ignoreWhitespace,
      GeneralOptions generalOptions,
      ImmutableList<String> versions) {
    this.origin = checkNotNull(origin);
    this.destination = checkNotNull(destination);
    this.destinationOnlyPaths = checkNotNull(destinationOnlyPaths);
    this.percentSimilar = percentSimilar;
    this.ignoreCarriageReturn = ignoreCarriageReturn;
    this.ignoreWhitespace = ignoreWhitespace;
    this.generalOptions = generalOptions;
    this.versions = versions;
  }

  /** Result of the config generation */
  public static class Result {

    private final Glob originGlob;
    private final GeneratorTransformations transformations;
    private final DestinationExcludePaths destinationExcludePaths;
    private final boolean shouldUseVersionSelector;
    private final Optional<String> versionSeparator;

    public Result(
        Glob originFiles,
        GeneratorTransformations transformations,
        DestinationExcludePaths destinationExcludePaths,
        boolean shouldUseVersionSelector,
        Optional<String> versionSeparator) {
      this.originGlob = originFiles;
      this.transformations = transformations;
      this.destinationExcludePaths = destinationExcludePaths;
      this.shouldUseVersionSelector = shouldUseVersionSelector;
      this.versionSeparator = versionSeparator;
    }

    public Glob getOriginGlob() {
      return originGlob;
    }

    public GeneratorTransformations getTransformations() {
      return transformations;
    }

    public DestinationExcludePaths getDestinationExcludePaths() {
      return destinationExcludePaths;
    }

    public boolean getShouldUseVersionSelector() {
      return shouldUseVersionSelector;
    }

    public Optional<String> getVersionSeparator() {
      return versionSeparator;
    }
  }

  /** A path and its similarity score to some other path. */
  public record PathAndScore(Path path, int score) {}

  /**
   * Run the config generation to find a good origin_files, destination_files and core.moves needed
   * to convert the code form {@code origin} to {@code destination}.
   *
   * @return an object containing all the heuristic results.
   */
  public ConfigGenHeuristics.Result run() throws IOException {
    ImmutableSet<Path> gitFiles = listFiles(origin);
    ImmutableSet<Path> g3Files = listFiles(destination);
    Map<Path, PathAndScore> destinationToOriginMapping =
        getDestinationToOriginMapping(gitFiles, g3Files);

    // Map of Origin file paths to destination file paths.
    // If multiple destination file map to the same origin file, we preserve the mapping with the
    // highest score.
    Map<Path, Path> similarFiles =
        destinationToOriginMapping.entrySet().stream()
            .collect(
                Collectors.groupingBy(
                    e -> e.getValue().path(),
                    collectingAndThen(
                        Collectors.maxBy(comparingInt(e -> e.getValue().score())),
                        e -> e.orElseThrow().getKey())));

    IncludesGlob originGlob = getOriginGlob(gitFiles, similarFiles, g3Files);
    ImmutableList<GeneratorMove> moves = generateMoves(similarFiles);
    DestinationExcludePaths destinationExcludePaths =
        new DestinationExcludePaths(
            getDestinationExcludePaths(g3Files, similarFiles, destinationOnlyPaths));
    Optional<String> tagSeparator = getVersionStringSeparator(versions);

    return new ConfigGenHeuristics.Result(
        originGlob.glob,
        new GeneratorTransformations(moves),
        destinationExcludePaths,
        tagSeparator.isPresent(),
        tagSeparator);
  }

  /**
   * Generates a mapping of destination files to origin files.
   *
   * @param gitFiles The list of paths in the origin
   * @param g3Files The list of paths in the destination
   * @return a map of destination to origin files, with their similarity scores
   * @throws IOException if there is an error reading the origin or destination files
   */
  protected Map<Path, PathAndScore> getDestinationToOriginMapping(
      ImmutableSet<Path> gitFiles, ImmutableSet<Path> g3Files) throws IOException {
    SimilarityDetector similarityDetector =
        SimilarityDetector.create(
            origin,
            gitFiles,
            destinationOnlyPaths,
            percentSimilar,
            ignoreCarriageReturn,
            ignoreWhitespace);
    // Map of destination file paths to origin file paths with similarity score.
    Map<Path, PathAndScore> destinationToOriginMapping = new TreeMap<>();

    for (Path file : g3Files) {
      Optional<PathAndScore> originPathAndScore =
          similarityDetector.find(destination.resolve(file));
      // If we find an origin file with a higher similarity score to the destination file, map
      // that origin file instead to the destination file.
      originPathAndScore.ifPresent(
          pathAndScore ->
              destinationToOriginMapping.merge(
                  file,
                  pathAndScore,
                  BinaryOperator.maxBy(Comparator.comparingInt(PathAndScore::score))));
    }
    return destinationToOriginMapping;
  }

  private Optional<String> getVersionStringSeparator(ImmutableList<String> versions) {
    HashMap<String, Integer> separators = new HashMap<>();
    if (versions.isEmpty()) {
      return Optional.empty();
    }

    for (String version : versions) {
      Matcher matcher = UPSTREAM_VERSION_REF_REGEX.matcher(version);
      if (matcher.matches() && matcher.groupCount() >= 3) {
        separators.put(matcher.group(3), separators.getOrDefault(matcher.group(3), 0) + 1);
      }
    }

    return separators.entrySet().stream().max(Entry.comparingByValue()).map(Entry::getKey);
  }

  private IncludesGlob getOriginGlob(
      ImmutableSet<Path> gitFiles, Map<Path, Path> similarFiles, ImmutableSet<Path> g3Files) {

    HashSet<Path> originOnly = new HashSet<>(gitFiles);
    originOnly.removeAll(similarFiles.keySet());

    HashSet<Path> destinationOnly = new HashSet<>(g3Files);
    destinationOnly.removeAll(similarFiles.values());

    IncludesGlob originGlob =
        new IncludesGlob(ImmutableSet.of("**"), ImmutableSet.of())
            .minimizeScore(similarFiles.keySet(), originOnly, 0);

    // Enable to debug what is being generated:
    if (generalOptions.isVerbose()) {
      debug(similarFiles, destinationOnly, originGlob);
    }

    return consolidateCommonPattern(
        originGlob, similarFiles.keySet(), p -> p.startsWith("."), ".**");
  }

  /**
   * Returns the set of files that are in the destination but not in the origin. This is the union
   * the known destinationOnlyPaths and the files that are in g3Files but not in similarFiles.
   */
  private ImmutableSet<Path> getDestinationExcludePaths(
      ImmutableSet<Path> g3Files,
      Map<Path, Path> similarFiles,
      ImmutableSet<Path> destinationOnlyPaths) {
    return Sets.union(
            destinationOnlyPaths,
            g3Files.stream().filter(p -> !similarFiles.containsValue(p)).collect(toImmutableSet()))
        .immutableCopy();
  }

  /**
   * Generates the minimal amount of core.moves from to map files from the origin to the
   * destination.
   *
   * <p>The general algorithm is the following, for each origin/destination file: - Find the common
   * suffix (e.g. for a/b/c/d.txt and x/y/z/c/d.txt it is c/d.txt). If we remove that suffix from
   * the origin and destination (a/b and x/y/z), this would be the more general move that we can do
   * (core.move(a/b, x/y/z)). The heuristic here is that more general moves are always better.
   *
   * <p>- But there is a problem. We need to make sure that this very general move doesn't move
   * other files to incorrect locations. So now we go over all the files, and for anything with that
   * prefix, we check that it would move to the correct destination. If all fine, we add this as the
   * set of moves and remove all the files with that prefix (as we have just handled all).
   *
   * <p>- If any has a wrong destination move, then we move to the next more general suffix (e.g.
   * c/d.txt -> d.txt) and we retry the whole thing again.
   *
   * <p>- In the extreme, if we cannot find a directory move, we add a move from file to file.
   *
   * <p>The algorithm is a bit brutal as is quadratic on these checks. If performance is an issue we
   * can see how to fix it. The expectation is that normally this should be fast because we find
   * common directories that move large amount of files.
   */
  private ImmutableList<GeneratorMove> generateMoves(Map<Path, Path> similarFiles) {
    ArrayDeque<Entry<Path, Path>> set = new ArrayDeque<>(similarFiles.entrySet());
    MovesTrie result = new MovesTrie();
    files:
    while (!set.isEmpty()) {
      Entry<Path, Path> entry = set.remove();
      Path origin = entry.getKey();
      Path dest = entry.getValue();
      if (origin.equals(dest)) {
        // already correctly positioned
        continue;
      }
      Path commonSuffix = commonSuffix(origin, dest);
      while (commonSuffix != null && !commonSuffix.toString().isEmpty()) {
        Path originPrefix =
            commonSuffix.getNameCount() != origin.getNameCount()
                ? origin.subpath(0, origin.getNameCount() - commonSuffix.getNameCount())
                : Path.of("");
        Path destPrefix =
            commonSuffix.getNameCount() != dest.getNameCount()
                ? dest.subpath(0, dest.getNameCount() - commonSuffix.getNameCount())
                : Path.of("");
        boolean tooBroad = false;
        HashSet<Entry<Path, Path>> includedPaths = new HashSet<>();
        for (Entry<Path, Path> e : similarFiles.entrySet()) {
          if (e.getKey().startsWith(originPrefix)) {
            if (destPrefix.resolve(
                    e.getKey().subpath(originPrefix.getNameCount(), e.getKey().getNameCount()))
                .equals(e.getValue())) {
              includedPaths.add(e);
            } else {
              tooBroad = true;
              break;
            }
          }
        }
        if (tooBroad) {
          if (commonSuffix.getNameCount() == 1) {
            commonSuffix = commonSuffix.getParent(); // 'foo' -> "", subpath doesn't work here.
          } else {
            commonSuffix = commonSuffix.subpath(1, commonSuffix.getNameCount());
          }
        } else {
          // Successfully moves a bunch of files with a directory move
          set.removeAll(includedPaths);
          result.insertMove(new GeneratorMove(originPrefix.toString(), destPrefix.toString()));
          continue files;
        }
      }
      result.insertMove(new GeneratorMove(origin.toString(), dest.toString()));
    }
    return result.getMovesInOrder();
  }

  private void debug(
      Map<Path, Path> similarFiles, HashSet<Path> destinationOnly, IncludesGlob originGlob) {
    Console console = generalOptions.console();
    for (Map.Entry<Path, Path> e : similarFiles.entrySet()) {
      console.verbose(e.getKey() + " -> " + e.getValue());
    }

    console.verbose("git_files = " + originGlob.glob);

    for (Path path : destinationOnly) {
      console.verbose("G3 Only: " + path);
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

  /**
   * Detects similar files based on hash and similarity.
   *
   * <p>This is used to find similar files in the origin and destination, and map them together.
   */
  protected static class SimilarityDetector {

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

    private Optional<PathAndScore> find(Path path) throws IOException {
      if (destinationOnlyPaths.contains(path.getFileName())) {
        return Optional.empty();
      }

      byte[] content = Files.readAllBytes(path);

      // Highest priority same hash. RenameDetector fails for small files

      Optional<Path> hashFinding =
          hashBased.get(hash(content)).stream()
              .max(comparingInt(o -> commonSuffix(path, o).getNameCount()));
      if (hashFinding.isPresent()) {
        return hashFinding.map(p -> new PathAndScore(p, RenameDetector.MAX_SCORE));
      }

      // Second priority similarity
      RenameDetector.Score<Path> score =
          Iterables.getFirst(
              similarLines.scoresForLaterFile(new ByteArrayInputStream(content)), null);
      if (score != null && score.getScore() > RenameDetector.MAX_SCORE * percentSimilar / 100) {
        return Optional.of(new PathAndScore(score.getKey(), score.getScore()));
      }
      return Optional.empty();
    }

    private static SimilarityDetector create(
        Path parent,
        ImmutableSet<Path> files,
        ImmutableSet<Path> destinationOnlyPaths,
        int percentSimilar,
        boolean ignoreCarriageReturn,
        boolean ignoreWhitespace)
        throws IOException {
      RenameDetector<Path> similarLines =
          new RenameDetector<>(ignoreCarriageReturn, ignoreWhitespace);
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

  /** Represents a core.move() to be included in the generation */
  public static class GeneratorMove {

    private final String before;
    private final String after;

    public GeneratorMove(String before, String after) {
      this.before = before;
      this.after = after;
    }

    public String getBefore() {
      return before;
    }

    public String getAfter() {
      return after;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof GeneratorMove)) {
        return false;
      }
      GeneratorMove that = (GeneratorMove) o;
      return Objects.equal(before, that.before)
          && Objects.equal(after, that.after);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(before, after);
    }

    @Override
    public String toString() {
      return "core.move(\"" + before + "\", \"" + after + "\")";
    }
  }

  /** Represents a collection of transformations to be included in the generation */
  public static class GeneratorTransformations {
    private final ImmutableList<GeneratorMove> moves;

    public GeneratorTransformations(ImmutableList<GeneratorMove> moves) {
      this.moves = moves;
    }

    public ImmutableList<GeneratorMove> getMoves() {
      return moves;
    }
  }

  /**
   * Represents a collection of paths that are found to only be present in the destination. This
   * should be a union of the given destinationOnlyPaths and files found to only exist in the
   * destination.
   */
  public static class DestinationExcludePaths {
    private final ImmutableSet<Path> paths;

    public DestinationExcludePaths(ImmutableSet<Path> paths) {
      this.paths = paths;
    }

    public ImmutableSet<Path> getPaths() {
      return paths;
    }

    @Override
    public String toString() {
      return paths.stream().map(Path::toString).collect(joining(", "));
    }
  }

  /**
   * A prefix tree (trie) used to maintain the order of core.move transforms, such that each
   * transform does not interfere with the operations of another. This is accomplished by using the
   * prefix tree to return transforms that move child directories/files before those that move
   * parent directories in the origin.
   *
   * <p>Consider the following scenario, where you have two transforms: <code>
   * core.move("", "src/main")
   * </code> and <code>core.move("res/META-INF/default.license", "LICENSE")</code>
   *
   * <p>In this case, you want <code>core.move("res/META-INF/default.license", "LICENSE")</code> to
   * run before <code>core.move("", "src/main")</code>. If <code>core.move("", "src/main")</code>
   * runs first, then <code>
   * res/META-INF/default.license</code> will be moved to <code>
   * src/main/res/META-INF/default.license</code>, and the remaining transform will error out with a
   * no-op.
   */
  private static class MovesTrie {
    private final MovesTrieNode root;

    private static class MovesTrieNode {
      private final ImmutableMap.Builder<Path, MovesTrieNode> children = ImmutableMap.builder();
      private Optional<GeneratorMove> move = Optional.empty();

      private MovesTrieNode() {}

      private Optional<GeneratorMove> getMove() {
        return move;
      }

      private void setMove(GeneratorMove move) {
        this.move = Optional.of(move);
      }

      private void addChildren(Path path, MovesTrieNode node) {
        children.put(path, node);
      }

      private ImmutableMap<Path, MovesTrieNode> getChildren() {
        return children.buildKeepingLast();
      }
    }

    private MovesTrie() {
      this.root = new MovesTrieNode();
    }

    private void insertMove(GeneratorMove move) {
      MovesTrieNode currentNode = root;

      // Edge case - if the before path is an empty path, this means it's moving the root.
      if (move.getBefore().isEmpty()) {
        currentNode.setMove(move);
        return;
      }

      for (Path names : Path.of(move.getBefore())) {
        if (!currentNode.getChildren().containsKey(names)) {
          MovesTrieNode newNode = new MovesTrieNode();
          currentNode.addChildren(names, newNode);
        }
        currentNode = currentNode.getChildren().get(names);
      }
      currentNode.setMove(move);
    }

    private ImmutableList<GeneratorMove> getMovesInOrder() {
      return getMovesInOrder(root);
    }

    private ImmutableList<GeneratorMove> getMovesInOrder(MovesTrieNode startNode) {
      ImmutableList.Builder<GeneratorMove> moves = ImmutableList.builder();

      for (MovesTrieNode child : startNode.getChildren().values()) {
        moves.addAll(getMovesInOrder(child));
      }

      startNode.getMove().ifPresent(moves::add);
      return moves.build();
    }
  }
}
