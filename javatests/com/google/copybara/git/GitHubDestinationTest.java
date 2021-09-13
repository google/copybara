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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Map.Entry;
import net.starlark.java.eval.EvalException;
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
  private String primaryBranch;

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
    primaryBranch = remote.getPrimaryBranch();
    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@github.com".getBytes(UTF_8));
    options.git.credentialHelperStorePath = credentialsFile.toString();

    options.gitDestination = new GitDestinationOptions(options.general, options.git);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    url = "https://github.com/foo";
    force = false;
    fetch = primaryBranch;
    push = primaryBranch;
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testDryRun() throws Exception {
    fetch = primaryBranch;
    push = primaryBranch;
    addFiles(
        remote,
        primaryBranch,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
    remote.simpleCommand("branch", "other");
    WriterContext writerContext =
        new WriterContext("piper_to_github", "test", true, new DummyRevision("origin_ref1"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer = destination().newWriter(writerContext);
    process(writer, new DummyRevision("origin_ref1"));

    GitTesting.assertThatCheckout(remote, primaryBranch)
        .containsFile("foo.txt", "foo")
        .containsNoMoreFiles();
    Files.write(workdir.resolve("test.txt"), "some content".getBytes(UTF_8));
    // Run again without dry run
    writer = newWriter();
    process(writer, new DummyRevision("origin_ref1"));
    GitTesting.assertThatCheckout(remote, primaryBranch)
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
  public void testPrToUpdateWithRegularString_defaultDelete() throws Exception {
    checkPrToUpdateWithRegularString(/*deletePrBranch=*/"None" , /*expectDeletePrBranch*/ false);
  }

  @Test
  public void testPrToUpdateWithRegularString_deleteRef() throws Exception {
    checkPrToUpdateWithRegularString(/*deletePrBranch=*/"True", /*expectDeletePrBranch*/ true);
  }

  @Test
  public void testPrToUpdateWithRegularString_deleteRefDisabled() throws Exception {
    checkPrToUpdateWithRegularString(/*deletePrBranch=*/"False", /*expectDeletePrBranch*/ false);
  }

  @Test
  public void testPrToUpdateWithRegularString_deleteRefDisabledByFlag() throws Exception {
    options.github.gitHubDeletePrBranch = false;
    checkPrToUpdateWithRegularString(/*deletePrBranch=*/"True", /*expectDeletePrBranch*/ false);
  }

  @Test
  public void testPrToUpdateWithRegularString_deleteRefEnabledByFlag() throws Exception {
    options.github.gitHubDeletePrBranch = true;
    checkPrToUpdateWithRegularString(/*deletePrBranch=*/"False", /*expectDeletePrBranch*/ true);
  }

  @Test
  public void testPrToUpdateIngoredForInitHistory() throws Exception {
    options.workflowOptions.initHistory = true;

    addFiles(
        remote,
        primaryBranch,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
    WriterContext writerContext =
        new WriterContext("piper_to_github", "test", false, new DummyRevision("origin_ref1"),
            Glob.ALL_FILES.roots());
    writeFile(this.workdir, "test.txt", "some content");
    Writer<GitRevision> writer =
        destinationWithExistingPrBranch("other", "True").newWriter(writerContext);
    DummyRevision ref = new DummyRevision("origin_ref1");
    TransformResult result = TransformResults.of(workdir, ref);

    Changes changes = new Changes(
        ImmutableList.of(
            new Change<>(ref, new Author("foo", "foo@foo.com"), "message",
                ZonedDateTime.now(ZoneOffset.UTC), ImmutableListMultimap.of("my_label", "12345")),
            new Change<>(ref, new Author("foo", "foo@foo.com"), "message",
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

    // This is a migration of two changes (use the same ref because mocks)
    verifyNoInteractions(gitUtil.httpTransport());
    GitTesting.assertThatCheckout(remote, primaryBranch)
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();

    assertThat(remote.simpleCommand("show-ref").getStdout()).doesNotContain("other");
  }

  private void checkPrToUpdateWithRegularString(String deletePrBranch, boolean expectDeletePrBranch)
      throws Exception {
    if (expectDeletePrBranch) {
      when(gitUtil.httpTransport().buildRequest(eq("DELETE"),
          contains("repos/foo/git/refs/heads/other"))).thenReturn(
          mockResponseWithStatus("", 204));
    }
    gitUtil.mockApi(
        "GET",
        "https://api.github.com/repos/foo/git/refs/heads/other",
        mockResponse(
            "{\n"
                + "\"ref\" : \"refs/heads/test_existing_pr\",\n"
                + "\"node_id\" : \"MDM6UmVmcmVmcy9oZWFkcy9mZWF0dXJlQQ==\",\n"
                + "\"url\" :"
                + " \"https://api.github.com/repos/octocat/Hello-World/git/refs/heads/test_existing_pr\",\n"
                + "\"object\" : {\n"
                + "         \"type\" : \"commit\",\n"
                + "         \"sha\" : \"aa218f56b14c9653891f9e74264a383fa43fefbd\",\n"
                + "         \"url\" :"
                + " \"https://api.github.com/repos/octocat/Hello-World/git/commits/aa218f56b14c9653891f9e74264a383fa43fefbd\"\n"
                + "       }\n"
                + "}"));
    addFiles(
        remote,
        primaryBranch,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
    remote.simpleCommand("branch", "other");
    GitTesting.assertThatCheckout(remote, primaryBranch)
        .containsFile("foo.txt", "foo")
        .containsNoMoreFiles();
    GitTesting.assertThatCheckout(remote, "other")
        .containsFile("foo.txt", "foo")
        .containsNoMoreFiles();
    WriterContext writerContext =
        new WriterContext("piper_to_github", "test", false, new DummyRevision("origin_ref1"),
            Glob.ALL_FILES.roots());
    writeFile(this.workdir, "test.txt", "some content");
    Writer<GitRevision> writer =
        destinationWithExistingPrBranch("other", deletePrBranch).newWriter(
            writerContext);
    DummyRevision ref = new DummyRevision("origin_ref1");
    TransformResult result = TransformResults.of(workdir, ref);

    Changes changes = new Changes(
        ImmutableList.of(
            new Change<>(ref, new Author("foo", "foo@foo.com"), "message",
                ZonedDateTime.now(ZoneOffset.UTC), ImmutableListMultimap.of("my_label", "12345")),
            new Change<>(ref, new Author("foo", "foo@foo.com"), "message",
                ZonedDateTime.now(ZoneOffset.UTC), ImmutableListMultimap.of("my_label", "6789"))),
        ImmutableList.of());
    result = result.withChanges(changes);
    ImmutableList<DestinationEffect> destinationResult =
        writer.write(result, destinationFiles, console);
    assertThat(destinationResult).hasSize(expectDeletePrBranch ? 3 : 1);
    assertThat(destinationResult.get(0).getErrors()).isEmpty();
    assertThat(destinationResult.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(destinationResult.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(destinationResult.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");

    // This is a migration of two changes (use the same ref because mocks)
    verify(gitUtil.httpTransport(), times(expectDeletePrBranch ? 2 : 0)).buildRequest(eq("DELETE"),
        contains("refs/heads/other"));

    GitTesting.assertThatCheckout(remote, primaryBranch)
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
    GitTesting.assertThatCheckout(remote, "other")
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
  }

  @Test
  public void testPrToUpdateWithLabel() throws Exception {
    gitUtil.mockApi(
        "GET",
        "https://api.github.com/repos/foo/git/refs/heads/other_12345",
        mockResponse(
            "{\n"
                + "\"ref\" : \"refs/heads/test_existing_12345_pr\",\n"
                + "\"node_id\" : \"MDM6UmVmcmVmcy9oZWFkcy9mZWF0dXJlQQ==\",\n"
                + "\"url\" :"
                + " \"https://api.github.com/repos/octocat/Hello-World/git/refs/heads/test_existing_12345_pr\",\n"
                + "\"object\" : {\n"
                + "         \"type\" : \"commit\",\n"
                + "         \"sha\" : \"aa218f56b14c9653891f9e74264a383fa43fefbd\",\n"
                + "         \"url\" :"
                + " \"https://api.github.com/repos/octocat/Hello-World/git/commits/aa218f56b14c9653891f9e74264a383fa43fefbd\"\n"
                + "       }\n"
                + "}"));
    gitUtil.mockApi(
        "GET",
        "https://api.github.com/repos/foo/git/refs/heads/other_6789",
        mockResponse(
            "{\n"
                + "\"ref\" : \"refs/heads/test_existing_6789_pr\",\n"
                + "\"node_id\" : \"MDM6UmVmcmVmcy9oZWFkcy9mZWF0dXJlQQ==\",\n"
                + "\"url\" :"
                + " \"https://api.github.com/repos/octocat/Hello-World/git/refs/heads/test_existing_6789_pr\",\n"
                + "\"object\" : {\n"
                + "         \"type\" : \"commit\",\n"
                + "         \"sha\" : \"aa218f56b14c9653891f9e74264a383fa43fefbd\",\n"
                + "         \"url\" :"
                + " \"https://api.github.com/repos/octocat/Hello-World/git/commits/aa218f56b14c9653891f9e74264a383fa43fefbd\"\n"
                + "       }\n"
                + "}"));
    addFiles(
        remote,
        primaryBranch,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
    remote.simpleCommand("branch", "other_12345");
    remote.simpleCommand("branch", "other_6789");
    GitTesting.assertThatCheckout(remote, primaryBranch)
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
        new WriterContext("piper_to_github", "test", false, new DummyRevision("origin_ref1"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer = destinationWithExistingPrBranch(
        "other_${my_label}", /*deletePrBranch=*/"None").newWriter(writerContext);
    process(writer, new DummyRevision("origin_ref1"));
    GitTesting.assertThatCheckout(remote, primaryBranch)
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
    checkRefNotFound();
  }

  @Test
  public void testWithRefsNotFoundUnprocessable() throws Exception {
    gitUtil.mockApi("GET",
        "https://api.github.com/repos/foo/git/refs/heads/other_12345",
        GitTestUtil.mockGitHubUnprocessable());
    gitUtil.mockApi("GET",
        "https://api.github.com/repos/foo/git/refs/heads/other_6789",
        GitTestUtil.mockGitHubUnprocessable());
    checkRefNotFound();
  }

  private void checkRefNotFound() throws IOException, RepoException, ValidationException {
    addFiles(
        remote,
        primaryBranch,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
    GitTesting.assertThatCheckout(remote, primaryBranch)
        .containsFile("foo.txt", "foo")
        .containsNoMoreFiles();
    writeFile(this.workdir, "test.txt", "some content");
    WriterContext writerContext =
        new WriterContext("piper_to_github", "test", false, new DummyRevision("origin_ref1"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer = destinationWithExistingPrBranch(
        "other_${my_label}", /*deletePrBranch=*/"None").newWriter(writerContext);
    process(writer, new DummyRevision("origin_ref1"));
    GitTesting.assertThatCheckout(remote, primaryBranch)
        .containsFile("test.txt", "some content")
        .containsNoMoreFiles();
    console.assertThat().onceInLog(MessageType.VERBOSE, "Branch other_12345 does not exist");
    console.assertThat().onceInLog(MessageType.VERBOSE, "Branch other_6789 does not exist");
  }

  @Test
  public void testChecker() throws Exception {
    GitDestination d = skylark.eval("r", "r = git.github_destination("
        + "    url = 'http://github.com/example/example', \n"
        + "    api_checker = testing.dummy_checker(),\n"
        + ")");
    WriterContext writerContext =
        new WriterContext("piper_to_github", "test", false, new DummyRevision("origin_ref1"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer = d.newWriter(writerContext);
    GitHubEndPoint endpoint = (GitHubEndPoint) writer.getFeedbackEndPoint(console);
    EvalException e =
        assertThrows(EvalException.class, () -> endpoint.getCombinedStatus("bad_word"));
    assertThat(e).hasMessageThat().contains("Bad word 'bad_word' found: field 'path'");
  }

  @Test
  public void testWithGitHubApiError() throws Exception {
    gitUtil.mockApi(
        "GET",
        "https://api.github.com/repos/foo/git/refs/heads/other_12345",
        mockResponseWithStatus("", 403));
    gitUtil.mockApi(
        "GET",
        "https://api.github.com/repos/foo/git/refs/other_6789",
        mockResponseWithStatus("", 403));
    addFiles(
        remote,
        primaryBranch,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
    WriterContext writerContext =
        new WriterContext(
            "piper_to_github",
            "test",
            false,
            new DummyRevision("origin_ref1"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer =
        destinationWithExistingPrBranch("other_${my_label}", /*deletePrBranch=*/ "None")
            .newWriter(writerContext);
    GitHubApiException e =
        assertThrows(
            GitHubApiException.class,() -> process(writer, new DummyRevision("origin_ref1")));
    Assert.assertSame(e.getResponseCode(), FORBIDDEN);
  }

  @Test
  public void testWithLabelNotFound() throws Exception {
    addFiles(
        remote,
        primaryBranch,
        "first change",
        ImmutableMap.<String, String>builder().put("foo.txt", "foo").build());
    WriterContext writerContext =
        new WriterContext(
            "piper_to_github",
            "test",
            false,
            new DummyRevision("origin_ref1"),
            Glob.ALL_FILES.roots());
    Writer<GitRevision> writer =
        destinationWithExistingPrBranch(
                "other_${no_such_label}", /*deletePrBranch=*/ "None")
            .newWriter(writerContext);
    ValidationException e =
        assertThrows(ValidationException.class,
            () -> process(writer, new DummyRevision("origin_ref1")));
    Assert.assertTrue(e.getMessage().contains("Template 'other_${no_such_label}' has an error"));
  }

  @Test
  public void testLabelIsPropagated()
      throws ValidationException {
    options.setForce(force);
    GitDestination dest =  skylark.eval("result",
        String.format("result = git.github_destination(\n"
            + "    url = '%s',\n"
            + "    fetch = '%s',\n"
            + "    push = '%s',\n"
            + "    tag_name = 'guten_tag',\n"
            + "    tag_msg = 'tag msg',\n"
            + ")", url, fetch, push));
    assertThat(dest.describe(Glob.ALL_FILES).get("tagName")).contains("guten_tag");
    assertThat(dest.describe(Glob.ALL_FILES).get("tagMsg")).contains("tag msg");
  }

  private void addFiles(GitRepository remote, String branch, String msg, Map<String, String> files)
      throws IOException, RepoException {
    Path temp = Files.createTempDirectory("temp");
    GitRepository tmpRepo = remote.withWorkTree(temp);
    if (branch != null) {
      if (tmpRepo.refExists(branch)) {
        tmpRepo.simpleCommand("checkout", branch);
      } else if (!branch.equals(primaryBranch)) {
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
    TransformResult result = TransformResults.of(workdir, ref);

    Changes changes = new Changes(
        ImmutableList.of(
            new Change<>(ref, new Author("foo", "foo@foo.com"), "message",
                ZonedDateTime.now(ZoneOffset.UTC), ImmutableListMultimap.of("my_label", "12345")),
            new Change<>(ref, new Author("foo", "foo@foo.com"), "message",
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

  private Writer<GitRevision> newWriter() throws ValidationException {
    return destination().newWriter(
        new WriterContext("piper_to_github", "TEST", false, new DummyRevision("test"),
            Glob.ALL_FILES.roots()));
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

  private GitDestination destinationWithExistingPrBranch(
      String prBranchToUpdate, String deletePrBranch)
      throws ValidationException {
    options.setForce(force);
    return skylark.eval("result",
        String.format("result = git.github_destination(\n"
            + "    url = '%s',\n"
            + "    fetch = '%s',\n"
            + "    push = '%s',\n"
            + "    pr_branch_to_update = '%s',\n"
            + "    delete_pr_branch = %s,\n"
            + ")", url, fetch, push, prBranchToUpdate, deletePrBranch));
  }
}
