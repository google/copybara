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

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.DestinationReader;
import com.google.copybara.Transformation;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitEnvironment;
import com.google.copybara.git.GitRepository;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RustModuleTest {
  SkylarkTestExecutor starlark;
  private TestingConsole console;
  private OptionsBuilder optionsBuilder;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    optionsBuilder = new OptionsBuilder();
    console = new TestingConsole();
    workdir = Files.createTempDirectory("workdir");
    optionsBuilder
        .setConsole(console)
        .setWorkdirToRealTempDir(workdir.toString())
        .setHomeDir(Files.createTempDirectory("home").toString());
    starlark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testGetVersionRequirement() throws Exception {
    boolean result =
        starlark.eval(
            "result",
            "result = rust.create_version_requirement(requirement ="
                + " \"1.8.0\").fulfills('1.8.0')");
    assertThat(result).isTrue();
  }

  @Test
  public void testDownloadCrateFuzzers_noCargoVcsJsonFile() throws Exception {
    Path cratePath = workdir.resolve("foo_crate_v1");
    Files.createDirectories(cratePath);
    String cargoToml = "[package]\n" + "repository = \"http://copy/bara\"";
    Files.writeString(cratePath.resolve("Cargo.toml"), cargoToml);

    runTransformation(
        "t = core.dynamic_transform(lambda ctx: rust.download_fuzzers(ctx = ctx, crate_path ="
            + " \"foo_crate_v1\"))");

    console
        .assertThat()
        .logContains(
            MessageType.WARNING,
            "Not downloading fuzzers. Cargo.toml or .cargo_vcs_info.json doesn't exist in the"
                + " crate's source file.");
  }

  @Test
  public void testDownloadCrateFuzzers_missingGitRepo() throws Exception {
    Path cratePath = workdir.resolve("foo_crate_v1");
    Files.createDirectories(cratePath);
    String cargoToml = "";
    Files.writeString(cratePath.resolve("Cargo.toml"), cargoToml);
    String cargoVcsJson =
        """
        {
          "git": {
            "sha1": "test"
          },
          "path_in_vcs": "foo"
        }\
        """;
    Files.writeString(cratePath.resolve(".cargo_vcs_info.json"), cargoVcsJson);

    runTransformation(
        "t = core.dynamic_transform(lambda ctx: rust.download_fuzzers(ctx = ctx, crate_path ="
            + " \"foo_crate_v1\"))");

    console
        .assertThat()
        .logContains(
            MessageType.WARNING,
            "Not downloading fuzzers. The URL is missing in Cargo.toml. If you wish to override"
                + " this, Pass in a URL manually using the repo_url parameter.");
  }

  @Test
  public void testDownloadCrateFuzzers_missingGitFieldInVcsJson() throws Exception {
    Path cratePath = workdir.resolve("foo_crate_v1");
    Files.createDirectories(cratePath);
    String cargoToml = "[package]\n" + "repository = \"http://copy/bara\"";
    Files.writeString(cratePath.resolve("Cargo.toml"), cargoToml);
    String cargoVcsJson =
        """
        {
          "path_in_vcs": "foo"
        }\
        """;
    Files.writeString(cratePath.resolve(".cargo_vcs_info.json"), cargoVcsJson);

    runTransformation(
        "t = core.dynamic_transform(lambda ctx: rust.download_fuzzers(ctx = ctx, crate_path ="
            + " \"foo_crate_v1\"))");

    console
        .assertThat()
        .logContains(
            MessageType.WARNING,
            "Not downloading fuzzers. The SHA1 reference is not available in"
                + " .cargo_vcs_info.json.");
  }

  @Test
  public void testDownloadCrateFuzzers_missingSha1Field() throws Exception {
    Path cratePath = workdir.resolve("foo_crate_v1");
    Files.createDirectories(cratePath);
    String cargoToml = "[package]\nrepository = \"http://foo\"";
    Files.writeString(cratePath.resolve("Cargo.toml"), cargoToml);
    String cargoVcsJson =
        """
        {
          "git": {},
          "path_in_vcs": "foo"
        }\
        """;
    Files.writeString(cratePath.resolve(".cargo_vcs_info.json"), cargoVcsJson);

    runTransformation(
        "t = core.dynamic_transform(lambda ctx: rust.download_fuzzers(ctx = ctx, crate_path ="
            + " \"foo_crate_v1\"))");

    console
        .assertThat()
        .logContains(
            MessageType.WARNING,
            "Not downloading fuzzers. The SHA1 reference is not available in"
                + " .cargo_vcs_info.json.");
  }

  private void runTransformation(String config)
      throws IOException, ValidationException, RepoException {
    starlark
        .<Transformation>eval("t", config)
        .transform(
            TransformWorks.of(
                    workdir,
                    "test",
                    optionsBuilder.general.console(),
                    DestinationReader.NOOP_DESTINATION_READER)
                .withCurrentRev(new DummyRevision("1.0.0")));
  }

  @Test
  public void testDownloadCrateFuzzers_missingSha1() throws Exception {
    Path cratePath = workdir.resolve("foo_crate_v1");
    Files.createDirectories(cratePath);
    String cargoToml = "[package]\nrepository = \"http://foo/bar\"";
    Files.writeString(cratePath.resolve("Cargo.toml"), cargoToml);
    String cargoVcsJson = "{}";
    Files.writeString(cratePath.resolve(".cargo_vcs_info.json"), cargoVcsJson);

    runTransformation(
        "t = core.dynamic_transform(lambda ctx: rust.download_fuzzers(ctx = ctx, crate_path ="
            + " \"foo_crate_v1\"))");

    console
        .assertThat()
        .logContains(
            MessageType.WARNING,
            "Not downloading fuzzers. The SHA1 reference is not available in"
                + " .cargo_vcs_info.json.");
  }

  @Test
  public void testDownloadCrateFuzzers() throws Exception {
    // Set up remote Git repo with fuzzers
    Path cratePath = workdir.resolve("foo_crate_v1");
    setUpRepoAndCheckout(cratePath, "fuzz", "None", true, "");

    assertThat(Files.exists(cratePath.resolve("fuzz/foo.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("fuzz/bar.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("ignore.rs"))).isFalse();
    console.assertThat().onceInLog(MessageType.INFO, "fuzz_path: foo_crate_v1/fuzz");
  }

  @Test
  public void testDownloadCrateFuzzers_differentRelativePath() throws Exception {
    // Set up remote Git repo with fuzzers
    Path cratePath = workdir.resolve("foo_crate_v1");
    // Relative path with slash at the end. Should have the same behavior as the more common ".."
    String parentLocation = "../";
    setUpRepoAndCheckout(cratePath, "fuzz", "None", true, "", parentLocation);

    assertThat(Files.exists(cratePath.resolve("fuzz/foo.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("fuzz/bar.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("ignore.rs"))).isFalse();
    console.assertThat().onceInLog(MessageType.INFO, "fuzz_path: foo_crate_v1/fuzz");
  }

  @Test
  public void testDownloadCrateFuzzers_usingCargoVcsInfoPath() throws Exception {
    Path cratePath = workdir.resolve("foo_crate_v1");
    setUpRepoAndCheckout(cratePath, "fuzzbara/fuzz", "None", true, "fuzzbara");

    assertThat(Files.exists(cratePath.resolve("fuzz/foo.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("fuzz/bar.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("ignore.rs"))).isFalse();
    console.assertThat().onceInLog(MessageType.INFO, "fuzz_path: foo_crate_v1/fuzz");
  }

  @Test
  public void testDownloadCrateFuzzers_parentCrateIsNotDep() throws Exception {
    // Set up remote Git repo with fuzzers
    Path cratePath = workdir.resolve("foo_crate_v1");
    setUpRepoAndCheckout(cratePath, "fuzz", "None", false, "");

    assertThat(Files.exists(cratePath.resolve("fuzz"))).isFalse();

    console
        .assertThat()
        .onceInLog(
            MessageType.INFO, "Not downloading fuzzers. This crate doesn't have any fuzzers.");
  }

  @Test
  public void testDownloadCrateFuzzers_sha1ReferenceNotFound() throws Exception {
    Path cratePath = workdir.resolve("foo_crate_v1");
    Path remote = Files.createTempDirectory("remote");
    String url = "file://" + remote.toFile().getAbsolutePath();
    GitRepository repo = getRepo(remote);
    repo.simpleCommand("config", "user.name", "Foo");
    repo.simpleCommand("config", "user.email", "foo@bar.com");

    GitTestUtil.writeFile(remote, "fuzz/foo.rs", "test1");
    String fuzzCargoToml = "[package.metadata]\ncargo-fuzz = true\n";
    GitTestUtil.writeFile(remote, "fuzz/Cargo.toml", fuzzCargoToml);

    GitTestUtil.writeFile(remote, "ignore.rs", "test3");
    repo.add().all().run();
    repo.git(remote, "commit", "-m", "first commit");

    // Set up cargo.toml with Git repo info
    Files.createDirectories(cratePath);
    String cargoToml = String.format("[package]\n" + "repository = \"%s\"", url);
    String cargoVcsJson =
        String.format(
            """
            {
              "git": {
                "sha1": "%s"
              },
              "path_in_vcs": "%s"
            }\
            """,
            "should_not_exist", "");
    Files.writeString(cratePath.resolve("Cargo.toml"), cargoToml);
    Files.writeString(cratePath.resolve(".cargo_vcs_info.json"), cargoVcsJson);

    // Run download_fuzzers in a transform
    runTransformation(
        "def test_download_fuzz(ctx):\n"
            + "   fuzz_path = rust.download_fuzzers(ctx = ctx, crate_path ="
            + " \"foo_crate_v1\", fuzz_excludes = None, crate_name = \"foo-crate-v1\")\n"
            + "   ctx.console.info(\"fuzz_path: \" + fuzz_path.path if fuzz_path else"
            + " \"None\")\n"
            + "t = core.dynamic_transform(lambda ctx: test_download_fuzz(ctx))");
    console
        .assertThat()
        .onceInLog(
            MessageType.WARNING,
            "Unable to download fuzzers. Failed to resolve the SHA1 reference in the upstream"
                + " repo.");
  }

  private GitRepository getRepo(Path remote) throws RepoException {
    return GitRepository.newRepo(
            true, remote, new GitEnvironment(optionsBuilder.general.getEnvironment()))
        .init();
  }

  @Test
  public void testDownloadCrateFuzzers_fuzzExcludes() throws Exception {
    // Set up remote Git repo with fuzzers
    Path cratePath = workdir.resolve("foo_crate_v1");
    setUpRepoAndCheckout(cratePath, "fuzz", "[\"bar.rs\", \"baz/foo.rs\"]", true, "");

    assertThat(Files.exists(cratePath.resolve("fuzz/foo.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("fuzz/bar.rs"))).isFalse();
    assertThat(Files.exists(cratePath.resolve("fuzz/baz/foo.rs"))).isFalse();
    assertThat(Files.exists(cratePath.resolve("ignore.rs"))).isFalse();
    console.assertThat().onceInLog(MessageType.INFO, "fuzz_path: foo_crate_v1/fuzz");
  }

  @Test
  public void testDownloadCrateFuzzers_differentFuzzDir() throws Exception {
    // Set up remote Git repo with fuzzers
    Path cratePath = workdir.resolve("foo_crate_v1");
    setUpRepoAndCheckout(cratePath, "fuzzbara", "None", true, "");

    assertThat(Files.exists(cratePath.resolve("fuzzbara/foo.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("fuzzbara/bar.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("ignore.rs"))).isFalse();
    console.assertThat().onceInLog(MessageType.INFO, "fuzz_path: foo_crate_v1/fuzzbara");
  }

  @Test
  public void testDownloadCrateFuzzers_overrideGitRepo() throws Exception {
    Path remote = Files.createTempDirectory("remote");
    Path cratePath = workdir.resolve("foo_crate_v1");
    String url = "file://" + remote.toFile().getAbsolutePath();
    GitRepository repo = getRepo(remote);
    repo.simpleCommand("config", "user.name", "Foo");
    repo.simpleCommand("config", "user.email", "foo@bar.com");

    String fuzzersDir = "fuzz";
    GitTestUtil.writeFile(remote, String.format("%s/foo.rs", fuzzersDir), "test1");
    String fuzzCargoToml = "[package.metadata]\ncargo-fuzz = true\n";
    GitTestUtil.writeFile(remote, String.format("%s/Cargo.toml", fuzzersDir), fuzzCargoToml);

    GitTestUtil.writeFile(remote, "ignore.rs", "test3");
    repo.add().all().run();
    repo.git(remote, "commit", "-m", "first commit");

    // Bad URL in the manifest
    Files.createDirectories(cratePath);
    String cargoToml = "[package]\n" + "repository = \"https://do/not/use\"";
    String cargoVcsJson =
        String.format(
            """
            {
              "git": {
                "sha1": "%s"
              },
              "path_in_vcs": ""
            }\
            """,
            repo.getHeadRef().getSha1());
    Files.writeString(cratePath.resolve("Cargo.toml"), cargoToml);
    Files.writeString(cratePath.resolve(".cargo_vcs_info.json"), cargoVcsJson);

    // We define the correct URL below as a starlark argument.
    // This should not throw.
    runTransformation(
        String.format(
            "def test_download_fuzz(ctx):\n"
                + "   fuzz_path = rust.download_fuzzers(ctx = ctx, crate_path ="
                + " \"foo_crate_v1\", repo_url = \"%s\", crate_name = \"foo-crate-v1\")\n"
                + "t = core.dynamic_transform(lambda ctx: test_download_fuzz(ctx))",
            url));
  }

  private void setUpRepoAndCheckout(
      Path cratePath, String fuzzersDir, String excludes, boolean defineParentDep, String vcsPath)
      throws IOException, RepoException, ValidationException {
    setUpRepoAndCheckout(cratePath, fuzzersDir, excludes, defineParentDep, vcsPath, "..");
  }

  private void setUpRepoAndCheckout(
      Path cratePath,
      String fuzzersDir,
      String excludes,
      boolean defineParentDep,
      String vcsPath,
      String parentLocation)
      throws IOException, RepoException, ValidationException {
    Path remote = Files.createTempDirectory("remote");
    String url = "file://" + remote.toFile().getAbsolutePath();
    GitRepository repo = getRepo(remote);
    repo.simpleCommand("config", "user.name", "Foo");
    repo.simpleCommand("config", "user.email", "foo@bar.com");

    GitTestUtil.writeFile(remote, String.format("%s/foo.rs", fuzzersDir), "test1");
    GitTestUtil.writeFile(remote, String.format("%s/bar.rs", fuzzersDir), "test2");
    GitTestUtil.writeFile(remote, String.format("%s/baz/foo.rs", fuzzersDir), "test3");
    String fuzzCargoToml = "[package.metadata]\ncargo-fuzz = true\n";
    if (defineParentDep) {
      fuzzCargoToml +=
          String.format("[dependencies.foo-crate-v1]\npath = \"%s\"\n", parentLocation);
    }
    GitTestUtil.writeFile(remote, String.format("%s/Cargo.toml", fuzzersDir), fuzzCargoToml);

    GitTestUtil.writeFile(remote, "ignore.rs", "test3");
    repo.add().all().run();
    repo.git(remote, "commit", "-m", "first commit");

    // Set up cargo.toml with Git repo info
    Files.createDirectories(cratePath);
    String cargoToml = String.format("[package]\n" + "repository = \"%s\"", url);
    String cargoVcsJson =
        String.format(
            """
            {
              "git": {
                "sha1": "%s"
              },
              "path_in_vcs": "%s"
            }\
            """,
            repo.getHeadRef().getSha1(), vcsPath);
    Files.writeString(cratePath.resolve("Cargo.toml"), cargoToml);
    Files.writeString(cratePath.resolve(".cargo_vcs_info.json"), cargoVcsJson);

    // Run download_fuzzers in a transform
    runTransformation(
        String.format(
            "def test_download_fuzz(ctx):\n"
                + "   fuzz_path = rust.download_fuzzers(ctx = ctx, crate_path ="
                + " \"foo_crate_v1\", fuzz_excludes = %s, crate_name = \"foo-crate-v1\")\n"
                + "   ctx.console.info(\"fuzz_path: \" + fuzz_path.path if fuzz_path else"
                + " \"None\")\n"
                + "t = core.dynamic_transform(lambda ctx: test_download_fuzz(ctx))",
            excludes));
  }
}
