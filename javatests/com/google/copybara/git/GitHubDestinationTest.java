/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.git.github.api.GitHubApiException.ResponseCode.FORBIDDEN;
import static com.google.copybara.testing.git.GitTestUtil.mockResponse;
import static com.google.copybara.testing.git.GitTestUtil.mockResponseWithStatus;
import static com.google.copybara.testing.git.GitTestUtil.writeFile;
import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Change;
import com.google.copybara.Changes;
import com.google.copybara.Destination.Writer;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.api.GitHubApiException;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.DummyChecker;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.testing.git.GitTestUtil.CompleteRefValidator;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.devtools.build.lib.syntax.EvalException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

//TODO(HUANHUANCHEN): Move those private functions to a util class if we need more functions from
// gitDestinationTest in next cl.
@RunWith(JUnit4.class)
public class GitHubDestinationTest {

  private OptionsBuilder options;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  private String url;
  private String fetch;
  private String push;
  private boolean force;

  private Glob destinationFiles;
  private Path workdir;
  private GitTestUtil gitUtil;
  private GitRepository remote;
  @Before
  public void setup() throws Exception {
    console = new TestingConsole();
    options =
        new OptionsBuilder()
            .setConsole(console)
            .setOutputRootToTmpDir();
    options.testingOptions.checker = new DummyChecker(ImmutableSet.of("bad_word"));
    workdir = Files.createTempDirectory("workdir");
    destinationFiles = Glob.createGlob(ImmutableList.of("**"));
    gitUtil = new GitTestUtil(options);
    gitUtil.mockRemoteGitRepos(new CompleteRefValidator());
    remote = gitUtil.mockRemoteRepo("github.com/foo");
    options.gitDestination = new GitDestinationOptions(options.general, options.git);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    url = "https://github.com/foo";
    force = false;
    fetch = "master";
    push = "master";
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testDryRun() throws Exception {
    fetch = "master";
    push = "master";
    addFiles(
        remote,
        "master",
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
    remote.simpleCommand("branch", "other");
    WriterContext writerContext =
        new WriterContext(
            "piper_to_github",
            "test",
            /*dryRun=*/ true,
            new DummyRevision("origin_ref1"));
    Writer<GitRevision> writer = destination().newWriter(writerContext);
    process(writer, new DummyRevision("origin_ref1"));

    GitTesting.assertThatCheckout(remote, "master")
        .containsFile("foo.txt", "foo")
        .containsNoMoreFiles();
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    // Run again without dry run
    writer = newWriter();
    process(writer, new DummyRevision("origin_ref1"));
    GitTesting.assertThatCheckout(remote, "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void testHttpUrl() throws Exception {
    GitDestination d = skylark.eval("r", "r = git.github_destination("
        + "    url = 'http://github.com/foo', \n"
        + ")");
    assertThat(d.describe(Glob.ALL_FILES).get("url")).contains("https://github.com/foo");
  }

  @Test
  public void testPrToUpdateWithRegularString() throws Exception {
    gitUtil.mockApi("GET",
        "https://api.github.com/repos/foo/git/refs/heads/other",
        mockResponse(
            "{\n"
                + "\"ref\" : \"refs/heads/test_existing_pr\",\n"
                + "\"node_id\" : \"MDM6UmVmcmVmcy9oZWFkcy9mZWF0dXJlQQ==\",\n"
                + "\"url\" : \"https://api.github.com/repos/octocat/Hello-World/git/refs/heads/test_existing_pr\",\n"
                + "\"object\" : {\n"
                + "         \"type\" : \"commit\",\n"
                + "         \"sha\" : \"aa218f56b14c9653891f9e74264a383fa43fefbd\",\n"
                + "         \"url\" : \"https://api.github.com/repos/octocat/Hello-World/git/commits/aa218f56b14c9653891f9e74264a383fa43fefbd\"\n"
                + "       }\n"
                + "}"));
    addFiles(
        remote,
        "master",
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
    remote.simpleCommand("branch", "other");
    GitTesting.assertThatCheckout(remote, "master")
        .containsFile("foo.txt", "foo")
        .containsNoMoreFiles();
    GitTesting.assertThatCheckout(remote, "other")
        .containsFile("foo.txt", "foo")
        .containsNoMoreFiles();
    WriterContext writerContext =
        new WriterContext(
            "piper_to_github",
            "test",
            /*dryRun=*/ false,
            new DummyRevision("origin_ref1"));
    writeFile(this.workdir, "test.txt", "some content");
    Writer<GitRevision> writer =
        destinationWithExistingPrBranch("other").newWriter(writerContext);
    process(writer, new DummyRevision("origin_ref1"));
    GitTesting.assertThatCheckout(remote, "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
    GitTesting.assertThatCheckout(remote, "other")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void testPrToUpdateWithLabel() throws Exception {
    gitUtil.mockApi("GET",
        "https://api.github.com/repos/foo/git/refs/heads/other_12345",
        mockResponse(
            "{\n"
              + "\"ref\" : \"refs/heads/test_existing_12345_pr\",\n"
              + "\"node_id\" : \"MDM6UmVmcmVmcy9oZWFkcy9mZWF0dXJlQQ==\",\n"
              + "\"url\" : \"https://api.github.com/repos/octocat/Hello-World/git/refs/heads/test_existing_12345_pr\",\n"
              + "\"object\" : {\n"
                + "         \"type\" : \"commit\",\n"
                + "         \"sha\" : \"aa218f56b14c9653891f9e74264a383fa43fefbd\",\n"
                + "         \"url\" : \"https://api.github.com/repos/octocat/Hello-World/git/commits/aa218f56b14c9653891f9e74264a383fa43fefbd\"\n"
                + "       }\n"
            + "}"));
    gitUtil.mockApi("GET",
        "https://api.github.com/repos/foo/git/refs/heads/other_6789",
        mockResponse(
            "{\n"
                + "\"ref\" : \"refs/heads/test_existing_6789_pr\",\n"
                + "\"node_id\" : \"MDM6UmVmcmVmcy9oZWFkcy9mZWF0dXJlQQ==\",\n"
                + "\"url\" : \"https://api.github.com/repos/octocat/Hello-World/git/refs/heads/test_existing_6789_pr\",\n"
                + "\"object\" : {\n"
                + "         \"type\" : \"commit\",\n"
                + "         \"sha\" : \"aa218f56b14c9653891f9e74264a383fa43fefbd\",\n"
                + "         \"url\" : \"https://api.github.com/repos/octocat/Hello-World/git/commits/aa218f56b14c9653891f9e74264a383fa43fefbd\"\n"
                + "       }\n"
                + "}"));
    addFiles(
        remote,
        "master",
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
    remote.simpleCommand("branch", "other_12345");
    remote.simpleCommand("branch", "other_6789");
    GitTesting.assertThatCheckout(remote, "master")
        .containsFile("foo.txt", "foo")
        .containsNoMoreFiles();
    GitTesting.assertThatCheckout(remote, "other_12345")
        .containsFile("foo.txt", "foo")
        .containsNoMoreFiles();
    GitTesting.assertThatCheckout(remote, "other_6789")
        .containsFile("foo.txt", "foo")
        .containsNoMoreFiles();

    writeFile(this.workdir, "test.txt", "some content");
    WriterContext writerContext =
        new WriterContext(
            "piper_to_github",
            "test",
            /*dryRun=*/ false,
            new DummyRevision("origin_ref1"));
    Writer<GitRevision> writer = destinationWithExistingPrBranch("other_${my_label}").newWriter(writerContext);
    process(writer, new DummyRevision("origin_ref1"));
    GitTesting.assertThatCheckout(remote, "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
    GitTesting.assertThatCheckout(remote, "other_12345")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
    GitTesting.assertThatCheckout(remote, "other_6789")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void testWithRefsNotFound() throws Exception {
    gitUtil.mockApi("GET",
        "https://api.github.com/repos/foo/git/refs/heads/other_12345",
        GitTestUtil.mockGitHubNotFound());
    gitUtil.mockApi("GET",
        "https://api.github.com/repos/foo/git/refs/heads/other_6789",
        GitTestUtil.mockGitHubNotFound());
    addFiles(
        remote,
        "master",
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
    GitTesting.assertThatCheckout(remote, "master")
        .containsFile("foo.txt", "foo")
        .containsNoMoreFiles();
    writeFile(this.workdir, "test.txt", "some content");
    WriterContext writerContext =
        new WriterContext(
            "piper_to_github",
            "test",
            /*dryRun=*/ false,
            new DummyRevision("origin_ref1"));
    Writer<GitRevision> writer = destinationWithExistingPrBranch("other_${my_label}").newWriter(writerContext);
    process(writer, new DummyRevision("origin_ref1"));
    GitTesting.assertThatCheckout(remote, "master")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
    console.assertThat().onceInLog(MessageType.INFO, "Branch other_12345 does not exist");
    console.assertThat().onceInLog(MessageType.INFO, "Branch other_6789 does not exist");
  }

  @Test
  public void testChecker() throws Exception {
    GitDestination d = skylark.eval("r", "r = git.github_destination("
        + "    url = 'http://github.com/example/example', \n"
        + "    api_checker = testing.dummy_checker(),\n"
        + ")");
    WriterContext writerContext =
        new WriterContext(
            "piper_to_github",
            "test",
            /*dryRun=*/ false,
            new DummyRevision("origin_ref1"));
    Writer<GitRevision> writer = d.newWriter(writerContext);
    GitHubEndPoint endpoint = (GitHubEndPoint) writer.getFeedbackEndPoint(console);
    try {
      endpoint.getCombinedStatus("bad_word", null);
      fail();
    } catch (EvalException e) {
      assertThat(e).hasMessageThat().contains("Bad word found");
    }
  }

  @Test
  public void testWithGitHubApiError() throws Exception {
    try {
      gitUtil.mockApi("GET",
          "https://api.github.com/repos/foo/git/refs/heads/other_12345",
          mockResponseWithStatus("", 403, any -> true));
      gitUtil.mockApi("GET",
          "https://api.github.com/repos/foo/git/refs/other_6789",
          mockResponseWithStatus("", 403, any -> true));
      addFiles(
          remote,
          "master",
          "first change",
          ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
      WriterContext writerContext =
          new WriterContext(
              "piper_to_github",
              "test",
              /*dryRun=*/ false,
              new DummyRevision("origin_ref1"));
      Writer<GitRevision> writer = destinationWithExistingPrBranch("other_${my_label}").newWriter(writerContext);
      process(writer, new DummyRevision("origin_ref1"));
      fail();
    } catch (GitHubApiException e) {
      Assert.assertSame(e.getResponseCode(), FORBIDDEN);
    }
  }

  @Test
  public void testWithLabelNotFound() throws Exception {
    try {
      addFiles(
          remote,
          "master",
          "first change",
          ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
      WriterContext writerContext =
          new WriterContext(
              "piper_to_github",
              "test",
              /*dryRun=*/ false,
              new DummyRevision("origin_ref1"));
      Writer<GitRevision> writer = destinationWithExistingPrBranch("other_${no_such_label}").newWriter(writerContext);
      process(writer, new DummyRevision("origin_ref1"));
      fail();
    } catch (ValidationException e) {
      Assert.assertTrue(e.getMessage().contains("Template 'other_${no_such_label}' has an error"));
    }
  }

  private void addFiles(GitRepository remote, String branch, String msg, Map<String, String> files)
      throws IOException, RepoException {
    Path temp = Files.createTempDirectory("temp");
    GitRepository tmpRepo = remote.withWorkTree(temp);
    if (branch != null) {
      if (tmpRepo.refExists(branch)) {
        tmpRepo.simpleCommand("checkout", branch);
      } else if (!branch.equals("master")) {
        tmpRepo.simpleCommand("branch", branch);
        tmpRepo.simpleCommand("checkout", branch);
      }
    }
    for (Entry<String, String> entry : files.entrySet()) {
      Path file = temp.resolve(entry.getKey());
      Files.createDirectories(file.getParent());
      Files.write(file, entry.getValue().getBytes(UTF_8));
    }
    tmpRepo.add().all().run();
    tmpRepo.simpleCommand("commit", "-m", msg);
  }

  private void process(Writer<GitRevision> writer, DummyRevision ref)
      throws ValidationException, RepoException, IOException {
    process(writer, destinationFiles, ref);
  }

  private Writer<GitRevision> newWriter() throws ValidationException {
    return destination().newWriter(
        new WriterContext(
            "piper_to_github",
            /*workflowIdentityUser=*/ "TEST",
            false,
            new DummyRevision("test")));
  }

  private GitDestination evalDestination()
      throws ValidationException {
    return skylark.eval("result",
        String.format("result = git.github_destination(\n"
            + "    url = '%s',\n"
            + "    fetch = '%s',\n"
            + "    push = '%s',\n"
            + ")", url, fetch, push));
  }

  private GitDestination destination() throws ValidationException {
    options.setForce(force);
    return evalDestination();
  }

  private GitDestination destinationWithExistingPrBranch(String prBranchToUpdate)
      throws ValidationException {
    options.setForce(force);
    return skylark.eval("result",
        String.format("result = git.github_destination(\n"
            + "    url = '%s',\n"
            + "    fetch = '%s',\n"
            + "    push = '%s',\n"
            + "    pr_branch_to_update = '%s',\n"
            + ")", url, fetch, push, prBranchToUpdate));
  }

  private void process(Writer<GitRevision> writer, Glob destinationFiles, DummyRevision originRef)
      throws ValidationException, RepoException, IOException {
    processWithBaseline(writer, destinationFiles, originRef, /*baseline=*/ null);
  }

  private void processWithBaseline(Writer<GitRevision> writer, Glob destinationFiles,
      DummyRevision originRef, String baseline)
      throws RepoException, ValidationException, IOException {
    processWithBaselineAndConfirmation(writer, destinationFiles, originRef, baseline,
        /*askForConfirmation*/false);
  }

  private void processWithBaselineAndConfirmation(Writer<GitRevision> writer,
      Glob destinationFiles, DummyRevision originRef, String baseline,
      boolean askForConfirmation)
      throws ValidationException, RepoException, IOException {
    TransformResult result = TransformResults.of(workdir, originRef);
    if (baseline != null) {
      result = result.withBaseline(baseline);
    }

    if (askForConfirmation) {
      result = result.withAskForConfirmation(true);
    }
    Changes changes = new Changes(
        ImmutableList.of(
            new Change<>(originRef, new Author("foo", "foo@foo.com"), "message",
                ZonedDateTime.now(ZoneOffset.UTC), ImmutableListMultimap.of("my_label", "12345")),
            new Change<>(originRef, new Author("foo", "foo@foo.com"), "message",
                ZonedDateTime.now(ZoneOffset.UTC), ImmutableListMultimap.of("my_label", "6789"))),
        ImmutableList.of());
    result = result.withChanges(changes);
    ImmutableList<DestinationEffect> destinationResult =
        writer.write(result, destinationFiles, console);
    assertThat(destinationResult).hasSize(1);
    assertThat(destinationResult.get(0).getErrors()).isEmpty();
    assertThat(destinationResult.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(destinationResult.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(destinationResult.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");
  }

}