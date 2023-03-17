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

package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.copybara.approval.ApprovalsProvider;
import com.google.copybara.approval.ApprovalsProvider.ApprovalsResult;
import com.google.copybara.approval.ChangeWithApprovals;
import com.google.copybara.approval.StatementPredicate;
import com.google.copybara.approval.UserPredicate;
import com.google.copybara.authoring.Author;
import com.google.copybara.git.github.api.GitHubGraphQLApi.GetCommitHistoryParams;
import com.google.copybara.git.github.util.GitHubHost;
import com.google.copybara.revision.Change;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GitHubPostSubmitApprovalsProviderTest {
  private OptionsBuilder builder;
  private GitTestUtil gitTestUtil;
  private TestingConsole console;
  private GitRepository gitRepository;
  private GitHubHost githubHost;
  private GetCommitHistoryParams params;
  private static final String TRUSTED_TEST_PROJECT = "google/copybara";

  @Before
  public void setUp() throws Exception {
    params = new GetCommitHistoryParams(5, 5, 5);
    githubHost = new GitHubHost("github.com");
    console = new TestingConsole();
    builder =
        new OptionsBuilder()
            .setOutputRootToTmpDir()
            .setWorkdirToRealTempDir()
            .setConsole(console)
            .setEnvironment(GitTestUtil.getGitEnv().getEnvironment());

    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.writeString(credentialsFile, "https://user:SECRET@github.com");
    builder.git.credentialHelperStorePath = credentialsFile.toString();
    Path repoGitDir = Files.createTempDirectory("githubPostSubmitApprovalProvider-repoGitDir");
    gitRepository = GitRepository.newRepo(true, repoGitDir, GitTestUtil.getGitEnv()).init();
    gitTestUtil = new GitTestUtil(builder);
    gitTestUtil.mockRemoteGitRepos();
  }

  public ApprovalsProvider getApprovalProviderUnderTest(GitHubOptions gitHubOptions)
      throws Exception {
    return new GitHubPostSubmitApprovalsProvider(
        githubHost,
        /* branch= */ "main",
        new GitHubSecuritySettingsValidator(
            gitHubOptions.newGitHubRestApi(TRUSTED_TEST_PROJECT), console),
        new GitHubUserApprovalsValidator(
            gitHubOptions.newGitHubGraphQLApi(TRUSTED_TEST_PROJECT), console, githubHost, params));
  }

  private ImmutableList<ChangeWithApprovals> generateChangeList(
      GitRepository gitRepository, String project, String... shas) throws Exception {
    ImmutableList.Builder<ChangeWithApprovals> changes = ImmutableList.builder();
    for (String sha : shas) {
      GitRevision revision =
          new GitRevision(
              gitRepository,
              sha,
              "reviewRef_unused",
              "main",
              ImmutableListMultimap.of(),
              String.format("https://github.com/%s", project));
      Change<GitRevision> change =
          new Change<>(
              revision,
              new Author("copybarauser", "copybarauser@google.com"),
              "placeholder message",
              ZonedDateTime.now(ZoneId.of("America/Los_Angeles")),
              ImmutableListMultimap.of());
      ChangeWithApprovals changeWithApprovals = new ChangeWithApprovals(change);
      changes.add(changeWithApprovals);
    }
    return changes.build();
  }

  @Test
  public void testGitHubPostSubmitApprovalsProvider_withEmptyChangeList() throws Exception {
    ApprovalsProvider underTest = getApprovalProviderUnderTest(builder.github);
    ImmutableList<ChangeWithApprovals> changes = ImmutableList.of();
    ApprovalsResult approvalsResult = underTest.computeApprovals(changes, console);
    assertThat(approvalsResult.getChanges()).isEmpty();
  }

  @Test
  public void testGitHubPostSubmitApprovalsProvider_withFullyCompliantChangeList()
      throws Exception {
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/orgs/google/installations"),
        GitTestUtil.mockResponse("{\"installations\":[{\"app_id\": 119816}]}"));
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/orgs/google"),
        GitTestUtil.mockResponse("{\"two_factor_requirement_enabled\":true}"));
    gitTestUtil.mockApi(
        eq("POST"),
        eq("https://api.github.com/graphql"),
        GitTestUtil.mockResponse(
            "{"
                + "\"data\": {"
                + "\"repository\": {"
                + "\"ref\": {"
                + "\"target\": {"
                + "\"id\": \"C_notreadatall\","
                + "\"history\": {"
                + "\"nodes\": ["
                + "{"
                + "\"id\": \"C_notreadatall\","
                + "\"oid\": \"3368ee55bcad7df67a18b588144e0888d6fa93ac\","
                + "\"associatedPullRequests\": {"
                + "\"edges\": ["
                + "{"
                + "\"node\": {"
                + "\"title\": \"title place holder\","
                + "\"author\": {"
                + "\"login\": \"copybaraauthor\""
                + "},"
                + "\"reviewDecision\": \"APPROVED\","
                + "\"latestOpinionatedReviews\": {"
                + "\"edges\": ["
                + "{"
                + "\"node\": {"
                + "\"author\": {"
                + "\"login\": \"copybarareviewer\""
                + "},"
                + "\"state\": \"APPROVED\""
                + "}"
                + "}"
                + "]"
                + "}"
                + "}"
                + "}"
                + "]"
                + "}"
                + "}"
                + "]"
                + "}"
                + "}"
                + "}"
                + "}"
                + "}"
                + "}"));
    ApprovalsProvider underTest = getApprovalProviderUnderTest(builder.github);
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            gitRepository, "google/copybara", "3368ee55bcad7df67a18b588144e0888d6fa93ac");
    ApprovalsResult approvalsResult = underTest.computeApprovals(changes, console);
    assertThat(approvalsResult.getChanges()).isNotEmpty();
    assertThat(Iterables.getOnlyElement(approvalsResult.getChanges()).getPredicates())
        .containsExactly(
            new StatementPredicate(
                GitHubSecuritySettingsValidator.TWO_FACTOR_PREDICATE_TYPE,
                "Whether the organization that the change originated from has two factor"
                    + " authentication requirement enabled.",
                Iterables.getLast(changes).getChange().getRevision().getUrl()),
            new StatementPredicate(
                GitHubSecuritySettingsValidator.ALL_STAR_PREDICATE_TYPE,
                "Whether the organization that the change originated from has allstar"
                    + " installed",
                Iterables.getLast(changes).getChange().getRevision().getUrl()),
            new UserPredicate(
                "copybaraauthor",
                UserPredicate.UserPredicateType.OWNER,
                Iterables.getLast(changes).getChange().getRevision().getUrl(),
                "GitHub user 'copybaraauthor' authored change with sha"
                    + " '3368ee55bcad7df67a18b588144e0888d6fa93ac'."),
            new UserPredicate(
                "copybarareviewer",
                UserPredicate.UserPredicateType.LGTM,
                Iterables.getLast(changes).getChange().getRevision().getUrl(),
                "GitHub user 'copybarareviewer' approved change with sha"
                    + " '3368ee55bcad7df67a18b588144e0888d6fa93ac'."));
  }

  @Test
  public void testGitHubPostSubmitApprovalsProvider_withCompliantOrgSettingsButNoApprovals()
      throws Exception {
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/orgs/google/installations"),
        GitTestUtil.mockResponse("{\"installations\":[{\"app_id\": 119816}]}"));
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/orgs/google"),
        GitTestUtil.mockResponse("{\"two_factor_requirement_enabled\":true}"));
    gitTestUtil.mockApi(
        eq("POST"),
        eq("https://api.github.com/graphql"),
        GitTestUtil.mockResponse(
            "{"
                + "\"data\": {"
                + "\"repository\": {"
                + "\"ref\": {"
                + "\"target\": {"
                + "\"id\": \"C_notreadatall\","
                + "\"history\": {"
                + "\"nodes\": ["
                + "{"
                + "\"id\": \"C_notreadatall\","
                + "\"oid\": \"3368ee55bcad7df67a18b588144e0888d6fa93ac\","
                + "\"associatedPullRequests\": {"
                + "\"edges\": ["
                + "{"
                + "\"node\": {"
                + "\"title\": \"title place holder\","
                + "\"author\": {"
                + "\"login\": \"copybaraauthor\""
                + "},"
                + "\"reviewDecision\": \"APPROVED\","
                + "\"latestOpinionatedReviews\": {"
                + "\"edges\": ["
                + "{"
                + "\"node\": {"
                + "\"author\": {"
                + "\"login\": \"copybarareviewer\""
                + "},"
                + "\"state\": \"CHANGES_REQUESTED\""
                + "}"
                + "}"
                + "]"
                + "}"
                + "}"
                + "}"
                + "]"
                + "}"
                + "}"
                + "]"
                + "}"
                + "}"
                + "}"
                + "}"
                + "}"
                + "}"));
    ApprovalsProvider underTest = getApprovalProviderUnderTest(builder.github);
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            gitRepository, "google/copybara", "3368ee55bcad7df67a18b588144e0888d6fa93ac");
    ApprovalsResult approvalsResult = underTest.computeApprovals(changes, console);
    assertThat(approvalsResult.getChanges()).isNotEmpty();
    assertThat(Iterables.getOnlyElement(approvalsResult.getChanges()).getPredicates())
        .containsExactly(
            new UserPredicate(
                "copybaraauthor",
                UserPredicate.UserPredicateType.OWNER,
                Iterables.getLast(changes).getChange().getRevision().getUrl(),
                "GitHub user 'copybaraauthor' authored change with sha"
                    + " '3368ee55bcad7df67a18b588144e0888d6fa93ac'."),
            new StatementPredicate(
                GitHubSecuritySettingsValidator.TWO_FACTOR_PREDICATE_TYPE,
                "Whether the organization that the change originated from has two factor"
                    + " authentication requirement enabled.",
                Iterables.getLast(changes).getChange().getRevision().getUrl()),
            new StatementPredicate(
                GitHubSecuritySettingsValidator.ALL_STAR_PREDICATE_TYPE,
                "Whether the organization that the change originated from has allstar"
                    + " installed",
                Iterables.getLast(changes).getChange().getRevision().getUrl()));
  }

  @Test
  public void testGitHubPostSubmitApprovalsProvider_withUnTrustWorthyOrgAndRepoSettings()
      throws Exception {
    gitTestUtil.mockApi(
        "GET",
        "https://api.github.com/orgs/google/installations",
        GitTestUtil.mockResponse("{\"installations\":[{\"app_id\": -1}]}"));
    gitTestUtil.mockApi(
        "GET",
        "https://api.github.com/orgs/google",
        GitTestUtil.mockResponse("{\"two_factor_requirement_enabled\":false}"));
    gitTestUtil.mockApi(
        eq("POST"),
        eq("https://api.github.com/graphql"),
        GitTestUtil.mockResponse(
            "{"
                + "\"data\": {"
                + "\"repository\": {"
                + "\"ref\": {"
                + "\"target\": {"
                + "\"id\": \"C_notreadatall\","
                + "\"history\": {"
                + "\"nodes\": ["
                + "{"
                + "\"id\": \"C_notreadatall\","
                + "\"oid\": \"3368ee55bcad7df67a18b588144e0888d6fa93ac\","
                + "\"associatedPullRequests\": {"
                + "\"edges\": ["
                + "{"
                + "\"node\": {"
                + "\"title\": \"title place holder\","
                + "\"author\": {"
                + "\"login\": \"copybaraauthor\""
                + "},"
                + "\"reviewDecision\": \"APPROVED\","
                + "\"latestOpinionatedReviews\": {"
                + "\"edges\": ["
                + "{"
                + "\"node\": {"
                + "\"author\": {"
                + "\"login\": \"copybarareviewer\""
                + "},"
                + "\"state\": \"APPROVED\""
                + "}"
                + "}"
                + "]"
                + "}"
                + "}"
                + "}"
                + "]"
                + "}"
                + "}"
                + "]"
                + "}"
                + "}"
                + "}"
                + "}"
                + "}"
                + "}"));
    ApprovalsProvider underTest = getApprovalProviderUnderTest(builder.github);
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            gitRepository, "google/copybara", "3368ee55bcad7df67a18b588144e0888d6fa93ac");
    ApprovalsResult approvalsResult = underTest.computeApprovals(changes, console);
    assertThat(approvalsResult.getChanges().size()).isEqualTo(changes.size());
    assertThat(Iterables.getOnlyElement(approvalsResult.getChanges()).getPredicates())
        .containsExactly(
            new UserPredicate(
                "copybaraauthor",
                UserPredicate.UserPredicateType.OWNER,
                Iterables.getLast(changes).getChange().getRevision().getUrl(),
                "GitHub user 'copybaraauthor' authored change with sha"
                    + " '3368ee55bcad7df67a18b588144e0888d6fa93ac'."),
            new UserPredicate(
                "copybarareviewer",
                UserPredicate.UserPredicateType.LGTM,
                Iterables.getLast(changes).getChange().getRevision().getUrl(),
                "GitHub user 'copybarareviewer' approved change with sha"
                    + " '3368ee55bcad7df67a18b588144e0888d6fa93ac'."));
  }
}
