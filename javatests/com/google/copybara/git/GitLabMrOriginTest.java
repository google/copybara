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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.json.gson.GsonFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Origin.Reader;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.credentials.ConstantCredentialIssuer;
import com.google.copybara.credentials.CredentialModule.UsernamePasswordIssuer;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitOrigin.SubmoduleStrategy;
import com.google.copybara.git.gitlab.GitLabUtil;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.entities.MergeRequest;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.DetailedMergeStatus;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.State;
import com.google.copybara.git.gitlab.api.entities.Project;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
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
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class GitLabMrOriginTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private GitLabApi gitLabApi;
  private static final GsonFactory GSON_FACTORY = new GsonFactory();
  private Path repoDir;
  private GitRepository testRepo;
  private TestingConsole console;
  private OptionsBuilder optionsBuilder;
  private Optional<UsernamePasswordIssuer> usernamePasswordIssuer;
  private Optional<CredentialFileHandler> credentialFileHandler;
  private URI repoUrl;
  private SkylarkTestExecutor starlarkExecutor;

  @Before
  public void setup() throws Exception {
    repoDir = Files.createTempDirectory("gitlab_test");
    testRepo = GitRepository.newRepo(true, repoDir, getGitEnv()).init();
    repoUrl = URI.create("file://" + repoDir);
    console = new TestingConsole();
    optionsBuilder =
        new OptionsBuilder()
            .setConsole(console)
            .setWorkdirToRealTempDir()
            .setHomeDir(Files.createTempDirectory("home").toString());
    optionsBuilder.gitLabOptions.setGitLabApiSupplier((unused) -> gitLabApi);
    usernamePasswordIssuer = Optional.empty();
    credentialFileHandler = Optional.empty();
    optionsBuilder.gitLabOptions.setCredentialFileHandlerSupplier(
        (unused, ignored) -> credentialFileHandler.orElse(null));
    starlarkExecutor = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void resolveLastRev() throws Exception {
    setupGitRepo();
    GitLabMrOrigin underTest = getGitLabMrOrigin(false);

    GitRevision revision =
        underTest.resolveLastRev(testRepo.resolveReference("source-branch").getSha1());

    assertThat(revision.getSha1()).isEqualTo(testRepo.resolveReference("source-branch").getSha1());
  }

  @Test
  public void resolveLastRev_notValidSha1() throws Exception {
    setupGitRepo();
    GitLabMrOrigin underTest = getGitLabMrOrigin(false);

    ValidationException e =
        assertThrows(ValidationException.class, () -> underTest.resolveLastRev("main"));

    assertThat(e).hasMessageThat().contains("'main' is not a valid SHA.");
  }

  @Test
  public void resolveMergeRequest() throws Exception {
    setupGitRepo();
    when(gitLabApi.getProject(anyString()))
        .thenReturn(Optional.ofNullable(GSON_FACTORY.fromString("{\"id\": 1}", Project.class)));
    when(gitLabApi.getMergeRequest(eq(1), eq(1)))
        .thenReturn(
            Optional.ofNullable(getMergeRequest()));
    GitLabMrOrigin underTest = getGitLabMrOrigin(false);

    GitRevision revision = underTest.resolve("1");

    assertThat(revision.getSha1())
        .isEqualTo(testRepo.resolveReference("refs/merge-requests/1/head").getSha1());
  }

  @Test
  public void resolveMergeRequest_contextRefIsPopulated() throws Exception {
    setupGitRepo();
    when(gitLabApi.getProject(anyString()))
        .thenReturn(Optional.ofNullable(GSON_FACTORY.fromString("{\"id\": 1}", Project.class)));
    when(gitLabApi.getMergeRequest(eq(1), eq(1)))
        .thenReturn(
            Optional.ofNullable(getMergeRequest()));
    GitLabMrOrigin underTest = getGitLabMrOrigin(false);

    GitRevision revision = underTest.resolve("1");

    assertThat(revision.contextReference()).isEqualTo("refs/merge-requests/1/head");
  }

  @Test
  public void resolveMergeCommit_usingMergeCommit() throws Exception {
    setupGitRepo();
    when(gitLabApi.getProject(anyString()))
        .thenReturn(Optional.ofNullable(GSON_FACTORY.fromString("{\"id\": 1}", Project.class)));
    when(gitLabApi.getMergeRequest(eq(1), eq(1)))
        .thenReturn(
            Optional.ofNullable(
                getMergeRequest()));
    GitLabMrOrigin underTest = getGitLabMrOrigin(true);

    GitRevision revision = underTest.resolve("1");

    assertThat(revision.getSha1())
        .isEqualTo(testRepo.resolveReference("refs/merge-requests/1/merge").getSha1());
  }

  @Test
  public void resolveNotANumberRef_throwsValidationException() {
    GitLabMrOrigin underTest = getGitLabMrOrigin(false);

    ValidationException e =
        assertThrows(ValidationException.class, () -> underTest.resolve("not_a_number"));
    assertThat(e)
        .hasMessageThat()
        .contains("The merge request reference not_a_number is not a valid numeric identifier.");
  }

  @Test
  public void resolveMergeRequest_projectNotFoundApiError() throws Exception {
    when(gitLabApi.getProject(anyString())).thenReturn(Optional.empty());
    GitLabMrOrigin underTest = getGitLabMrOrigin(false);

    ValidationException e = assertThrows(ValidationException.class, () -> underTest.resolve("1"));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Could not find Project "
                + GitLabUtil.getUrlEncodedProjectPath(repoUrl)
                + " in "
                + repoUrl
                + ".");
  }

  @Test
  public void resolveMergeRequest_labelsSetCorrectly() throws Exception {
    setupGitRepo();
    when(gitLabApi.getProject(anyString()))
        .thenReturn(Optional.ofNullable(GSON_FACTORY.fromString("{\"id\": 1}", Project.class)));
    when(gitLabApi.getMergeRequest(eq(1), eq(1)))
        .thenReturn(
            Optional.ofNullable(getMergeRequest()));
    GitLabMrOrigin underTest = getGitLabMrOrigin(false);

    GitRevision revision = underTest.resolve("1");

    assertThat(revision.associatedLabels())
        .containsEntry(GitLabMrOrigin.GITLAB_BASE_BRANCH_REF, "refs/merge-requests/1/base");
    assertThat(revision.associatedLabels())
        .containsEntry(GitLabMrOrigin.GITLAB_MR_TITLE, "title");
    assertThat(revision.associatedLabels())
        .containsEntry(GitLabMrOrigin.GITLAB_MR_DESCRIPTION, "description");
    assertThat(revision.associatedLabels())
        .containsEntry(GitLabMrOrigin.GITLAB_MR_URL, "https://gitlab.com/example/1");
  }

  @Test
  public void resolveMergeRequest_mrIsClosed_validationException() throws Exception {
    setupGitRepo();
    when(gitLabApi.getProject(anyString()))
        .thenReturn(Optional.ofNullable(GSON_FACTORY.fromString("{\"id\": 1}", Project.class)));
    when(gitLabApi.getMergeRequest(eq(1), eq(1)))
        .thenReturn(
            Optional.ofNullable(
                GSON_FACTORY.fromString(
                    """
                    {
                      "id": 12345,
                      "iid": 1,
                      "source_branch": "source-branch",
                      "state": "closed",
                      "web_url": "https://gitlab.com/example/1",
                      "title": "title",
                      "description": "description"
                    }
                    """,
                    MergeRequest.class)));
    GitLabMrOrigin underTest = getGitLabMrOrigin(false);

    ValidationException e = assertThrows(ValidationException.class, () -> underTest.resolve("1"));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "The merge request https://gitlab.com/example/1 must not be"
                + " marked as closed or merged.");
  }

  @Test
  public void resolveMergeRequest_mrIsMerged_validationException() throws Exception {
    setupGitRepo();
    when(gitLabApi.getProject(anyString()))
        .thenReturn(Optional.ofNullable(GSON_FACTORY.fromString("{\"id\": 1}", Project.class)));
    when(gitLabApi.getMergeRequest(eq(1), eq(1)))
        .thenReturn(
            Optional.ofNullable(
                GSON_FACTORY.fromString(
                    """
                    {
                      "id": 12345,
                      "iid": 1,
                      "source_branch": "source-branch",
                      "state": "merged",
                      "web_url": "https://gitlab.com/example/1",
                      "title": "title",
                      "description": "description"
                    }
                    """,
                    MergeRequest.class)));
    GitLabMrOrigin underTest = getGitLabMrOrigin(false);

    ValidationException e = assertThrows(ValidationException.class, () -> underTest.resolve("1"));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "The merge request https://gitlab.com/example/1 must not be"
                + " marked as closed or merged.");
  }

  @Test
  public void resolveMergeRequest_usesCredentialFileHandlerCorrectly() throws Exception {
    usernamePasswordIssuer =
        Optional.of(
            UsernamePasswordIssuer.create(
                ConstantCredentialIssuer.createConstantOpenValue("user"),
                ConstantCredentialIssuer.createConstantSecret("pass", "super-secret-token")));
    credentialFileHandler = Optional.ofNullable(Mockito.mock(CredentialFileHandler.class));
    when(credentialFileHandler.get().getUsername())
        .thenReturn(usernamePasswordIssuer.get().username().issue().provideSecret());
    when(credentialFileHandler.get().getPassword())
        .thenReturn(usernamePasswordIssuer.get().password().issue().provideSecret());
    optionsBuilder.gitLabOptions.setCredentialFileHandlerSupplier(
        (unused, ignored) -> credentialFileHandler.orElse(null));
    setupGitRepo();
    when(gitLabApi.getProject(anyString()))
        .thenReturn(Optional.ofNullable(GSON_FACTORY.fromString("{\"id\": 1}", Project.class)));
    when(gitLabApi.getMergeRequest(eq(1), eq(1)))
        .thenReturn(
            Optional.ofNullable(getMergeRequest()));
    GitLabMrOrigin underTest = getGitLabMrOrigin(false);

    GitRevision unused = underTest.resolve("1");

    verify(credentialFileHandler.get(), times(1))
        .install(any(GitRepository.class), any(Path.class));
  }

  @Test
  public void originReader_canFindBaselinesWithoutLabel() throws Exception {
    setupGitRepo();
    when(gitLabApi.getProject(anyString()))
        .thenReturn(Optional.ofNullable(GSON_FACTORY.fromString("{\"id\": 1}", Project.class)));
    when(gitLabApi.getMergeRequest(eq(1), eq(1)))
        .thenReturn(
            Optional.ofNullable(getMergeRequest()));
    GitLabMrOrigin origin = getGitLabMrOrigin(false);
    GitRevision startRevision = getGitLabMrOrigin(true).resolve("1");
    GitRevision baseline = origin.resolve("1");

    Reader<GitRevision> underTest =
        origin.newReader(
            Glob.ALL_FILES,
            new Authoring(
                Author.parse("Copy Bara <noreply@copybara.io>"),
                AuthoringMappingMode.OVERWRITE,
                ImmutableSet.of()));

    assertThat(
            underTest.findBaselinesWithoutLabel(startRevision, 1).stream()
                .map(GitRevision::getSha1))
        .containsExactly(baseline.getSha1());
  }

  @Test
  public void starlarkGitModule_returnsOriginSuccessfully() throws Exception {
    String starlark =
"""
token_issuer = credentials.static_value("project_access_token")

origin = git.gitlab_mr_origin(
  url = "https://gitlab.com/capybara/project",
  credentials = credentials.username_password(
    credentials.static_value("capybara"),
    token_issuer
  ),
  submodules = "YES",
  excluded_submodules = ["foo", "bar"],
  partial_fetch = True,
  use_merge_commit = True,
  describe_version = True,
  first_parent = True,
)
""";

    GitLabMrOrigin origin = starlarkExecutor.eval("origin", starlark);
    ImmutableSetMultimap<String, String> describe = origin.describe(Glob.ALL_FILES);

    assertThat(describe).containsEntry("type", GitLabMrOrigin.class.getCanonicalName());
    assertThat(describe).containsEntry("url", "https://gitlab.com/capybara/project");
    assertThat(describe).containsEntry("submoduleStrategy", "YES");
    assertThat(describe).containsEntry("excludedSubmodules", "[foo, bar]");
    assertThat(describe).containsEntry("partialFetch", "true");
    assertThat(describe).containsEntry("useMergeCommit", "true");
    assertThat(describe).containsEntry("describeVersion", "true");
    assertThat(describe).containsEntry("firstParent", "true");
  }

  @Test
  public void starlarkGitModule_gitlabMrOriginUrlEmpty_throwsException() {
    String starlark =
"""
token_issuer = credentials.static_value("project_access_token")

origin = git.gitlab_mr_origin(
  url = "",
  credentials = credentials.username_password(
    credentials.static_value("capybara"),
    token_issuer
  ),
)
""";

    ValidationException e =
        assertThrows(ValidationException.class, () -> starlarkExecutor.eval("origin", starlark));

    assertThat(e).hasMessageThat().contains("Invalid empty field 'url'");
  }

  @Test
  public void origin_describesCredentialsCorrectly() {
    usernamePasswordIssuer =
        Optional.of(
            UsernamePasswordIssuer.create(
                ConstantCredentialIssuer.createConstantOpenValue("user"),
                ConstantCredentialIssuer.createConstantOpenValue("pass")));

    ImmutableList<ImmutableSetMultimap<String, String>> result =
        getGitLabMrOrigin(false).describeCredentials();

    assertThat(usernamePasswordIssuer.get().describeCredentials())
        .containsExactlyElementsIn(result);
  }

  private void setupGitRepo() throws IOException, RepoException, ValidationException {
    // Prepare the test by creating a fake merge request and merge commit.
    Files.writeString(repoDir.resolve("test.txt"), "hello world");
    testRepo.add().all().run();
    String author = "Copy Bara <noreply@copybara.io>";
    testRepo.commit(author, ZonedDateTime.now(ZoneId.systemDefault()), "change1");
    String mainBranch = testRepo.getCurrentBranch();
    testRepo.branch("source-branch").run();
    Files.writeString(repoDir.resolve("test2.txt"), "not a beaver\nTEST_LABEL=foo\n");
    testRepo.add().all().run();
    testRepo.commit(author, ZonedDateTime.now(ZoneId.systemDefault()), "change2");
    // refs/merge-requests/*/head points to the head commit of the MR.
    testRepo.git(
        repoDir,
        "update-ref",
        "refs/merge-requests/1/head",
        testRepo.resolveReference("source-branch").getSha1());
    testRepo.forceCheckout(mainBranch);
    testRepo.branch("merge-branch").run();
    testRepo.merge("source-branch", ImmutableList.of()).run(ImmutableMap.of());
    // refs/merge-requests/*/merge points to the merge commit of the MR.
    testRepo.git(
        repoDir,
        "update-ref",
        "refs/merge-requests/1/merge",
        testRepo.resolveReference("merge-branch").getSha1());
  }
  
  private MergeRequest getMergeRequest() throws RepoException, CannotResolveRevisionException {
    return new MergeRequest(
        12345,
        1,
        testRepo.resolveReference("source-branch").getSha1(),
        "title",
        "description",
        DetailedMergeStatus.MERGEABLE,
        "source-branch",
        "https://gitlab.com/example/1",
        State.OPENED
    );
  }

  private GitLabMrOrigin getGitLabMrOrigin(boolean useMergeCommit) {
    return GitLabMrOrigin.builder()
        .setConsole(console)
        .setRepoUrl(repoUrl)
        .setUsernamePasswordIssuer(usernamePasswordIssuer)
        .setGitOptions(optionsBuilder.git)
        .setGitLabOptions(optionsBuilder.gitLabOptions)
        .setGitOriginOptions(optionsBuilder.gitOrigin)
        .setGeneralOptions(optionsBuilder.general)
        .setSubmoduleStrategy(SubmoduleStrategy.NO)
        .setExcludedSubmodules(ImmutableList.of())
        .setUseMergeCommit(useMergeCommit)
        .build();
  }
}
