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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpStatusCodes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.checks.Checker;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.PathBasedConfigFile;
import com.google.copybara.credentials.ConstantCredentialIssuer;
import com.google.copybara.effect.DestinationEffect;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitLabMrDestination.GitLabMrDestinationParams;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.GitLabApiException;
import com.google.copybara.git.gitlab.api.entities.CreateMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.ListProjectMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.ListUsersParams;
import com.google.copybara.git.gitlab.api.entities.MergeRequest;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.DetailedMergeStatus;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.State;
import com.google.copybara.git.gitlab.api.entities.Project;
import com.google.copybara.git.gitlab.api.entities.UpdateMergeRequestParams;
import com.google.copybara.git.gitlab.api.entities.User;
import com.google.copybara.testing.DummyChecker;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class GitLabMrDestinationTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private GitLabApi gitLabApi;
  private final TestingConsole console = new TestingConsole();
  private CredentialFileHandler credentialFileHandler;
  private OptionsBuilder optionsBuilder;
  private String mainBranchName;
  private URI repoUrl;
  private SkylarkTestExecutor starlarkExecutor;
  private Path tempDir;

  @Before
  public void setup() throws Exception {
    Path remoteRepoDir = Files.createTempDirectory("gitlab_test");
    GitRepository testRemoteRepo = GitRepository.newRepo(true, remoteRepoDir, getGitEnv()).init();
    mainBranchName = testRemoteRepo.getCurrentBranch();
    Files.writeString(remoteRepoDir.resolve("test.txt"), "capybara");
    testRemoteRepo.add().all().run();
    testRemoteRepo.commit(
        "Capy Bara <no-reply@copybara.io>",
        ZonedDateTime.now(ZoneId.of("America/Los_Angeles")),
        "first commit");
    repoUrl = URI.create("file://" + remoteRepoDir);

    ConstantCredentialIssuer user =
        ConstantCredentialIssuer.createConstantOpenValue("super-secret-token");
    ConstantCredentialIssuer token =
        ConstantCredentialIssuer.createConstantSecret("token", "super-secret-token");
    credentialFileHandler = new CredentialFileHandler("", repoUrl.getPath(), user, token);
    tempDir = Files.createTempDirectory("gitlab_mr_destination");
    optionsBuilder =
        new OptionsBuilder()
            .setConsole(console)
            .setWorkdirToRealTempDir()
            .setHomeDir(Files.createTempDirectory("home").toString());
    optionsBuilder.gitDestination.committerEmail = "no-reply@copybara.io";
    optionsBuilder.gitDestination.committerName = "Capy Bara";
    starlarkExecutor = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void state_localBranch_matchesFormat() throws Exception {
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.of(new Project(1)));
    WriterContext writerContext = getWriterContext(false);

    GitLabMrDestination destination = getGitLabMrDestination(Optional.empty());
    GitLabMrWriter mrWriter = destination.newWriter(writerContext);
    assertThat(mrWriter.state.localBranch).matches("copybara/push-.*");
  }

  @Test
  public void state_localBranch_dryRunMode_matchesFormat() throws Exception {
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.of(new Project(1)));
    WriterContext writerContext = getWriterContext(true);

    GitLabMrDestination destination = getGitLabMrDestination(Optional.empty());
    GitLabMrWriter mrWriter = destination.newWriter(writerContext);
    assertThat(mrWriter.state.localBranch).matches("copybara/push-.*-dryrun");
  }

  @Test
  public void writer_projectApiReturnsEmpty_throwsException() throws Exception {
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.empty());
    WriterContext writerContext = getWriterContext(false);

    GitLabMrDestination destination = getGitLabMrDestination(Optional.empty());
    ValidationException e =
        assertThrows(ValidationException.class, () -> destination.newWriter(writerContext));
    assertThat(e)
        .hasMessageThat()
        .contains("GitLab API did not return a Project response for " + repoUrl);
  }

  @Test
  public void writer_projectApiThrowsError_throwsException() throws Exception {
    when(gitLabApi.getProject(anyString()))
        .thenThrow(new GitLabApiException("error", HttpStatusCodes.STATUS_CODE_SERVER_ERROR));
    WriterContext writerContext = getWriterContext(false);

    GitLabMrDestination destination = getGitLabMrDestination(Optional.empty());
    ValidationException e =
        assertThrows(ValidationException.class, () -> destination.newWriter(writerContext));
    assertThat(e).hasMessageThat().contains("Failed to query for GitLab Project status.");
  }

  @Test
  public void writer_noContextRefInRevision_throwsException() throws Exception {
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.of(new Project(12345)));
    WriterContext writerContext =
        new WriterContext(
            "workflowName",
            "workflowIdentityUser",
            false,
            new DummyRevision("1"),
            Glob.ALL_FILES.roots());

    GitLabMrDestination destination = getGitLabMrDestination(Optional.empty());
    ValidationException e =
        assertThrows(ValidationException.class, () -> destination.newWriter(writerContext));
    assertThat(e)
        .hasMessageThat()
        .contains(
            destination.getType()
                + " is incompatible with the current origin. Origin"
                + " has to be able to provide the context reference.");
  }

  @Test
  public void write_createMrWorksCorrectly() throws Exception {
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.of(new Project(12345)));
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of());
    when(gitLabApi.getListUsers(any(ListUsersParams.class)))
        .thenReturn(ImmutableList.of(new User(999)));
    when(gitLabApi.createMergeRequest(any(CreateMergeRequestParams.class)))
        .thenReturn(
            Optional.of(
                new MergeRequest(
                    12345,
                    54321,
                    "aaaa",
                    "title", "description", DetailedMergeStatus.NOT_APPROVED,
                    "source-branch",
                    "web-url",
                    State.OPENED)));
    WriterContext writerContext = getWriterContext(false);
    TransformResult transformResult =
        TransformResults.of(tempDir, new DummyRevision("1").withContextReference("contextRef"));
    GitLabMrWriter underTest = getGitLabMrDestination(Optional.empty()).newWriter(writerContext);

    ImmutableList<DestinationEffect> effects =
        underTest.write(transformResult, Glob.ALL_FILES, console);

    verify(gitLabApi)
        .createMergeRequest(
            eq(
                new CreateMergeRequestParams(
                    12345,
                    "source-branch",
                    mainBranchName,
                    "title",
                    "body",
                    ImmutableList.of(999))));

    Optional<DestinationEffect> commitCreated =
        effects.stream().filter(e -> e.getDestinationRef().getType().equals("commit")).findFirst();
    Optional<DestinationEffect> mrCreated =
        effects.stream()
            .filter(e -> e.getDestinationRef().getType().equals("merge_request"))
            .findFirst();
    assertThat(commitCreated).isPresent();
    assertThat(commitCreated.get().getType()).isEqualTo(DestinationEffect.Type.CREATED);
    assertThat(mrCreated).isPresent();
    assertThat(mrCreated.get().getType()).isEqualTo(DestinationEffect.Type.CREATED);
    String sha1 = commitCreated.get().getDestinationRef().getId();
    assertThat(
            underTest
                .state
                .localRepo
                .load(console)
                .resolveReference(underTest.state.localBranch)
                .getSha1())
        .isEqualTo(sha1);
  }

  @Test
  public void write_labelTemplatingWorksCorrectly() throws Exception {
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.of(new Project(12345)));
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of());
    when(gitLabApi.getListUsers(any(ListUsersParams.class)))
        .thenReturn(ImmutableList.of(new User(999)));
    when(gitLabApi.createMergeRequest(any(CreateMergeRequestParams.class)))
        .thenReturn(
            Optional.of(
                new MergeRequest(
                    12345,
                    54321,
                    "aaaa",
                    "title",
                    "description",
                    DetailedMergeStatus.NOT_APPROVED,
                    "source-branch-contextRef",
                    "web-url",
                    State.OPENED)));
    GitLabMrWriter underTest =
        getGitLabMrDestination(
                Optional.empty(),
                Optional.of("title-${LABEL_1}"),
                Optional.of("body-${LABEL_2}"),
                ImmutableList.of("${LABEL_3}"),
                Optional.of("source-branch-${CONTEXT_REFERENCE}"))
            .newWriter(getWriterContext(false));
    ImmutableListMultimap<String, String> labels =
        ImmutableListMultimap.of(
            "LABEL_1", "label1_value", "LABEL_2", "label2_value", "LABEL_3", "label3_value");
    TransformResult transformResult =
        TransformResults.of(tempDir, new DummyRevision("1").withContextReference("contextRef"))
            .withLabelFinder(labels::get);

    ImmutableList<DestinationEffect> unused =
        underTest.write(transformResult, Glob.ALL_FILES, console);

    verify(gitLabApi).getListUsers(eq(new ListUsersParams("label3_value")));
    verify(gitLabApi)
        .createMergeRequest(
            eq(
                new CreateMergeRequestParams(
                    12345,
                    "source-branch-contextRef",
                    mainBranchName,
                    "title-label1_value",
                    "body-label2_value",
                    ImmutableList.of(999))));
  }

  @Test
  public void write_updateMrWorksCorrectly() throws Exception {
    MergeRequest mr =
        new MergeRequest(
            12345,
            54321,
            "aaaa",
            "title", "description", DetailedMergeStatus.NOT_APPROVED,
            "source-branch",
            "web-url",
            State.OPENED);
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.of(new Project(12345)));
    when(gitLabApi.getProjectMergeRequests(anyInt(), any(ListProjectMergeRequestParams.class)))
        .thenReturn(ImmutableList.of(mr));
    when(gitLabApi.getListUsers(any(ListUsersParams.class)))
        .thenReturn(ImmutableList.of(new User(999)));
    when(gitLabApi.updateMergeRequest(any(UpdateMergeRequestParams.class)))
        .thenReturn(Optional.of(mr));
    WriterContext writerContext = getWriterContext(false);
    TransformResult transformResult =
        TransformResults.of(tempDir, new DummyRevision("1").withContextReference("contextRef"));
    GitLabMrWriter underTest = getGitLabMrDestination(Optional.empty()).newWriter(writerContext);

    ImmutableList<DestinationEffect> effects =
        underTest.write(transformResult, Glob.ALL_FILES, console);

    verify(gitLabApi)
        .updateMergeRequest(
            eq(new UpdateMergeRequestParams(12345, 54321, "title", "body", ImmutableList.of(999),
                null
                )));

    Optional<DestinationEffect> commitCreated =
        effects.stream().filter(e -> e.getDestinationRef().getType().equals("commit")).findFirst();
    Optional<DestinationEffect> mrUpdated =
        effects.stream()
            .filter(e -> e.getDestinationRef().getType().equals("merge_request"))
            .findFirst();
    assertThat(commitCreated).isPresent();
    assertThat(commitCreated.get().getType()).isEqualTo(DestinationEffect.Type.CREATED);
    assertThat(mrUpdated).isPresent();
    assertThat(mrUpdated.get().getType()).isEqualTo(DestinationEffect.Type.UPDATED);
    String sha1 = commitCreated.get().getDestinationRef().getId();
    assertThat(
            underTest
                .state
                .localRepo
                .load(console)
                .resolveReference(underTest.state.localBranch)
                .getSha1())
        .isEqualTo(sha1);
  }

  @Test
  public void describeWriter() {
    DummyChecker checker = new DummyChecker(ImmutableSet.of("badword"));
    GitLabMrDestination underTest = getGitLabMrDestination(Optional.of(checker));
    Glob destinationFiles = Glob.createGlob(ImmutableList.of("foo/1.txt", "foo/2.txt"));
    ImmutableMultimap.Builder<String, String> expected =
        ImmutableMultimap.<String, String>builder()
            .put("type", underTest.getType())
            .put("url", repoUrl.toString())
            .put("title_template", "title")
            .put("source_branch_template", "source-branch")
            .put("target_branch", mainBranchName)
            .put("allow_empty_diff", "false")
            .put("partial_fetch", "false")
            .put("checker", checker.getClass().getName());
    destinationFiles.roots().forEach(root -> expected.put("root", root));

    ImmutableSetMultimap<String, String> results = underTest.describe(destinationFiles);

    assertThat(results).containsExactlyEntriesIn(expected.build());
  }

  @Test
  public void starlark_worksCorrectly() throws Exception {
    String starlark =
"""
token_issuer = credentials.static_value("project_access_token")

destination = git.gitlab_mr_destination(
  url = "https://gitlab.com/capybara/capybara",
  credentials = credentials.username_password(
    credentials.static_value("capybara"),
    token_issuer
  ),
  auth_interceptor = http.bearer_auth(creds = token_issuer),
  source_branch = "source-branch",
  target_branch = "main",
  title = "title",
  body = "body",
  assignees = ["capybara"],
  allow_empty_diff = False,
  allow_empty_diff_merge_statuses = ['CI_MUST_PASS', 'NEED_REBASE'],
  partial_fetch = False,
  integrates = [],
  checker = None
)
""";
    GitLabMrDestination underTest = starlarkExecutor.eval("destination", starlark);

    ImmutableSetMultimap<String, String> results = underTest.describe(Glob.ALL_FILES);

    assertThat(results)
        .containsExactly(
            "type", underTest.getType(),
            "url", "https://gitlab.com/capybara/capybara",
            "title_template", "title",
            "source_branch_template", "source-branch",
            "target_branch", "main",
            "allow_empty_diff", "false",
            "partial_fetch", "false");
  }

  private static WriterContext getWriterContext(boolean dryRun) {
    return new WriterContext(
        "workflowName",
        "workflowIdentityUser",
        dryRun,
        new DummyRevision("1").withContextReference("contextRef"),
        Glob.ALL_FILES.roots());
  }

  private GitLabMrDestination getGitLabMrDestination(Optional<Checker> checker) {
    return getGitLabMrDestination(
        checker,
        Optional.of("title"),
        Optional.of("body"),
        ImmutableList.of("assignee"),
        Optional.of("source-branch"));
  }

  private GitLabMrDestination getGitLabMrDestination(
      Optional<Checker> checker,
      Optional<String> titleTemplate,
      Optional<String> bodyTemplate,
      ImmutableList<String> assigneeTemplates,
      Optional<String> sourceBranchTemplate) {
    ConfigFile configFile =
        new PathBasedConfigFile(tempDir.resolve("copy.bara.sky"), tempDir, null);
    return GitLabMrDestinationParams.builder()
        .setGitLabApi(gitLabApi)
        .setRepoUrl(repoUrl)
        .setTitleTemplate(titleTemplate)
        .setBodyTemplate(bodyTemplate)
        .setAssigneeTemplates(assigneeTemplates)
        .setCredentialFileHandler(credentialFileHandler)
        .setSourceBranchTemplate(sourceBranchTemplate)
        .setTargetBranch(mainBranchName)
        .setConfigFile(configFile)
        .setAllowEmptyDiff(false)
        .setAllowEmptyDiffMergeStatuses(ImmutableSet.of())
        .setGeneralOptions(optionsBuilder.general)
        .setGitOptions(optionsBuilder.git)
        .setDestinationOptions(optionsBuilder.gitDestination)
        .setPartialFetch(false)
        .setIntegrates(ImmutableList.of())
        .setChecker(checker)
        .build()
        .createDestination();
  }
}
