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

    starlark
        .<Transformation>eval(
            "t",
            "t = core.dynamic_transform(lambda ctx: rust.download_fuzzers(ctx = ctx, crate_path ="
                + " \"foo_crate_v1\"))")
        .transform(
            TransformWorks.of(
                    workdir,
                    "test",
                    optionsBuilder.general.console(),
                    DestinationReader.NOOP_DESTINATION_READER)
                .withCurrentRev(new DummyRevision("1.0.0")));

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
        "{\n"
            + "  \"git\": {\n"
            + "    \"sha1\": \"test\"\n"
            + "  },\n"
            + "  \"path_in_vcs\": \"foo\"\n"
            + "}";
    Files.writeString(cratePath.resolve(".cargo_vcs_info.json"), cargoVcsJson);

    starlark
        .<Transformation>eval(
            "t",
            "t = core.dynamic_transform(lambda ctx: rust.download_fuzzers(ctx = ctx, crate_path ="
                + " \"foo_crate_v1\"))")
        .transform(
            TransformWorks.of(
                    workdir,
                    "test",
                    optionsBuilder.general.console(),
                    DestinationReader.NOOP_DESTINATION_READER)
                .withCurrentRev(new DummyRevision("1.0.0")));

    console
        .assertThat()
        .logContains(
            MessageType.WARNING,
            "Not downloading fuzzers. URL or sha1 reference are not available.");
  }

  @Test
  public void testDownloadCrateFuzzers_missingSha1() throws Exception {
    Path cratePath = workdir.resolve("foo_crate_v1");
    Files.createDirectories(cratePath);
    String cargoToml = "[package]\nrepository = \"http://foo/bar\"";
    Files.writeString(cratePath.resolve("Cargo.toml"), cargoToml);
    String cargoVcsJson = "{}";
    Files.writeString(cratePath.resolve(".cargo_vcs_info.json"), cargoVcsJson);

    starlark
        .<Transformation>eval(
            "t",
            "t = core.dynamic_transform(lambda ctx: rust.download_fuzzers(ctx = ctx, crate_path ="
                + " \"foo_crate_v1\"))")
        .transform(
            TransformWorks.of(
                    workdir,
                    "test",
                    optionsBuilder.general.console(),
                    DestinationReader.NOOP_DESTINATION_READER)
                .withCurrentRev(new DummyRevision("1.0.0")));

    console
        .assertThat()
        .logContains(
            MessageType.WARNING,
            "Not downloading fuzzers. URL or sha1 reference are not available.");
  }

  @Test
  public void testDownloadCrateFuzzers() throws Exception {
    // Set up remote Git repo with fuzzers
    Path cratePath = workdir.resolve("foo_crate_v1");
    setUpRepoAndCheckout(cratePath, "fuzz", "None");

    assertThat(Files.exists(cratePath.resolve("fuzz/foo.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("fuzz/bar.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("ignore.rs"))).isFalse();
    console.assertThat().onceInLog(MessageType.INFO, "fuzz_path: foo_crate_v1/fuzz");
  }

  @Test
  public void testDownloadCrateFuzzers_fuzzExcludes() throws Exception {
    // Set up remote Git repo with fuzzers
    Path cratePath = workdir.resolve("foo_crate_v1");
    setUpRepoAndCheckout(cratePath, "fuzz", "[\"bar.rs\", \"baz/foo.rs\"]");

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
    setUpRepoAndCheckout(cratePath, "fuzzbara", "None");

    assertThat(Files.exists(cratePath.resolve("fuzzbara/foo.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("fuzzbara/bar.rs"))).isTrue();
    assertThat(Files.exists(cratePath.resolve("ignore.rs"))).isFalse();
    console.assertThat().onceInLog(MessageType.INFO, "fuzz_path: foo_crate_v1/fuzzbara");
  }

  private void setUpRepoAndCheckout(Path cratePath, String fuzzersDir, String excludes)
      throws IOException, RepoException, ValidationException {
    Path remote = Files.createTempDirectory("remote");
    String url = "file://" + remote.toFile().getAbsolutePath();
    GitRepository repo =
        GitRepository.newRepo(
                true, remote, new GitEnvironment(optionsBuilder.general.getEnvironment()))
            .init();
    repo.simpleCommand("config", "user.name", "Foo");
    repo.simpleCommand("config", "user.email", "foo@bar.com");

    GitTestUtil.writeFile(remote, String.format("%s/foo.rs", fuzzersDir), "test1");
    GitTestUtil.writeFile(remote, String.format("%s/bar.rs", fuzzersDir), "test2");
    GitTestUtil.writeFile(remote, String.format("%s/baz/foo.rs", fuzzersDir), "test3");
    GitTestUtil.writeFile(
        remote,
        String.format("%s/Cargo.toml", fuzzersDir),
        "[package.metadata]\ncargo-fuzz = true");
    GitTestUtil.writeFile(remote, "ignore.rs", "test3");
    repo.add().all().run();
    repo.git(remote, "commit", "-m", "first commit");

    // Set up cargo.toml with Git repo info
    Files.createDirectories(cratePath);
    String cargoToml = String.format("[package]\n" + "repository = \"%s\"", url);
    String cargoVcsJson =
        String.format(
            "{\n"
                + "  \"git\": {\n"
                + "    \"sha1\": \"%s\"\n"
                + "  },\n"
                + "  \"path_in_vcs\": \"foo\"\n"
                + "}",
            repo.getHeadRef().getSha1());
    Files.writeString(cratePath.resolve("Cargo.toml"), cargoToml);
    Files.writeString(cratePath.resolve(".cargo_vcs_info.json"), cargoVcsJson);

    // Run download_fuzzers in a transform
    starlark
        .<Transformation>eval(
            "t",
            String.format(
                "def test_download_fuzz(ctx):\n"
                    + "   fuzz_path = rust.download_fuzzers(ctx = ctx, crate_path"
                    + " = \"foo_crate_v1\", fuzz_excludes = %s)\n"
                    + "   ctx.console.info(\"fuzz_path: \" + fuzz_path.path)\n"
                    + "t = core.dynamic_transform(lambda ctx: test_download_fuzz(ctx))",
                excludes))
        .transform(
            TransformWorks.of(
                    workdir,
                    "test",
                    optionsBuilder.general.console(),
                    DestinationReader.NOOP_DESTINATION_READER)
                .withCurrentRev(new DummyRevision("1.0.0")));
  }
}
