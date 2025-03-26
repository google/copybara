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
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.authoring.Author;
import com.google.copybara.checks.Checker;
import com.google.copybara.credentials.ConstantCredentialIssuer;
import com.google.copybara.effect.DestinationEffect;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitLabMrDestination.GitLabWriterState;
import com.google.copybara.git.GitLabMrWriteHook.GitLabMrWriteHookParams;
import com.google.copybara.git.GitLabMrWriter.GitLabMrWriterParams;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.entities.CreateMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.ListProjectMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.ListUsersParams;
import com.google.copybara.git.gitlab.api.entities.MergeRequest;
import com.google.copybara.git.gitlab.api.entities.Project;
import com.google.copybara.git.gitlab.api.entities.UpdateMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.User;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.TransformWorks;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import net.starlark.java.eval.EvalException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class GitLabMrWriterTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private GitLabApi gitLabApi;
  private CredentialFileHandler credentialFileHandler;
  private final TestingConsole console = new TestingConsole();
  private OptionsBuilder optionsBuilder;
  private GitRepository testLocalRepo;
  private URI remoteRepoUrl;
  private WriterContext writerContext;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    Path remoteRepoDir = Files.createTempDirectory("gitlab_test");
    GitRepository testRemoteRepo = GitRepository.newRepo(true, remoteRepoDir, getGitEnv()).init();
    remoteRepoUrl = URI.create("file://" + remoteRepoDir);
    Path localRepoDir = Files.createTempDirectory("gitlab_test_local");
    testLocalRepo = GitRepository.newRepo(true, localRepoDir, getGitEnv()).init();
    Files.writeString(remoteRepoDir.resolve("test.txt"), "capybara");
    testRemoteRepo.add().all().run();
    testRemoteRepo.commit("Capy Bara <no-reply@copybara.io>", getNowDateTime(), "first commit");
    testRemoteRepo.branch("source-branch").run();
    Files.writeString(remoteRepoDir.resolve("test2.txt"), "beaver");
    testRemoteRepo.add().all().run();
    testRemoteRepo.commit("Capy Bara <no-reply@copybara.io>", getNowDateTime(), "second commit");
    testRemoteRepo.branch("target-branch").run();

    ConstantCredentialIssuer user =
        ConstantCredentialIssuer.createConstantOpenValue("super-secret-token");
    ConstantCredentialIssuer token =
        ConstantCredentialIssuer.createConstantSecret("token", "super-secret-token");
    credentialFileHandler = new CredentialFileHandler("", remoteRepoUrl.getPath(), user, token);

    optionsBuilder = new OptionsBuilder().setConsole(console).setWorkdirToRealTempDir();
    optionsBuilder.gitDestination.committerEmail = "no-reply@copybara.io";
    optionsBuilder.gitDestination.committerName = "Capy Bara";
    writerContext =
        new WriterContext(
            "workflowName",
            "workflowIdentityUser",
            /* dryRun= */ false,
            new DummyRevision("capybara"),
            Glob.ALL_FILES.roots());
  }

  @Test
  public void dryRunMode_noExtraDestinationEffects() throws Exception {
    writerContext =
        new WriterContext(
            "workflowName",
            "workflowIdentityUser",
            /* dryRun= */ true,
            new DummyRevision("capybara"),
            Glob.ALL_FILES.roots());
    GitLabMrWriter underTest =
        getGitLabMrWriter(
            new Project(1),
            /* checker= */ Optional.empty(),
            Optional.of("titleTemplate"),
            Optional.of("bodyTemplate"),
            ImmutableList.of("assignee1"),
            /* integrates= */ getWriterState());

    ImmutableList<DestinationEffect> result =
        underTest.write(getTransformResult(ImmutableMultimap.of()), Glob.ALL_FILES, console);

    // We should only have the single effect created by GitDestination's WriterImpl super class.
    assertThat(result).hasSize(1);
    String destinationSha1 = result.getFirst().getDestinationRef().getId();
    assertThat(testLocalRepo.resolveReference(destinationSha1).getSha1())
        .isEqualTo(destinationSha1);
  }

  @Test
  public void mergeRequestNumberInWriterState_noExtraDestinationEffects() throws Exception {
    writerContext =
        new WriterContext(
            "workflowName",
            "workflowIdentityUser",
            /* dryRun= */ false,
            new DummyRevision("capybara"),
            Glob.ALL_FILES.roots());
    GitLabWriterState state = getWriterState();
    state.setMrNumber(1L);
    GitLabMrWriter underTest =
        getGitLabMrWriter(
            new Project(1),
            /* checker= */ Optional.empty(),
            Optional.of("titleTemplate"),
            Optional.of("bodyTemplate"),
            ImmutableList.of("assignee1"),
            /* integrates= */ state);

    ImmutableList<DestinationEffect> result =
        underTest.write(getTransformResult(ImmutableMultimap.of()), Glob.ALL_FILES, console);

    // We should only have the single effect created by GitDestination's WriterImpl super class.
    assertThat(result).hasSize(1);
    String destinationSha1 = result.getFirst().getDestinationRef().getId();
    assertThat(testLocalRepo.resolveReference(destinationSha1).getSha1())
        .isEqualTo(destinationSha1);
  }

  @Test
  public void titleMustNotBeEmpty() {
    writerContext =
        new WriterContext(
            "workflowName",
            "workflowIdentityUser",
            /* dryRun= */ false,
            new DummyRevision("capybara"),
            Glob.ALL_FILES.roots());
    GitLabMrWriter underTest =
        getGitLabMrWriter(
            new Project(1),
            /* checker= */ Optional.empty(),
            /* titleTemplate= */ Optional.of(""),
            Optional.of("bodyTemplate"),
            ImmutableList.of("assignee1"),
            /* integrates= */ getWriterState());

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                underTest.write(
                    getTransformResult(ImmutableMultimap.of()), Glob.ALL_FILES, console));

    assertThat(e).hasMessageThat().contains("Merge request title can not be empty.");
  }

  @Test
  public void createNewMergeRequest() throws Exception {
    int userId = 55;
    int projectId = 878787;
    int mrId = 12345;
    ArgumentCaptor<CreateMergeRequestParams> createMrParamsCaptor =
        ArgumentCaptor.forClass(CreateMergeRequestParams.class);
    // Return no merge requests so we end up creating a new one.
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of());
    when(gitLabApi.createMergeRequest(createMrParamsCaptor.capture()))
        .thenReturn(Optional.of(getMergeRequest(projectId, mrId)));
    when(gitLabApi.getListUsers(any(ListUsersParams.class)))
        .thenReturn(ImmutableList.of(new User(userId)));

    writerContext =
        new WriterContext(
            "workflowName",
            "workflowIdentityUser",
            /* dryRun= */ false,
            new DummyRevision("capybara"),
            Glob.ALL_FILES.roots());
    GitLabMrWriter underTest =
        getGitLabMrWriter(
            new Project(projectId),
            /* checker= */ Optional.empty(),
            Optional.of("titleTemplate"),
            Optional.of("bodyTemplate"),
            ImmutableList.of("assignee1"),
            /* integrates= */ getWriterState());

    ImmutableList<DestinationEffect> result =
        underTest.write(getTransformResult(ImmutableMultimap.of()), Glob.ALL_FILES, console);

    assertThat(result).hasSize(2);
    assertThat(result)
        .contains(
            new DestinationEffect(
                DestinationEffect.Type.CREATED,
                "Merge Request https://gitlab.com/test/test/merge_requests/" + mrId + " created",
                TransformWorks.EMPTY_CHANGES.getCurrent(),
                new DestinationEffect.DestinationRef(
                    "" + mrId,
                    "merge_request",
                    "https://gitlab.com/test/test/merge_requests/" + mrId)));
    assertThat(createMrParamsCaptor.getValue().title()).isEqualTo("titleTemplate");
    assertThat(createMrParamsCaptor.getValue().description()).isEqualTo("bodyTemplate");
    assertThat(createMrParamsCaptor.getValue().projectId()).isEqualTo(projectId);
    assertThat(createMrParamsCaptor.getValue().sourceBranch()).isEqualTo("source-branch");
    assertThat(createMrParamsCaptor.getValue().targetBranch()).isEqualTo("target-branch");
    assertThat(createMrParamsCaptor.getValue().assigneeIds()).containsExactly(userId);
  }

  @Test
  public void labelTemplatingForMrTitleBodyAndAssignees() throws Exception {
    int userId = 55;
    int projectId = 878787;
    int mrId = 12345;
    ArgumentCaptor<CreateMergeRequestParams> createMrParamsCaptor =
        ArgumentCaptor.forClass(CreateMergeRequestParams.class);
    ArgumentCaptor<ListUsersParams> listUsersParamsCaptor =
        ArgumentCaptor.forClass(ListUsersParams.class);
    // Return no merge requests so we end up creating a new one.
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of());
    when(gitLabApi.createMergeRequest(createMrParamsCaptor.capture()))
        .thenReturn(Optional.of(getMergeRequest(projectId, mrId)));
    when(gitLabApi.getListUsers(listUsersParamsCaptor.capture()))
        .thenReturn(ImmutableList.of(new User(userId)));

    writerContext =
        new WriterContext(
            "workflowName",
            "workflowIdentityUser",
            /* dryRun= */ false,
            new DummyRevision("capybara"),
            Glob.ALL_FILES.roots());
    ImmutableMultimap<String, String> testLabels =
        ImmutableMultimap.of(
            "title_label",
            "resolved_title",
            "body_label",
            "resolved_body",
            "assignee_label",
            "resolved_assignee");
    GitLabMrWriter underTest =
        getGitLabMrWriter(
            new Project(projectId),
            /* checker= */ Optional.empty(),
            Optional.of("${title_label}"),
            Optional.of("${body_label}"),
            ImmutableList.of("${assignee_label}"),
            /* integrates= */ getWriterState());

    ImmutableList<DestinationEffect> unused =
        underTest.write(getTransformResult(testLabels), Glob.ALL_FILES, console);

    assertThat(createMrParamsCaptor.getValue().title()).isEqualTo("resolved_title");
    assertThat(createMrParamsCaptor.getValue().description()).isEqualTo("resolved_body");
    assertThat(listUsersParamsCaptor.getValue().username()).isEqualTo("resolved_assignee");
  }

  @Test
  public void createNewMergeRequest_noMrResponseThrowsRepoException() throws Exception {
    int userId = 55;
    int projectId = 878787;
    ArgumentCaptor<CreateMergeRequestParams> createMrParamsCaptor =
        ArgumentCaptor.forClass(CreateMergeRequestParams.class);
    // Return no merge requests so we end up creating a new one.
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of());
    // Don't return a response so we end up throwing a RepoException.
    when(gitLabApi.createMergeRequest(createMrParamsCaptor.capture())).thenReturn(Optional.empty());
    when(gitLabApi.getListUsers(any(ListUsersParams.class)))
        .thenReturn(ImmutableList.of(new User(userId)));

    writerContext =
        new WriterContext(
            "workflowName",
            "workflowIdentityUser",
            /* dryRun= */ false,
            new DummyRevision("capybara"),
            Glob.ALL_FILES.roots());
    GitLabMrWriter underTest =
        getGitLabMrWriter(
            new Project(projectId),
            /* checker */ Optional.empty(),
            Optional.of("titleTemplate"),
            Optional.of("bodyTemplate"),
            ImmutableList.of("assignee1"),
            /* integrates */
            getWriterState());

    RepoException e =
        assertThrows(
            RepoException.class,
            () ->
                underTest.write(
                    getTransformResult(ImmutableMultimap.of()), Glob.ALL_FILES, console));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Attempted to create a new merge request, but the API did not respond with information"
                + " about the new merge request");
  }

  @Test
  public void moreThanOneUserFoundForAssigneeTemplate_throwsException() throws Exception {
    int projectId = 878787;
    int mrId = 12345;
    // Return no merge requests so we end up creating a new one.
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of());
    when(gitLabApi.createMergeRequest(any(CreateMergeRequestParams.class)))
        .thenReturn(Optional.of(getMergeRequest(projectId, mrId)));
    // Return 2 users for a username, which we expect not to happen.
    when(gitLabApi.getListUsers(any(ListUsersParams.class)))
        .thenReturn(ImmutableList.of(new User(123), new User(456)));

    writerContext =
        new WriterContext(
            "workflowName",
            "workflowIdentityUser",
            /* dryRun= */ false,
            new DummyRevision("capybara"),
            Glob.ALL_FILES.roots());
    GitLabMrWriter underTest =
        getGitLabMrWriter(
            new Project(projectId),
            /* checker= */ Optional.empty(),
            Optional.of("titleTemplate"),
            Optional.of("bodyTemplate"),
            ImmutableList.of("assignee1"),
            /* integrates= */ getWriterState());

    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                underTest.write(
                    getTransformResult(ImmutableMultimap.of()), Glob.ALL_FILES, console));

    assertThat(e).hasMessageThat().contains("Found more than 1 user for assignee1");
  }

  @Test
  public void updateExistingMergeRequest() throws Exception {
    int userId = 55;
    int projectId = 878787;
    int mrId = 12345;
    ArgumentCaptor<UpdateMergeRequestParams> updateMrParamsCaptor =
        ArgumentCaptor.forClass(UpdateMergeRequestParams.class);
    // Return a merge request so we end up updating it.
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of(getMergeRequest(projectId, mrId)));
    when(gitLabApi.updateMergeRequest(updateMrParamsCaptor.capture()))
        .thenReturn(Optional.of(getMergeRequest(projectId, mrId)));
    when(gitLabApi.getListUsers(any(ListUsersParams.class)))
        .thenReturn(ImmutableList.of(new User(userId)));
    writerContext =
        new WriterContext(
            "workflowName",
            "workflowIdentityUser",
            /* dryRun= */ false,
            new DummyRevision("capybara"),
            Glob.ALL_FILES.roots());
    GitLabMrWriter underTest =
        getGitLabMrWriter(
            new Project(projectId),
            /* checker= */ Optional.empty(),
            Optional.of("titleTemplate"),
            Optional.of("bodyTemplate"),
            ImmutableList.of("assignee1"),
            /* integrates= */ getWriterState());

    ImmutableList<DestinationEffect> result =
        underTest.write(getTransformResult(ImmutableMultimap.of()), Glob.ALL_FILES, console);

    assertThat(result).hasSize(2);
    assertThat(result)
        .contains(
            new DestinationEffect(
                DestinationEffect.Type.UPDATED,
                "Merge Request https://gitlab.com/test/test/merge_requests/" + mrId + " updated",
                TransformWorks.EMPTY_CHANGES.getCurrent(),
                new DestinationEffect.DestinationRef(
                    "" + mrId,
                    "merge_request",
                    "https://gitlab.com/test/test/merge_requests/" + mrId)));
    assertThat(updateMrParamsCaptor.getValue().title()).isEqualTo("titleTemplate");
    assertThat(updateMrParamsCaptor.getValue().description()).isEqualTo("bodyTemplate");
    assertThat(updateMrParamsCaptor.getValue().projectId()).isEqualTo(projectId);
    assertThat(updateMrParamsCaptor.getValue().assigneeIds()).containsExactly(userId);
  }

  @Test
  public void updateExistingMergeRequest_noMrResponseThrowsRepoException() throws Exception {
    int userId = 55;
    int projectId = 878787;
    int mrId = 12345;
    ArgumentCaptor<UpdateMergeRequestParams> updateMrParamsCaptor =
        ArgumentCaptor.forClass(UpdateMergeRequestParams.class);
    // Return a merge request so we end up updating it.
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of(getMergeRequest(projectId, mrId)));
    when(gitLabApi.updateMergeRequest(updateMrParamsCaptor.capture())).thenReturn(Optional.empty());
    when(gitLabApi.getListUsers(any(ListUsersParams.class)))
        .thenReturn(ImmutableList.of(new User(userId)));
    writerContext =
        new WriterContext(
            "workflowName",
            "workflowIdentityUser",
            /* dryRun= */ false,
            new DummyRevision("capybara"),
            Glob.ALL_FILES.roots());
    GitLabMrWriter underTest =
        getGitLabMrWriter(
            new Project(projectId),
            /* checker= */ Optional.empty(),
            Optional.of("titleTemplate"),
            Optional.of("bodyTemplate"),
            ImmutableList.of("assignee1"),
            /* integrates= */ getWriterState());

    RepoException e =
        assertThrows(
            RepoException.class,
            () ->
                underTest.write(
                    getTransformResult(ImmutableMultimap.of()), Glob.ALL_FILES, console));

    assertThat(e)
        .hasMessageThat()
        .contains(
            "Attempted to create a new merge request, but the API did not respond with information"
                + " about the new merge request");
  }

  private static MergeRequest getMergeRequest(int projectId, int mrId) {
    return new MergeRequest(
        projectId,
        mrId,
        "sha1",
        MergeRequest.DetailedMergeStatus.MERGEABLE,
        "source-branch",
        "https://gitlab.com/test/test/merge_requests/" + mrId);
  }

  private ZonedDateTime getNowDateTime() {
    return ZonedDateTime.now(ZoneId.of("America/Los_Angeles"));
  }

  private GitLabMrWriteHookParams getWriteHookParams() {
    return new GitLabMrWriteHookParams(
        /* allowEmptyDiff= */ false,
        gitLabApi,
        remoteRepoUrl,
        "target-branch",
        optionsBuilder.general,
        /* partialFetch= */ false,
        ImmutableSet.of());
  }

  private GitLabMrWriter getGitLabMrWriter(
      Project project,
      Optional<Checker> checker,
      Optional<String> titleTemplate,
      Optional<String> bodyTemplate,
      ImmutableList<String> assigneeTemplates,
      GitLabWriterState state) {
    return GitLabMrWriterParams.builder()
        .setGitLabApi(gitLabApi)
        .setTitleTemplate(titleTemplate)
        .setBodyTemplate(bodyTemplate)
        .setAssigneeTemplates(assigneeTemplates)
        .setProject(project)
        .setWriterContext(writerContext)
        .setSkipPush(false)
        .setRepoUrl(remoteRepoUrl)
        .setSourceBranch("source-branch")
        .setTargetBranch("target-branch")
        .setPartialFetch(false)
        .setGeneralOptions(optionsBuilder.general)
        .setGitOptions(optionsBuilder.git)
        .setWriteHook(getWriteHookParams().createWriteHook())
        .setState(state)
        .setIntegrates(ImmutableSet.of())
        .setChecker(checker)
        .setDestinationOptions(optionsBuilder.gitDestination)
        .setCredentials(credentialFileHandler)
        .build()
        .createWriter();
  }

  private GitLabWriterState getWriterState() {
    return new GitLabWriterState(
        LazyResourceLoader.memoized((unused) -> testLocalRepo), "local-branch");
  }

  private TransformResult getTransformResult(ImmutableMultimap<String, String> testLabels)
      throws RepoException, EvalException {
    return new TransformResult(
        workdir,
        new DummyRevision("1"),
        Author.parse("Capy Bara <no-reply@copybara.io>"),
        "test",
        new DummyRevision("1"),
        "workflowName",
        TransformWorks.EMPTY_CHANGES,
        /* rawSourceRef= */ "1",
        /* setRevId= */ false,
        testLabels::get,
        "rev_id");
  }
}
