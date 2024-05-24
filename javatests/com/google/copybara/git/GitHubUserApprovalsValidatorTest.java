/*
 * Copyright (C) 2023 Google LLC
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
import com.google.copybara.approval.ChangeWithApprovals;
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
public final class GitHubUserApprovalsValidatorTest {
  private TestingConsole console;
  private OptionsBuilder builder;
  private GitRepository gitRepository;
  private GitTestUtil gitTestUtil;
  private GitHubHost githubHost;
  private GetCommitHistoryParams params;
  private static final String PROJECT_URL = "https://github.com/google/copybara";
  private static final String PROJECT_ID = "google/copybara";
  private static final String BRANCH = "main";

  @Before
  public void setUp() throws Exception {
    githubHost = new GitHubHost("github.com");
    params = new GetCommitHistoryParams(5, 5, 5);
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
    Path repoGitDir = Files.createTempDirectory("userApprovalValidation-repoGitDir");
    gitRepository = GitRepository.newRepo(true, repoGitDir, GitTestUtil.getGitEnv()).init();
    gitTestUtil = new GitTestUtil(builder);
    gitTestUtil.mockRemoteGitRepos();
  }

  private GitHubUserApprovalsValidator getUnitUnderTest() throws Exception {
    return new GitHubUserApprovalsValidator(
        builder.github.newGitHubApiSupplier(PROJECT_URL, null, null, githubHost),
        builder.github.newGitHubGraphQLApiSupplier(PROJECT_URL, null, null, githubHost),
        console,
        githubHost,
        params);
  }

  @Test
  public void testGitHubUserApprovalsValidator_withEmptyChange() throws Exception {
    GitHubUserApprovalsValidator validator = getUnitUnderTest();
    ImmutableList<ChangeWithApprovals> changes = ImmutableList.of();
    GetCommitHistoryParams params = new GetCommitHistoryParams(5, 5, 5);
    ImmutableList<ChangeWithApprovals> approvals =
        validator.mapApprovalsForUserPredicates(changes, BRANCH);
    assertThat(approvals).isEmpty();
  }

  @Test
  public void testGitHubUserApprovalsValidator_withNullParameter() throws Exception {
    GitHubUserApprovalsValidator validator = getUnitUnderTest();
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            gitRepository,
            PROJECT_ID,
            ImmutableListMultimap.of(),
            "3071d674373ab56d8a7f264d308b39b7773b9e44");

    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/repos/google/copybara"),
        GitTestUtil.mockResponse("{" + "\"default_branch\": \"main\"" + "}"));
    gitTestUtil.mockApi(
        eq("POST"),
        eq("https://api.github.com/graphql"),
        GitTestUtil.mockResponse(
           "{"
          + "\"data\": {"
          +   "\"repository\": {"
          +     "\"ref\": {"
          +       "\"target\": {"
          +        "\"id\": \"C_notreadatall\","
          +         "\"history\": {"
          +           "\"nodes\": ["
          +             "{"
          +               "\"id\": \"C_notreadatall\","
          +                "\"oid\": \"3071d674373ab56d8a7f264d308b39b7773b9e44\","
          +                "\"associatedPullRequests\": {"
          +                  "\"edges\": ["
          +                     "{"
          +                       "\"node\": {"
          +                         "\"title\": \"title place holder\","
          +                           "\"author\": {"
          +                             "\"login\": \"copybaraauthor\""
          +                           "},"
          +                           "\"reviewDecision\": \"CHANGES_REQUESTED\","
          +                           "\"latestOpinionatedReviews\": {"
          +                              "\"edges\": ["
          +                                "{"
          +                                 "\"node\": {"
          +                                   "\"author\": {"
          +                                     "\"login\": \"copybarareviewer\""
          +                                   "},"
          +                                   "\"state\": \"CHANGES_REQUESTED\""
          +                                 "}"
          +                                "}"
          +                              "]"
          +                            "}"
          +                           "}"
          +                         "}"
          +                       "]"
          +                     "}"
          +                   "}"
          +                 "]"
          +               "}"
          +             "}"
          +           "}"
          +         "}"
          +       "}"
          +     "}"
    ));

    ImmutableList<ChangeWithApprovals> approvals =
        validator.mapApprovalsForUserPredicates(changes, /* branch= */ null);
    assertThat(approvals).hasSize(changes.size());
    assertThat(Iterables.getOnlyElement(approvals).getPredicates()).containsNoDuplicates();
    assertThat(Iterables.getOnlyElement(approvals).getPredicates())
        .containsExactly(
            new UserPredicate(
                "copybaraauthor",
                UserPredicate.UserPredicateType.OWNER,
                Iterables.getLast(changes).getChange().getRevision().getUrl(),
                "GitHub user 'copybaraauthor' authored change with sha"
                    + " '3071d674373ab56d8a7f264d308b39b7773b9e44'."));
  }

  @Test
  public void testGitHubUserApprovalsValidator_withNoApprovals() throws Exception {
    gitTestUtil.mockApi(
        eq("POST"),
        eq("https://api.github.com/graphql"),
        GitTestUtil.mockResponse(
           "{"
          + "\"data\": {"
          +   "\"repository\": {"
          +     "\"ref\": {"
          +       "\"target\": {"
          +        "\"id\": \"C_notreadatall\","
          +         "\"history\": {"
          +           "\"nodes\": ["
          +             "{"
          +               "\"id\": \"C_notreadatall\","
          +                "\"oid\": \"3071d674373ab56d8a7f264d308b39b7773b9e44\","
          +                "\"associatedPullRequests\": {"
          +                  "\"edges\": ["
          +                     "{"
          +                       "\"node\": {"
          +                         "\"title\": \"title place holder\","
          +                           "\"author\": {"
          +                             "\"login\": \"copybaraauthor\""
          +                           "},"
          +                           "\"reviewDecision\": \"CHANGES_REQUESTED\","
          +                           "\"latestOpinionatedReviews\": {"
          +                              "\"edges\": ["
          +                                "{"
          +                                 "\"node\": {"
          +                                   "\"author\": {"
          +                                     "\"login\": \"copybarareviewer\""
          +                                   "},"
          +                                   "\"state\": \"CHANGES_REQUESTED\""
          +                                 "}"
          +                                "}"
          +                              "]"
          +                            "}"
          +                           "}"
          +                         "}"
          +                       "]"
          +                     "}"
          +                   "}"
          +                 "]"
          +               "}"
          +             "}"
          +           "}"
          +         "}"
          +       "}"
          +     "}"
    ));
    GitHubUserApprovalsValidator validator = getUnitUnderTest();
    GetCommitHistoryParams params = new GetCommitHistoryParams(5, 5, 5);
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            gitRepository,
            PROJECT_ID,
            ImmutableListMultimap.of(),
            "3071d674373ab56d8a7f264d308b39b7773b9e44");
    ImmutableList<ChangeWithApprovals> approvals =
        validator.mapApprovalsForUserPredicates(changes, BRANCH);
    assertThat(approvals).hasSize(changes.size());
    assertThat(Iterables.getOnlyElement(approvals).getPredicates()).containsNoDuplicates();
    assertThat(Iterables.getOnlyElement(approvals).getPredicates())
        .containsExactly(
            new UserPredicate(
                "copybaraauthor",
                UserPredicate.UserPredicateType.OWNER,
                Iterables.getLast(changes).getChange().getRevision().getUrl(),
                "GitHub user 'copybaraauthor' authored change with sha"
                    + " '3071d674373ab56d8a7f264d308b39b7773b9e44'."));
  }

  @Test
  public void testGitHubUserApprovalsValidator_withOnlyApprovals() throws Exception {
    gitTestUtil.mockApi(
        eq("POST"),
        eq("https://api.github.com/graphql"),
        GitTestUtil.mockResponse(
           "{"
          + "\"data\": {"
          +   "\"repository\": {"
          +     "\"ref\": {"
          +       "\"target\": {"
          +        "\"id\": \"C_notreadatall\","
          +         "\"history\": {"
          +           "\"nodes\": ["
          +             "{"
          +               "\"id\": \"C_notreadatall\","
          +                "\"oid\": \"3071d674373ab56d8a7f264d308b39b7773b9e44\","
          +                "\"associatedPullRequests\": {"
          +                  "\"edges\": ["
          +                     "{"
          +                       "\"node\": {"
          +                         "\"title\": \"title place holder\","
          +                           "\"author\": {"
          +                             "\"login\": \"copybaraauthor\""
          +                           "},"
          +                           "\"reviewDecision\": \"APPROVED\","
          +                           "\"latestOpinionatedReviews\": {"
          +                              "\"edges\": ["
          +                                "{"
          +                                 "\"node\": {"
          +                                   "\"author\": {"
          +                                     "\"login\": \"copybarareviewer\""
          +                                   "},"
          +                                   "\"state\": \"APPROVED\""
          +                                 "}"
          +                                "}"
          +                              "]"
          +                            "}"
          +                           "}"
          +                         "}"
          +                       "]"
          +                     "}"
          +                   "}"
          +                 "]"
          +               "}"
          +             "}"
          +           "}"
          +         "}"
          +       "}"
          +     "}"
    ));
    GitHubUserApprovalsValidator validator = getUnitUnderTest();
    GetCommitHistoryParams params = new GetCommitHistoryParams(5, 5, 5);
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            gitRepository,
            PROJECT_ID,
            ImmutableListMultimap.of(),
            "3071d674373ab56d8a7f264d308b39b7773b9e44");
    ImmutableList<ChangeWithApprovals> approvals =
        validator.mapApprovalsForUserPredicates(changes, BRANCH);
    assertThat(approvals).hasSize(changes.size());
    assertThat(Iterables.getOnlyElement(approvals).getPredicates()).containsNoDuplicates();
    assertThat(Iterables.getOnlyElement(approvals).getPredicates())
        .containsExactly(
            new UserPredicate(
                "copybaraauthor",
                UserPredicate.UserPredicateType.OWNER,
                Iterables.getLast(changes).getChange().getRevision().getUrl(),
                "GitHub user 'copybaraauthor' authored change with sha"
                    + " '3071d674373ab56d8a7f264d308b39b7773b9e44'."),
            new UserPredicate(
                "copybarareviewer",
                UserPredicate.UserPredicateType.LGTM,
                Iterables.getLast(changes).getChange().getRevision().getUrl(),
                "GitHub user 'copybarareviewer' approved change with sha"
                    + " '3071d674373ab56d8a7f264d308b39b7773b9e44'."));
  }

  @Test
  public void testGitHubUserApprovalsValidator_withMixedOpinionatedReviews() throws Exception {
     gitTestUtil.mockApi(
        eq("POST"),
        eq("https://api.github.com/graphql"),
        GitTestUtil.mockResponse(
           "{"
          + "\"data\": {"
          +   "\"repository\": {"
          +     "\"ref\": {"
          +       "\"target\": {"
          +        "\"id\": \"C_notreadatall\","
          +         "\"history\": {"
          +           "\"nodes\": ["
          +             "{"
          +               "\"id\": \"C_notreadatall\","
          +                "\"oid\": \"3071d674373ab56d8a7f264d308b39b7773b9e44\","
          +                "\"associatedPullRequests\": {"
          +                  "\"edges\": ["
          +                     "{"
          +                       "\"node\": {"
          +                         "\"title\": \"title place holder\","
          +                           "\"author\": {"
          +                             "\"login\": \"copybaraauthor\""
          +                           "},"
          +                           "\"reviewDecision\": \"APPROVED\","
          +                           "\"latestOpinionatedReviews\": {"
          +                              "\"edges\": ["
          +                                "{"
          +                                 "\"node\": {"
          +                                   "\"author\": {"
          +                                     "\"login\": \"copybarareviewer\""
          +                                   "},"
          +                                   "\"state\": \"APPROVED\""
          +                                 "}"
          +                                "},"
          +                                "{"
          +                                 "\"node\": {"
          +                                   "\"author\": {"
          +                                     "\"login\": \"copybarareviewer2\""
          +                                   "},"
          +                                   "\"state\": \"CHANGES_REQUESTED\""
          +                                 "}"
          +                                "}"
          +                              "]"
          +                            "}"
          +                           "}"
          +                         "}"
          +                       "]"
          +                     "}"
          +                   "}"
          +                 "]"
          +               "}"
          +             "}"
          +           "}"
          +         "}"
          +       "}"
          +     "}"
    ));
    GitHubUserApprovalsValidator validator = getUnitUnderTest();
    GetCommitHistoryParams params = new GetCommitHistoryParams(5, 5, 5);
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            gitRepository,
            PROJECT_ID,
            ImmutableListMultimap.of(),
            "3071d674373ab56d8a7f264d308b39b7773b9e44");
    ImmutableList<ChangeWithApprovals> approvals =
        validator.mapApprovalsForUserPredicates(changes, BRANCH);
    assertThat(approvals).hasSize(changes.size());
    assertThat(Iterables.getOnlyElement(approvals).getPredicates()).containsNoDuplicates();
    assertThat(Iterables.getOnlyElement(approvals).getPredicates())
        .containsExactly(
            new UserPredicate(
                "copybaraauthor",
                UserPredicate.UserPredicateType.OWNER,
                Iterables.getLast(changes).getChange().getRevision().getUrl(),
                "GitHub user 'copybaraauthor' authored change with sha"
                    + " '3071d674373ab56d8a7f264d308b39b7773b9e44'."),
            new UserPredicate(
                "copybarareviewer",
                UserPredicate.UserPredicateType.LGTM,
                Iterables.getLast(changes).getChange().getRevision().getUrl(),
                "GitHub user 'copybarareviewer' approved change with sha"
                    + " '3071d674373ab56d8a7f264d308b39b7773b9e44'."));
  }

  private ImmutableList<ChangeWithApprovals> generateChangeList(GitRepository gitRepository,
      String project, ImmutableListMultimap<String, String> labels, String... shas)
      throws Exception {
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
              labels);
      ChangeWithApprovals changeWithApprovals = new ChangeWithApprovals(change);
      changes.add(changeWithApprovals);
    }
    return changes.build();
  }
}
