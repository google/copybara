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

package com.google.copybara.onboard;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.GeneralOptions;
import com.google.copybara.configgen.ConfigGenHeuristics;
import com.google.copybara.configgen.ConfigGenHeuristics.DestinationExcludePaths;
import com.google.copybara.configgen.ConfigGenHeuristics.GeneratorTransformations;
import com.google.copybara.configgen.ConfigGenHeuristics.Result;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.FuzzyClosestVersionSelector;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitRepository;
import com.google.copybara.git.GitRevision;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.Input;
import com.google.copybara.onboard.core.InputProvider;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.remotefile.HttpStreamFactory;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.remotefile.extractutil.ExtractType;
import com.google.copybara.remotefile.extractutil.ExtractUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An input provider that uses the origin and destination content information to infer several
 * fields like the origin_files glob.
 */
public class ConfigHeuristicsInputProvider implements InputProvider {

  private static final Glob INCLUDE_EXCLUDE_NOOP =
      Glob.createGlob(ImmutableList.of("**"), ImmutableList.of("**"));

  @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull"})
  private Optional<Result> cached = null;

  private CannotProvideException cachedException = null;

  private final GitOptions gitOptions;
  private final GeneralOptions generalOptions;
  private final GeneratorOptions generatorOptions;
  private final RemoteFileOptions remoteFileOptions;
  private final ImmutableSet<Path> destinationOnlyPaths;
  private final int percentSimilar;
  private final Console console;
  private final DestinationPathProvider destinationPathProvider;

  public ConfigHeuristicsInputProvider(
      GitOptions gitOptions,
      GeneralOptions generalOptions,
      GeneratorOptions generatorOptions,
      RemoteFileOptions remoteFileOptions,
      ImmutableSet<Path> destinationOnlyPaths,
      int percentSimilar,
      Console console,
      DestinationPathProvider destinationPathProvider) {
    this.gitOptions = gitOptions;
    this.generalOptions = generalOptions;
    this.generatorOptions = generatorOptions;
    this.remoteFileOptions = remoteFileOptions;
    this.destinationOnlyPaths = destinationOnlyPaths;
    this.percentSimilar = percentSimilar;
    this.console = console;
    this.destinationPathProvider = destinationPathProvider;
  }

  @Override
  public <T> Optional<T> resolve(Input<T> input, InputProviderResolver db)
      throws InterruptedException, CannotProvideException {
    Path destination = destinationPathProvider.resolve(db);
    Optional<Result> result = computeHeuristic(db, destination);
    if (result.isEmpty()) {
      return Optional.empty();
    }
    if (input == Inputs.ORIGIN_GLOB) {
      Glob resultGlob = result.get().getOriginGlob();
      return resultGlob.equals(INCLUDE_EXCLUDE_NOOP)
          ? Optional.empty()
          : Optional.of(Inputs.ORIGIN_GLOB.asValue(resultGlob));
    }
    if (input == Inputs.TRANSFORMATIONS) {
      GeneratorTransformations transformations = result.get().getTransformations();
      return Optional.of(Inputs.TRANSFORMATIONS.asValue(transformations));
    }
    if (input == Inputs.DESTINATION_EXCLUDE_PATHS) {
      DestinationExcludePaths destinationExcludePaths = result.get().getDestinationExcludePaths();
      boolean optimizeGlobs = generatorOptions.optimizeGlobs;
      if (optimizeGlobs) {
        // We filter out paths containing "AUTOPATCHES" unconditionally here. This optimizes the
        // generated globs by excluding autopatch paths, which are intended to be managed by
        // Copybara rather than explicitly listed as excludes.
        ImmutableSet<Path> filtered =
            destinationExcludePaths.getPaths().stream()
                .filter(p -> !p.toString().contains("AUTOPATCHES"))
                .collect(toImmutableSet());
        destinationExcludePaths = new DestinationExcludePaths(filtered);
      }
      return Optional.of(Inputs.DESTINATION_EXCLUDE_PATHS.asValue(destinationExcludePaths));
    }
    return Optional.empty();
  }

  @SuppressWarnings("OptionalAssignedToNull")
  protected Optional<Result> computeHeuristic(InputProviderResolver db, Path destination)
      throws InterruptedException, CannotProvideException {
    if (cachedException != null) {
      throw cachedException;
    }
    if (!Files.isDirectory(destination)) {
      return Optional.empty();
    }
    if (cached != null) {
      return cached;
    }

    String originForLog = "unknown";
    try {
      Path origin = generalOptions.getDirFactory().newTempDir("checkout");
      ImmutableList<String> upstreamTags = ImmutableList.of();

      OriginUrls urls = resolveOriginUrls(db);
      URL archiveUrl = urls.archiveUrl;
      URL originUrl = urls.originUrl;

      if (archiveUrl != null) {
        originForLog = archiveUrl.toString();
        String unpackMethod = db.resolve(Inputs.UNPACK_METHOD);
        console.infoFmt("Downloading archive from %s", archiveUrl);
        HttpStreamFactory transport = remoteFileOptions.getTransport();
        try (InputStream inputStream = transport.open(archiveUrl, null)) {
          ExtractType extractType;
          try {
            extractType = ExtractType.valueOf(Ascii.toUpperCase(unpackMethod));
          } catch (IllegalArgumentException e) {
            throw new ValidationException(
                String.format("Invalid unpack method '%s'", unpackMethod), e);
          }
          ExtractUtil.extractArchive(inputStream, origin, extractType, null);
        }

        // Verify that the archive was extracted successfully.
        long fileCount = 0;
        try (Stream<Path> stream = Files.walk(origin)) {
          fileCount = stream.filter(Files::isRegularFile).count();
        } catch (IOException e) {
          console.warnFmt("Failed to iterate files in extracted archive: %s", e.getMessage());
        }
        console.infoFmt("Extracted %d files from remote archive", fileCount);
        if (fileCount == 0) {
          console.warnFmt(
              "Remote archive yielded 0 files! This might indicate download/unpack failure.");
        }
      } else if (originUrl != null) {
        originForLog = originUrl.toString();
        String currentVersion = db.resolve(Inputs.CURRENT_VERSION);

        GitRepository repo =
            gitOptions.cachedBareRepoForUrl(originUrl.toString()).withWorkTree(origin);

        FuzzyClosestVersionSelector selector = new FuzzyClosestVersionSelector();
        currentVersion =
            selector.selectVersion(currentVersion, repo, originUrl.toString(), console);

        console.progressFmt("Fetching '%s' from %s", currentVersion, originUrl.toString());
        GitRevision gitRevision;
        try {
          gitRevision =
              repo.fetchSingleRefWithTags(
                  originUrl.toString(),
                  currentVersion,
                  /* fetchTags= */ true,
                  /* partialFetch= */ false,
                  Optional.empty());
        } catch (RepoException e) {
          gitRevision =
              repo.fetchSingleRef(
                  originUrl.toString(),
                  currentVersion,
                  /* partialFetch= */ false,
                  Optional.empty());
        }
        Path git = Files.createDirectories(origin);
        upstreamTags =
            repo.showRef().keySet().stream()
                .filter(ref -> ref.startsWith("refs/tags/"))
                .collect(toImmutableList());

        console.progressFmt("Checking out git files");
        repo.withWorkTree(git).forceCheckout(gitRevision.getHash());
      } else {
        throw new CannotProvideException(
            "Neither GIT_ORIGIN_URL nor REMOTE_ARCHIVE_URL was provided.");
      }

      ConfigGenHeuristics heuristics =
          getConfigGenHeuristics(
              destination,
              origin,
              destinationOnlyPaths,
              percentSimilar,
              generatorOptions,
              generalOptions,
              upstreamTags);

      console.progressFmt("Computing globs");
      cached = Optional.of(heuristics.run());
      return cached;

    } catch (ValidationException | IOException | RepoException e) {
      cachedException =
          new CannotProvideException(
              String.format(
                  "Cannot compute heuristics for repository %s: %s", originForLog, e.getMessage()),
              e);
      throw cachedException;
    }
  }

  /** Represents the resolved origin URLs. */
  protected static class OriginUrls {
    public final URL archiveUrl;
    public final URL originUrl;

    public OriginUrls(URL archiveUrl, URL originUrl) {
      this.archiveUrl = archiveUrl;
      this.originUrl = originUrl;
    }
  }

  /** Resolves the origin URLs from the resolver. */
  protected OriginUrls resolveOriginUrls(InputProviderResolver db)
      throws InterruptedException, CannotProvideException {
    // 1. Peek silently to see if a Git URL is already known (e.g. from flags or file).
    URL originUrl = db.resolveOptional(Inputs.GIT_ORIGIN_URL).orElse(null);
    URL archiveUrl = null;

    // 2. If no Git URL found, peek silently for an Archive URL.
    if (originUrl == null) {
      archiveUrl = db.resolveOptional(Inputs.REMOTE_ARCHIVE_URL).orElse(null);
    }

    // 3. If still nothing found silently, force resolution (may prompt user) based on template.
    if (originUrl == null && archiveUrl == null) {
      if (Objects.equals(generatorOptions.getTemplate(), "remote_archive_to_third_party")) {
        archiveUrl = db.resolve(Inputs.REMOTE_ARCHIVE_URL);
      } else {
        originUrl = db.resolve(Inputs.GIT_ORIGIN_URL);
      }
    }
    return new OriginUrls(archiveUrl, originUrl);
  }

  /**
   * Returns a {@link ConfigGenHeuristics} object.
   *
   * @param destination the local path to the destination
   * @param origin the local path to the origin
   * @param destinationOnlyPaths paths that should be considered destination-only, and excluded from
   *     heuristics
   * @param percentSimilar the threshold for considering an origin file similar enough to a
   *     destination file
   * @param generatorOptions the generator options from {@link com.google.copybara.Options}
   * @param generalOptions the general options from {@link com.google.copybara.Options}
   * @param versions a list of version refs from the upstream
   * @return the object
   */
  protected ConfigGenHeuristics getConfigGenHeuristics(
      Path destination,
      Path origin,
      ImmutableSet<Path> destinationOnlyPaths,
      int percentSimilar,
      GeneratorOptions generatorOptions,
      GeneralOptions generalOptions,
      ImmutableList<String> versions) {
    return new ConfigGenHeuristics(
        origin,
        destination,
        destinationOnlyPaths,
        percentSimilar,
        generatorOptions.computeGlobIgnoreCarriageReturn,
        generatorOptions.computeGlobIgnoreWhitespace,
        generalOptions,
        versions);
  }

  @Override
  public ImmutableMap<Input<?>, Integer> provides() throws CannotProvideException {
    return defaultPriority(
        ImmutableSet.of(
            Inputs.ORIGIN_GLOB, Inputs.TRANSFORMATIONS, Inputs.DESTINATION_EXCLUDE_PATHS));
  }

  /**
   * Resolves a destination path for glob generation heuristics. This allows the destination path to
   * be different than the generator output folder, if needed.
   */
  @FunctionalInterface
  public interface DestinationPathProvider {
    Path resolve(InputProviderResolver db) throws InterruptedException, CannotProvideException;
  }
}
