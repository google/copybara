/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.rust;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;

import com.google.common.collect.ImmutableList;
import com.google.copybara.CheckoutPath;
import com.google.copybara.DestinationInfo;
import com.google.copybara.GeneralOptions;
import com.google.copybara.TransformWork;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestinationReader;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitRepository;
import com.google.copybara.git.GitRevision;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.toml.TomlContent;
import com.google.copybara.toml.TomlModule;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import com.google.copybara.util.Glob;
import com.google.copybara.version.VersionResolver;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/** A module for importing Rust crates from crates.io. */
@StarlarkBuiltin(name = "rust", doc = "A module for importing Rust crates", documented = false)
public class RustModule implements StarlarkValue {
  private final RemoteFileOptions remoteFileOptions;
  private final GitOptions gitOptions;
  private final GeneralOptions generalOptions;

  public RustModule(
      RemoteFileOptions remoteFileOptions, GitOptions gitOptions, GeneralOptions generalOptions) {
    this.remoteFileOptions = remoteFileOptions;
    this.gitOptions = gitOptions;
    this.generalOptions = generalOptions;
  }

  @StarlarkMethod(
      name = "crates_io_version_list",
      doc = "Returns a crates.io version_list object",
      documented = false,
      parameters = {
        @Param(name = "crate", named = true, doc = "The name of the crate, e.g. \"libc\""),
        @Param(
            name = "match_pre_release_versions",
            named = true,
            doc =
                "Whether we should match pre-release versions of a crate when finding the latest"
                    + " version.",
            defaultValue = "False"),
        @Param(
            name = "auth",
            doc = "Optional, an interceptor for providing credentials.",
            named = true,
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = AuthInterceptor.class),
              @ParamType(type = NoneType.class)
            },
            positional = false),
      })
  @Example(
      title = "Create a version list for a given rust crate",
      before = "Example: creating a version list for libc",
      code = "rust.crates_io_version_list(\n" + "crate = \"libc\"\n)")
  public RustCratesIoVersionList getRustCratesIoVersionList(
      String crateName, boolean matchPreReleaseVersions, Object auth) {
    return RustCratesIoVersionList.forCrate(
        crateName, remoteFileOptions, matchPreReleaseVersions, convertFromNoneable(auth, null));
  }

  @StarlarkMethod(
      name = "crates_io_version_resolver",
      doc = "A version resolver for Rust crates from crates.io",
      documented = false,
      parameters = {
        @Param(name = "crate", named = true, doc = "The name of the rust crate."),
        @Param(
            name = "match_pre_release_versions",
            named = true,
            doc =
                "Whether we should match pre-release versions of a crate when finding the latest"
                    + " version.",
            defaultValue = "False"),
        @Param(
            name = "auth",
            doc = "Optional, an interceptor for providing credentials.",
            named = true,
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = AuthInterceptor.class),
              @ParamType(type = NoneType.class)
            },
            positional = false),
      })
  @SuppressWarnings("unused")
  public VersionResolver getResolver(String crate, boolean matchPreReleaseVersions, Object auth) {
    return new RustCratesIoVersionResolver(
        crate, remoteFileOptions, matchPreReleaseVersions, convertFromNoneable(auth, null));
  }

  @StarlarkMethod(
      name = "create_version_requirement",
      doc =
          "Represents a Cargo version requirement. You can compare version strings against this"
              + "object to determine if they meet this requirement or not. ",
      documented = true,
      parameters = {
        @Param(name = "requirement", named = true, doc = "The Cargo version requirement"),
        @Param(name = "allow_epochs", named = true, defaultValue = "False", doc = "Allow epoch version requirements"),
      })
  @Example(
      title = "Create a version requirement object",
      before = "Example:  Create a requirement object and compare a version string against it.",
      code =
          "rust.create_version_requirement(\">= 0.5\")")
  @SuppressWarnings("unused")
  public RustVersionRequirement getVersionRequirement(String requirement, boolean allowEpochs)
      throws ValidationException {
    return RustVersionRequirement.getVersionRequirement(requirement, allowEpochs);
  }

  @StarlarkMethod(
      name = "check_version_requirement",
      doc =
          "Checks a version against a Cargo version requirement. Currently, default, caret, and"
              + " comparison requirements are supported. Please see"
              + " https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html for more"
              + " information.",
      documented = false,
      parameters = {
        @Param(name = "requirement", named = true, doc = "The Cargo version requirement"),
        @Param(name = "version", named = true, doc = "The version to check")
      })
  public boolean checkVersionRequirement(String requirement, String version)
      throws ValidationException {
    // TODO(chriscampos): Remove this in favor of getVersionRequirement
    return RustVersionRequirement.getVersionRequirement(requirement, false).fulfills(version);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "download_fuzzers",
      doc =
          "Downloads the crate fuzzers from the upstream Git"
              + " source. It does this by using the repository path and SHA1 defined in the"
              + " manifest files (e.g. Cargo.toml).",
      documented = false,
      parameters = {
        @Param(name = "ctx", doc = "The TransformWork object", named = true),
        @Param(
            name = "crate_path",
            doc = "The path to the crate, relative to the checkout directory.",
            named = true),
        @Param(
            name = "fuzz_excludes",
            doc = "A list of glob patterns to exclude from the fuzz directory.",
            allowedTypes = {
              @ParamType(type = NoneType.class),
              @ParamType(type = StarlarkList.class, generic1 = String.class)
            },
            named = true,
            defaultValue = "None"),
        @Param(
            name = "crate_name",
            doc = "The name of the crate, used to find the correct fuzzer directory.",
            named = true,
            defaultValue = "None"),
        @Param(
            name = "repo_url",
            doc =
                "The Git repository to fetch the fuzzers from. This is optional. If not defined,"
                    + " the package.repository value is used instead.",
            named = true,
            allowedTypes = {@ParamType(type = NoneType.class), @ParamType(type = String.class)},
            defaultValue = "None"),
      },
      allowReturnNones = true)
  @Nullable
  public CheckoutPath downloadRustFuzzers(
      TransformWork ctx,
      String crateDir,
      Object maybeFuzzExcludes,
      Object crateName,
      Object fuzzersRepoUrl)
      throws EvalException, RepoException, ValidationException {
    try (ProfilerTask ignore = generalOptions.profiler().start("rust_download_fuzzers")) {
      Glob originGlob = Glob.createGlob(ImmutableList.of("**/Cargo.toml"));
      // Read in the repository info from Cargo.toml and .cargo_vcs_info.json
      Path cratePath = ctx.getCheckoutDir().resolve(crateDir);
      Path cargoTomlPath = cratePath.resolve("Cargo.toml");
      Path cargoVcsInfoJsonPath = cratePath.resolve(".cargo_vcs_info.json");
      if (!(Files.exists(cargoTomlPath) && Files.exists(cargoVcsInfoJsonPath))) {
        ctx.getConsole()
            .warn(
                "Not downloading fuzzers. Cargo.toml or .cargo_vcs_info.json doesn't exist in the"
                    + " crate's source files.");
        return null;
      }

      Optional<String> url = getUrlFromCargoToml(fuzzersRepoUrl, cargoTomlPath);
      JsonObject vcsJsonObject =
          JsonParser.parseString(Files.readString(cargoVcsInfoJsonPath)).getAsJsonObject();
      Optional<String> sha1 = getSha1FromCargoVcsJson(vcsJsonObject);

      if (url.isEmpty()) {
        ctx.getConsole()
            .warn(
                "Not downloading fuzzers. The URL is missing in Cargo.toml. If you wish to override"
                    + " this, Pass in a URL manually using the repo_url parameter.");
        return null;
      }

      if (sha1.isEmpty()) {
        ctx.getConsole()
            .warn(
                "Not downloading fuzzers. The SHA1 reference is not available in"
                    + " .cargo_vcs_info.json.");
        return null;
      }

      ctx.getConsole().infoFmt("Downloading fuzzers from %s at ref %s", url.get(), sha1.get());
      GitRepository repo = gitOptions.cachedBareRepoForUrl(url.get());
      Optional<String> maybeCrateName =
          Optional.ofNullable(SkylarkUtil.convertOptionalString(crateName));
      GitRevision rev =
          getGitRevision(url.get(), sha1.get(), repo, ctx.getDestinationInfo(), maybeCrateName);
      GitDestinationReader destinationReader = new GitDestinationReader(repo, rev, cratePath);

      String relativePath = getPathInVcs(vcsJsonObject).orElse("");
      Optional<String> fuzzersDir = getFuzzersDir(destinationReader, maybeCrateName, relativePath);

      if (fuzzersDir.isEmpty()) {
        ctx.getConsole().info("Not downloading fuzzers. This crate doesn't have any fuzzers.");
      } else {
        return copyFuzzersToWorkdir(
            ctx,
            convertFromNoneable(maybeFuzzExcludes, StarlarkList.empty()),
            cratePath,
            destinationReader,
            relativePath,
            fuzzersDir.get());
      }
    } catch (CannotResolveRevisionException e) {
      ctx.getConsole()
          .warnFmt(
              "Unable to download fuzzers. Failed to resolve the SHA1 reference in the upstream"
                  + " repo. Cause: %s",
              e);
    } catch (IOException e) {
      logError(ctx, e);
      throw new ValidationException("Failed to obtain Rust fuzzers from Git.", e);
    } catch (ValidationException | RepoException e) {
      logError(ctx, e);
      throw e;
    }

    return null;
  }

  protected Optional<String> getSha1FromCargoVcsJson(JsonObject vcsJsonObject) {
    if (vcsJsonObject.has("git") && ((JsonObject) vcsJsonObject.get("git")).has("sha1")) {
      String sha1 = ((JsonObject) vcsJsonObject.get("git")).get("sha1").getAsString();
      // Filter out empty strings.
      return Optional.ofNullable(sha1).filter(Predicate.not(String::isEmpty));
    }
    return Optional.empty();
  }

  protected Optional<String> getUrlFromCargoToml(Object fuzzersRepoUrl, Path cargoTomlPath)
      throws ValidationException, EvalException, IOException {
    Optional<String> url =
        Optional.ofNullable(
                convertFromNoneable(fuzzersRepoUrl, getFuzzersDownloadUrl(cargoTomlPath)))
            .filter(Predicate.not(String::isEmpty));

    if (url.isPresent()) {
      url = Optional.of(normalizeUrl(url.get()));
    }

    return url;
  }

  protected GitRevision getGitRevision(
      String url,
      String sha1,
      GitRepository repo,
      DestinationInfo destinationInfo,
      Optional<String> crateName)
      throws RepoException, ValidationException {
    return repo.fetchSingleRef(url, sha1, true, Optional.empty());
  }

  protected static void logError(TransformWork ctx, Exception e) {
    ctx.getConsole()
        .errorFmt("There was an error downloading Rust fuzzers. Error: %s", e.getMessage());
  }

  private CheckoutPath copyFuzzersToWorkdir(
      TransformWork ctx,
      List<String> exclude,
      Path checkoutCratePath,
      GitDestinationReader destinationReader,
      String relativePath,
      String fuzzersDir)
      throws IOException, RepoException, EvalException {
    Path tmpDir = generalOptions.getDirFactory().newTempDir("fuzz_copy");
    String fullGitFuzzersDir = Path.of(relativePath, fuzzersDir).toString();
    destinationReader.copyDestinationFilesToDirectory(
        Glob.createGlob(
            ImmutableList.of(String.format("%s/**", fullGitFuzzersDir)),
            exclude.stream()
                .map(e -> String.format("%s/%s", fullGitFuzzersDir, e))
                .collect(toImmutableList())),
        tmpDir);

    // Copy the fuzzers to the checkout directory.
    // We resolve the path against the "path_in_vcs" value to get the crate root folder in the
    // upstream repo.
    // This is necessary as some repos contain more than one crate, so we need to find the crate
    // root we are interested in to copy files to the checkout dir.
    Path gitCratePath = tmpDir.resolve(relativePath);
    // This gives us the path to the fuzzers relative to the upstream crate root.
    String fuzzersDirectory = gitCratePath.relativize(tmpDir.resolve(fullGitFuzzersDir)).toString();
    FileUtil.copyFilesRecursively(
        gitCratePath, checkoutCratePath, CopySymlinkStrategy.IGNORE_INVALID_SYMLINKS);

    // We return the location of the fuzzers
    return ctx.newPath(
        ctx.getCheckoutDir().relativize(checkoutCratePath.resolve(fuzzersDirectory)).toString());
  }

  /**
   * Gets the subdirectory that contains the crate in the upstream repo from .cargo_vcs_info.json.
   */
  private static Optional<String> getPathInVcs(JsonObject vcsJsonObject) {
    if (!vcsJsonObject.has("path_in_vcs")) {
      return Optional.empty();
    }

    String pathToVcs = vcsJsonObject.get("path_in_vcs").getAsString();
    return Optional.ofNullable(emptyToNull(pathToVcs));
  }

  @StarlarkMethod(
      name = "crates_io_version_selector",
      doc =
          "Returns a version selector that selects the latest version of a crate based on a version"
              + " requirement. e.g. \"1.2\" selects 1.2.3, 1.2.4, but not 1.3.0.",
      documented = false,
      parameters = {
        @Param(name = "requirement", named = true, doc = "The Cargo version requirement"),
        @Param(name = "allow_epochs", named = true, defaultValue = "False", doc = "Allow epoch version requirements"),

      })
  @SuppressWarnings("unused")
  public RustCratesIoVersionSelector getCratesIoVersionSelector(String requirement, boolean allowEpochs)
      throws ValidationException {
    return new RustCratesIoVersionSelector(
        RustVersionRequirement.getVersionRequirement(requirement, allowEpochs));
  }

  /** Gets the location of the fuzzers in the upstream repo. */
  private Optional<String> getFuzzersDir(
      GitDestinationReader destinationReader, Optional<String> crateName, String relativePath)
      throws RepoException, IOException, ValidationException, EvalException {
    // Limit the Cargo.toml files we analyze to the upstream repo directory mentioned in the crate's
    // manifest.
    Glob cargoTomlGlob =
        Glob.createGlob(ImmutableList.of(Path.of(relativePath, "**/Cargo.toml").toString()));
    Path tmpCheckoutPath = generalOptions.getDirFactory().newTempDir("fuzz_checkout");
    destinationReader.copyDestinationFilesToDirectory(cargoTomlGlob, tmpCheckoutPath);
    PathMatcher pathMatcher = cargoTomlGlob.relativeTo(tmpCheckoutPath);
    ImmutableList<Path> cargoTomlFiles;
    Optional<String> fuzzersDir = Optional.empty();
    try (Stream<Path> stream = Files.walk(tmpCheckoutPath)) {
      cargoTomlFiles =
          stream
              .filter(Files::isRegularFile)
              .filter(pathMatcher::matches)
              .collect(toImmutableList());
    }

    for (Path path : cargoTomlFiles) {
      if (isCargoTomlCargoFuzz(path, crateName)) {
        fuzzersDir =
            Optional.of(
                tmpCheckoutPath.resolve(relativePath).relativize(path.getParent()).toString());
        break;
      }
    }
    return fuzzersDir;
  }

  protected String getFuzzersDownloadUrl(Path cargoTomlPath)
      throws ValidationException, EvalException, IOException {
    return (String)
        new TomlModule()
            .parse(Files.readString(cargoTomlPath))
            .getOrDefault("package.repository", "");
  }

  private static String normalizeUrl(String url) throws ValidationException {
    if (GitHubHost.GITHUB_COM.isGitHubUrl(url)) {
      url = GitHubHost.GITHUB_COM.normalizeUrl(url);
    }
    return url;
  }

  private boolean isCargoTomlCargoFuzz(Path cargoTomlPath, Optional<String> crateName)
      throws IOException, ValidationException, EvalException {
    TomlContent parsedToml = new TomlModule().parse(Files.readString(cargoTomlPath));
    boolean isFuzzerForCrate =
        (boolean) parsedToml.getOrDefault("package.metadata.cargo-fuzz", false);

    if (crateName.isPresent()) {
      String depPath =
          (String)
              parsedToml.getOrDefault(String.format("dependencies.%s.path", crateName.get()), null);

      isFuzzerForCrate &=
          (depPath != null
              && cargoTomlPath
                  .getParent()
                  .resolve(depPath)
                  .normalize()
                  .equals(cargoTomlPath.getParent().resolve("..").normalize()));
    }

    return isFuzzerForCrate;
  }
}
