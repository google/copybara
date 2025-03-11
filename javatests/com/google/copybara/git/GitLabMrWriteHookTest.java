/*
 * Copyright (C) 2025 Google LLC
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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpStatusCodes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.RedundantChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.MessageInfo;
import com.google.copybara.git.GitLabMrWriteHook.GitLabMrWriteHookParams;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.GitLabApiException;
import com.google.copybara.git.gitlab.api.entities.ListProjectMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.MergeRequest;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.DetailedMergeStatus;
import com.google.copybara.git.gitlab.api.entities.Project;
import com.google.copybara.json.GsonParserUtil;
import com.google.copybara.revision.Change;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.gson.reflect.TypeToken;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class GitLabMrWriteHookTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  private final TestingConsole console = new TestingConsole();
  @Mock private GitLabApi gitLabApi;
  private OptionsBuilder optionsBuilder;
  private GitRepository repository;

  @Before
  public void setup() throws Exception {
    optionsBuilder = new OptionsBuilder().setConsole(console);
    Path repoDir = Files.createTempDirectory("gitlab_mr_write_hook_test");
    repository = getRepository(repoDir);
  }

  @Test
  public void noEmptyDiffCheck_ifSkipPushIsTrue() throws Exception {
    GitLabMrWriteHook underTest =
        getGitLabMrWriteHook(
            false, ImmutableSet.of(), URI.create("https://gitlab.com/capybara/capybara"));

    underTest.beforePush(
        repository,
        new MessageInfo(ImmutableList.of()),
        /* skipPush= */ true,
        ImmutableList.of(),
        ImmutableList.of());

    console
        .assertThat()
        .logContains(
            MessageType.VERBOSE, "Not performing empty-diff check because skipPush is true");
  }

  @Test
  public void noEmptyDiffCheck_ifAllowEmptyDiffIsTrue() throws Exception {
    GitLabMrWriteHook underTest =
        getGitLabMrWriteHook(
            true, ImmutableSet.of(), URI.create("https://gitlab.com/capybara/capybara"));

    underTest.beforePush(
        repository,
        new MessageInfo(ImmutableList.of()),
        /* skipPush= */ false,
        ImmutableList.of(),
        ImmutableList.of());

    console
        .assertThat()
        .logContains(
            MessageType.VERBOSE, "Not performing empty-diff check because allowEmptyDiff is true");
  }

  @Test
  public void projectNotFound_throwsException() throws Exception {
    GitLabMrWriteHook underTest =
        getGitLabMrWriteHook(
            false, ImmutableSet.of(), URI.create("https://gitlab.com/capybara/capybara"));
    when(gitLabApi.getProject(anyString()))
        .thenThrow(
            new GitLabApiException("project not found", HttpStatusCodes.STATUS_CODE_NOT_FOUND));

    GitLabApiException e =
        assertThrows(
            GitLabApiException.class,
            () ->
                underTest.beforePush(
                    repository,
                    new MessageInfo(ImmutableList.of()),
                    /* skipPush= */ false,
                    ImmutableList.of(),
                    ImmutableList.of(createChange("ref", "contextref"))));

    verify(gitLabApi).getProject(eq(URLEncoder.encode("capybara/capybara", UTF_8)));
    assertThat(e).hasMessageThat().contains("project not found");
    assertThat(e.getResponseCode()).hasValue(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
    console
        .assertThat()
        .onceInLog(MessageType.WARNING, "The project capybara/capybara was not found");
  }

  @Test
  public void projectEmptyResponse_throwsException() throws Exception {
    GitLabMrWriteHook underTest =
        getGitLabMrWriteHook(
            false, ImmutableSet.of(), URI.create("https://gitlab.com/capybara/capybara"));
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.empty());

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                underTest.beforePush(
                    repository,
                    new MessageInfo(ImmutableList.of()),
                    /* skipPush= */ false,
                    ImmutableList.of(),
                    ImmutableList.of(createChange("ref", "contextref"))));

    verify(gitLabApi).getProject(eq(URLEncoder.encode("capybara/capybara", UTF_8)));
    assertThat(e)
        .hasMessageThat()
        .contains("Failed to obtain project info from URL https://gitlab.com/capybara/capybara");
  }

  @Test
  public void noEmptyDiffCheck_ifNoMergeRequestsFound() throws Exception {
    GitLabMrWriteHook underTest =
        getGitLabMrWriteHook(
            false, ImmutableSet.of(), URI.create("https://gitlab.com/capybara/capybara"));
    when(gitLabApi.getProject(anyString()))
        .thenReturn(
            Optional.of(
                GsonParserUtil.parseString(
                    """
                    {"id": 1}\
                    """,
                    TypeToken.get(Project.class).getType(),
                    false)));
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of());

    underTest.beforePush(
        repository,
        new MessageInfo(ImmutableList.of()),
        /* skipPush= */ false,
        ImmutableList.of(),
        ImmutableList.of(createChange("ref", "contextref")));

    verify(gitLabApi).getProject(eq(URLEncoder.encode("capybara/capybara", UTF_8)));
    verify(gitLabApi)
        .getProjectMergeRequests(
            eq(1),
            argThat(
                arg ->
                    arg.params().stream()
                        .anyMatch(
                            s ->
                                s.encodedKey().equals("source_branch")
                                    && s.encodedValue().equals("beaver"))));
    console
        .assertThat()
        .onceInLog(
            MessageType.VERBOSE,
            "Not performing empty-diff check because no merge requests found for repo"
                + " https://gitlab.com/capybara/capybara and branch beaver");
  }

  @Test
  public void noEmptyDiffCheck_ifMoreThanOneMergeRequestFould() throws Exception {
    GitLabMrWriteHook underTest =
        getGitLabMrWriteHook(
            false, ImmutableSet.of(), URI.create("https://gitlab.com/capybara/capybara"));
    Project project =
        GsonParserUtil.parseString(
            """
            {"id": 1}
            """,
            TypeToken.get(Project.class).getType(),
            false);
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.of(project));
    MergeRequest mergeRequest1 =
        GsonParserUtil.parseString(
            """
            {"iid": 123}
            """,
            TypeToken.get(MergeRequest.class).getType(),
            false);
    MergeRequest mergeRequest2 =
        GsonParserUtil.parseString(
            """
            {"iid": 456}
            """,
            TypeToken.get(MergeRequest.class).getType(),
            false);
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of(mergeRequest1, mergeRequest2));

    underTest.beforePush(
        repository,
        new MessageInfo(ImmutableList.of()),
        /* skipPush= */ false,
        ImmutableList.of(),
        ImmutableList.of(createChange("ref", "contextref")));

    verify(gitLabApi).getProject(eq(URLEncoder.encode("capybara/capybara", UTF_8)));
    verify(gitLabApi)
        .getProjectMergeRequests(
            eq(1),
            argThat(
                arg ->
                    arg.params().stream()
                        .anyMatch(
                            s ->
                                s.encodedKey().equals("source_branch")
                                    && s.encodedValue().equals("beaver"))));
    console
        .assertThat()
        .onceInLog(
            MessageType.WARNING,
            "Not performing empty-diff check because more than one merge request was found for repo"
                + " https://gitlab.com/capybara/capybara and branch beaver. MR IDs: 123, 456");
  }

  @Test
  public void noEmptyDiffCheck_ifMergeStatusIsInAllowEmptyDiffMergeStatuses() throws Exception {
    ImmutableSet<DetailedMergeStatus> allowEmptyDiffMergeStatuses =
        ImmutableSet.of(DetailedMergeStatus.CONFLICT);
    GitLabMrWriteHook underTest =
        getGitLabMrWriteHook(
            false, allowEmptyDiffMergeStatuses, URI.create("https://gitlab.com/capybara/capybara"));
    Project project =
        GsonParserUtil.parseString(
            """
            {"id": 1}
            """,
            TypeToken.get(Project.class).getType(),
            false);
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.of(project));
    MergeRequest mergeRequest =
        GsonParserUtil.parseString(
            """
            {"iid": 123, "detailed_merge_status": "conflict", "sha": "abcdef"}
            """,
            TypeToken.get(MergeRequest.class).getType(),
            false);
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of(mergeRequest));

    underTest.beforePush(
        repository,
        new MessageInfo(ImmutableList.of()),
        /* skipPush= */ false,
        ImmutableList.of(),
        ImmutableList.of(createChange("ref", "contextref")));

    verify(gitLabApi).getProject(eq(URLEncoder.encode("capybara/capybara", UTF_8)));
    verify(gitLabApi)
        .getProjectMergeRequests(
            eq(1),
            argThat(
                arg ->
                    arg.params().stream()
                        .anyMatch(
                            s ->
                                s.encodedKey().equals("source_branch")
                                    && s.encodedValue().equals("beaver"))));
    console
        .assertThat()
        .onceInLog(
            MessageType.VERBOSE,
            String.format(
                "Not performing empty-diff check because mergeable status is %s for MR %s. Allowed"
                    + " merge statuses for empty-diff: %s",
                mergeRequest.getDetailedMergeStatus(),
                mergeRequest.getIid(),
                Pattern.quote(allowEmptyDiffMergeStatuses.toString())));
  }

  @Test
  public void skipPush_sameGitTreeAsUpstream() throws Exception {
    MockRemoteAndLocalRepoWithSameTree mockRepos = getMockRemoteAndLocalRepoWithSameTree();
    URI remoteRepoUrl = URI.create("file://" + mockRepos.remoteRepo().getGitDir());
    Project project =
        GsonParserUtil.parseString(
            """
            {"id": 1}
            """,
            TypeToken.get(Project.class).getType(),
            false);
    MergeRequest mergeRequest =
        GsonParserUtil.parseString(
            String.format(
                """
                {"iid": 123, "detailed_merge_status": "mergeable", "sha": "%s"}
                """,
                mockRepos.remoteRepo().resolveReference("HEAD").getSha1()),
            TypeToken.get(MergeRequest.class).getType(),
            false);
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.of(project));
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of(mergeRequest));
    GitLabMrWriteHook underTest = getGitLabMrWriteHook(false, ImmutableSet.of(), remoteRepoUrl);

    RedundantChangeException e =
        assertThrows(
            RedundantChangeException.class,
            () ->
                underTest.beforePush(
                    mockRepos.localRepo(),
                    new MessageInfo(ImmutableList.of()),
                    /* skipPush= */ false,
                    ImmutableList.of(),
                    ImmutableList.of(createChange("ref", "contextref"))));

    assertThat(e)
        .hasMessageThat()
        .contains(
            String.format(
                "Skipping push to the existing MR 123 in repo %s as the change ref is empty.",
                remoteRepoUrl));
  }

  private static MockRemoteAndLocalRepoWithSameTree getMockRemoteAndLocalRepoWithSameTree()
      throws IOException, RepoException, ValidationException {
    Path test = Files.createTempDirectory("gitlab_mr_write_hook_test");
    Files.createDirectories(test.resolve("capybara"));
    Path remoteRepoPath = test.resolve("capybara/capybara");
    Path localRepoPath = test.resolve("capybara/capybara_local");
    Files.createDirectories(remoteRepoPath);
    Files.createDirectories(localRepoPath);
    GitRepository remoteRepo = getRepository(remoteRepoPath);
    GitRepository localRepo = getRepository(localRepoPath);
    Files.writeString(remoteRepoPath.resolve("foo.txt"), "foo");
    remoteRepo.add().files("foo.txt").run();
    remoteRepo.simpleCommand("commit", "foo.txt", "-m", "commit msg remote 1");
    String sha1 = remoteRepo.resolveReference("HEAD").getSha1();
    GitRevision unused =
        localRepo.fetchSingleRef(
            remoteRepoPath.toString(), remoteRepo.getPrimaryBranch(), false, Optional.empty());
    localRepo.forceCheckout(sha1);
    // Mock the same SHA1 in the remote and local repo.
    for (GitRepository repo : ImmutableList.of(localRepo, remoteRepo)) {
      Files.writeString(repo.getWorkTree().resolve("foo.txt"), "update content");
      repo.simpleCommand("commit", "foo.txt", "-m", "update msg");
    }
    return new MockRemoteAndLocalRepoWithSameTree(remoteRepo, localRepo);
  }

  private record MockRemoteAndLocalRepoWithSameTree(
      GitRepository remoteRepo, GitRepository localRepo) {}

  private static GitRepository getRepository(Path gitlabMrWriteHookTestRemote)
      throws RepoException {
    return GitRepository.newRepo(true, gitlabMrWriteHookTestRemote, GitTestUtil.getGitEnv()).init();
  }

  private GitLabMrWriteHook getGitLabMrWriteHook(
      boolean allowEmptyDiff,
      ImmutableSet<DetailedMergeStatus> allowEmptyDiffMergeStatuses,
      URI repoUrl) {
    return new GitLabMrWriteHookParams(
            allowEmptyDiff,
            gitLabApi,
            repoUrl,
            /* mrBranchToUpdate= */ "beaver",
            optionsBuilder.general,
            /* partialFetch= */ false,
            allowEmptyDiffMergeStatuses)
        .createWriteHook();
  }

  private Change<DummyRevision> createChange(String ref, @Nullable String contextRef) {
    return new Change<>(
        new DummyRevision(ref, contextRef),
        new Author("capybara", "no-reply@copybara.io"),
        "Change " + ref,
        ZonedDateTime.now(ZoneId.of("America/Los_Angeles")),
        ImmutableListMultimap.of());
  }
}
