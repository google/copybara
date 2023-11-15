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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.CheckoutPath;
import com.google.copybara.GeneralOptions;
import com.google.copybara.TransformWork;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestinationReader;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitRepository;
import com.google.copybara.git.GitRevision;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.toml.TomlContent;
import com.google.copybara.toml.TomlModule;
import com.google.copybara.util.Glob;
import com.google.copybara.version.VersionResolver;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Optional;
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
        @Param(name = "crate", named = true, doc = "The name of the crate, e.g. \"libc\"")
      })
  @Example(
      title = "Create a version list for a given rust crate",
      before = "Example: creating a version list for libc",
      code = "rust.crates_io_version_list(\n" + "crate = \"libc\"\n)")
  public RustCratesIoVersionList getRustCratesIoVersionList(String crateName) {
    return RustCratesIoVersionList.forCrate(crateName, remoteFileOptions);
  }

  @StarlarkMethod(
      name = "crates_io_version_resolver",
      doc = "A version resolver for Rust crates from crates.io",
      documented = false,
      parameters = {@Param(name = "crate", named = true, doc = "The name of the rust crate.")})
  @SuppressWarnings("unused")
  public VersionResolver getResolver(String crate) {
    return new RustCratesIoVersionResolver(crate, remoteFileOptions);
  }

  @StarlarkMethod(
      name = "create_version_requirement",
      doc =
          "Represents a Cargo version requirement. You can compare version strings against this"
              + "object to determine if they meet this requirement or not. ",
      documented = true,
      parameters = {
        @Param(name = "requirement", named = true, doc = "The Cargo version requirement"),
      })
  @Example(
      title = "Create a version requirement object",
      before = "Example:  Create a requirement object and compare a version string against it.",
      code =
          "rust.create_version_requirement(\">= 0.5\")")
  @SuppressWarnings("unused")
  public RustVersionRequirement getVersionRequirement(String requirement)
      throws ValidationException {
    return RustVersionRequirement.getVersionRequirement(requirement);
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
    return RustVersionRequirement.getVersionRequirement(requirement).fulfills(version);
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
      },
      allowReturnNones = true)
  @Nullable
  public CheckoutPath downloadRustFuzzers(
      TransformWork ctx, String crateDir, Object maybeFuzzExcludes, Object crateName)
      throws EvalException, RepoException, ValidationException {
    try (ProfilerTask ignore = generalOptions.profiler().start("rust_download_fuzzers")) {
      Glob originGlob = Glob.createGlob(ImmutableList.of("**/Cargo.toml"));
      // Read in the repository info from Cargo.toml and .cargo_vcs_info.json
      Path cratePath = ctx.getCheckoutDir().resolve(crateDir);
      Path cargoTomlPath = cratePath.resolve("Cargo.toml");
      Path cargoVcsInfoJsonPath = cratePath.resolve(".cargo_vcs_info.json");
      Path tmpCheckoutPath = generalOptions.getDirFactory().newTempDir("fuzz_checkout");
      if (!(Files.exists(cargoTomlPath) && Files.exists(cargoVcsInfoJsonPath))) {
        ctx.getConsole()
            .warn(
                "Not downloading fuzzers. Cargo.toml or .cargo_vcs_info.json doesn't exist in the"
                    + " crate's source files.");
        return null;
      }

      String url = getFuzzersDownloadUrl(cargoTomlPath);
      JsonObject vcsJsonObject =
          JsonParser.parseString(Files.readString(cargoVcsInfoJsonPath)).getAsJsonObject();
      if (Strings.isNullOrEmpty(url)
          || !vcsJsonObject.has("git")
          || !((JsonObject) vcsJsonObject.get("git")).has("sha1")) {
        ctx.getConsole().warn("Not downloading fuzzers. URL or sha1 reference are not available.");
        return null;
      }

      String sha1 = ((JsonObject) vcsJsonObject.get("git")).get("sha1").getAsString();
      ctx.getConsole().infoFmt("Downloading fuzzers from %s at ref %s", url, sha1);
      GitRepository repo = gitOptions.cachedBareRepoForUrl(url);
      GitRevision rev = repo.fetchSingleRef(url, sha1, true, Optional.empty());
      GitDestinationReader destinationReader = new GitDestinationReader(repo, rev, cratePath);

      Optional<Path> maybeFuzzCargoTomlPath =
          getMaybeFuzzCargoTomlPath(
              tmpCheckoutPath,
              destinationReader,
              Optional.ofNullable(SkylarkUtil.convertOptionalString(crateName)));

      if (maybeFuzzCargoTomlPath.isEmpty()) {
        ctx.getConsole().info("Not downloading fuzzers. This crate doesn't have any fuzzers.");
      } else {
        Path fuzzerPath = maybeFuzzCargoTomlPath.get().getParent();
        StarlarkList<String> exclude =
            SkylarkUtil.convertFromNoneable(maybeFuzzExcludes, StarlarkList.empty());
        destinationReader.copyDestinationFilesToDirectory(
            Glob.createGlob(
                ImmutableList.of(String.format("%s/**", fuzzerPath)),
                exclude.stream()
                    .map(e -> String.format("%s/%s", fuzzerPath, e))
                    .collect(toImmutableList())),
            cratePath);
        return ctx.newPath(
            ctx.getCheckoutDir().relativize(cratePath.resolve(fuzzerPath.toString())).toString());
      }

      return null;
    } catch (IOException e) {
      throw new ValidationException("Failed to obtain Rust fuzzers from Git.", e);
    }
  }

  @StarlarkMethod(
      name = "crates_io_version_selector",
      doc =
          "Returns a version selector that selects the latest version of a crate based on a version"
              + " requirement. e.g. \"1.2\" selects 1.2.3, 1.2.4, but not 1.3.0.",
      documented = false,
      parameters = {
        @Param(name = "requirement", named = true, doc = "The Cargo version requirement"),
      })
  @SuppressWarnings("unused")
  public RustCratesIoVersionSelector getCratesIoVersionSelector(String requirement)
      throws ValidationException {
    return new RustCratesIoVersionSelector(
        RustVersionRequirement.getVersionRequirement(requirement));
  }

  private Optional<Path> getMaybeFuzzCargoTomlPath(
      Path tmpCheckoutPath, GitDestinationReader destinationReader, Optional<String> maybeCrateName)
      throws RepoException, IOException, ValidationException, EvalException {
    Glob cargoTomlGlob = Glob.createGlob(ImmutableList.of("**/Cargo.toml"));
    destinationReader.copyDestinationFilesToDirectory(cargoTomlGlob, tmpCheckoutPath);
    PathMatcher pathMatcher = cargoTomlGlob.relativeTo(tmpCheckoutPath);
    ImmutableList<Path> cargoTomlFiles;
    Optional<Path> maybeCargoTomlPath = Optional.empty();
    try (Stream<Path> stream = Files.walk(tmpCheckoutPath)) {
      cargoTomlFiles =
          stream
              .filter(Files::isRegularFile)
              .filter(pathMatcher::matches)
              .collect(toImmutableList());
    }

    for (Path path : cargoTomlFiles) {
      if (isCargoTomlCargoFuzz(path, maybeCrateName)) {
        maybeCargoTomlPath = Optional.of(tmpCheckoutPath.relativize(path));
        break;
      }
    }
    return maybeCargoTomlPath;
  }

  protected String getFuzzersDownloadUrl(Path cargoTomlPath)
      throws ValidationException, EvalException, IOException {
    return (String)
        new TomlModule()
            .parse(Files.readString(cargoTomlPath))
            .getOrDefault("package.repository", "");
  }

  private boolean isCargoTomlCargoFuzz(Path cargoTomlPath, Optional<String> maybeCrateName)
      throws IOException, ValidationException, EvalException {
    TomlContent parsedToml = new TomlModule().parse(Files.readString(cargoTomlPath));
    boolean isFuzzerForCrate =
        (boolean) parsedToml.getOrDefault("package.metadata.cargo-fuzz", false);

    if (maybeCrateName.isPresent()) {
      String depPath =
          (String)
              parsedToml.getOrDefault(
                  String.format("dependencies.%s.path", maybeCrateName.get()), null);

      isFuzzerForCrate &= (depPath != null && depPath.equals(".."));
    }

    return isFuzzerForCrate;
  }
}
