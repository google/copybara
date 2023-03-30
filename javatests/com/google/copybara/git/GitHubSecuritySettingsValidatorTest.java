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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.copybara.approval.ChangeWithApprovals;
import com.google.copybara.approval.StatementPredicate;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.github.api.GitHubApi;
import com.google.copybara.revision.Change;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.console.Message.MessageType;
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
public final class GitHubSecuritySettingsValidatorTest {
  private GitRepository gitRepository;
  private OptionsBuilder builder;
  private GitTestUtil gitTestUtil;
  private TestingConsole console;
  private static final String PROJECT_ID = "google/copybara";
  private static final String ORGANIZATION = "google";

  @Before
  public void setUp() throws Exception {
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
    Path repoGitDir = Files.createTempDirectory("githubSecuritySettingsValidator-repoGitDir");
    gitRepository = GitRepository.newRepo(true, repoGitDir, GitTestUtil.getGitEnv()).init();
    gitTestUtil = new GitTestUtil(builder);
    gitTestUtil.mockRemoteGitRepos();
  }

  private GitHubSecuritySettingsValidator getUnitUnderTest(GitHubApi api) {
    return new GitHubSecuritySettingsValidator(api, console);
  }

  @Test
  public void testGitHubSecuritySettingsValidator_withEmptyChangeList() throws Exception {
    GitHubSecuritySettingsValidator validator =
        getUnitUnderTest(builder.github.newGitHubRestApi(PROJECT_ID));
    ImmutableList<ChangeWithApprovals> changes = ImmutableList.of();
    ImmutableList<ChangeWithApprovals> approvals =
        validator.mapTwoFactorAuth(changes, ORGANIZATION);
    assertThat(approvals).isEmpty();
  }

  @Test
  public void testGitHubSecuritySettingsValidator_withTwoFactorRequirementsEnabled()
      throws Exception {
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/orgs/google"),
        GitTestUtil.mockResponse("{\"two_factor_requirement_enabled\":true}"));
    GitHubSecuritySettingsValidator validator =
        getUnitUnderTest(builder.github.newGitHubRestApi(PROJECT_ID));
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            PROJECT_ID, ImmutableListMultimap.of(), "3071d674373ab56d8a7f264d308b39b7773b9e44");
    ImmutableList<ChangeWithApprovals> approvals =
        validator.mapTwoFactorAuth(changes, ORGANIZATION);
    assertThat(approvals).hasSize(changes.size());
    assertThat(Iterables.getOnlyElement(approvals).getPredicates()).containsNoDuplicates();
    assertThat(Iterables.getOnlyElement(approvals).getPredicates())
        .containsExactly(
            new StatementPredicate(
                GitHubSecuritySettingsValidator.TWO_FACTOR_PREDICATE_TYPE,
                "Whether the organization that the change originated from has two factor"
                    + " authentication requirement enabled.",
                Iterables.getLast(changes).getChange().getRevision().getUrl()));
  }

  @Test
  public void testGitHubSecuritySettingsValidator_withoutTwoFactorRequirementsEnabled()
      throws Exception {
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/orgs/google"),
        GitTestUtil.mockResponse("{\"two_factor_requirement_enabled\":false}"));
    GitHubSecuritySettingsValidator validator =
        getUnitUnderTest(builder.github.newGitHubRestApi(PROJECT_ID));
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            PROJECT_ID, ImmutableListMultimap.of(), "3071d674373ab56d8a7f264d308b39b7773b9e44");
    ImmutableList<ChangeWithApprovals> approvals =
        validator.mapTwoFactorAuth(changes, ORGANIZATION);
    assertThat(approvals).hasSize(changes.size());
    assertThat(Iterables.getOnlyElement(approvals).getPredicates()).containsNoDuplicates();
    assertThat(Iterables.getOnlyElement(approvals).getPredicates()).isEmpty();
  }

  @Test
  public void testGitHubSecuritySettingsValidator_withoutTwoFactorRequirementsVisible()
      throws Exception {
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/orgs/google"),
        GitTestUtil.mockResponse("{\"two_factor_requirement_enabled\":null}"));
    GitHubSecuritySettingsValidator validator =
        getUnitUnderTest(builder.github.newGitHubRestApi(PROJECT_ID));
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            PROJECT_ID, ImmutableListMultimap.of(), "3071d674373ab56d8a7f264d308b39b7773b9e44");
    ImmutableList<ChangeWithApprovals> approvals =
        validator.mapTwoFactorAuth(changes, ORGANIZATION);
    assertThat(approvals).hasSize(changes.size());
    assertThat(Iterables.getOnlyElement(approvals).getPredicates()).containsNoDuplicates();
    assertThat(Iterables.getOnlyElement(approvals).getPredicates()).isEmpty();
    console
        .assertThat()
        .logContains(
            MessageType.WARNING,
            "Copybara could not confirm that 2FA requirement is being enforced *");
  }

  @Test
  public void testGitHubSecuritySettingsValidator_withAllStarInstalled() throws Exception {
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/orgs/google/installations?per_page=100"),
        GitTestUtil.mockResponse("{\"installations\":[{\"app_id\": 119816}]}"));
    GitHubSecuritySettingsValidator validator =
        getUnitUnderTest(builder.github.newGitHubRestApi(PROJECT_ID));
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            PROJECT_ID, ImmutableListMultimap.of(), "3071d674373ab56d8a7f264d308b39b7773b9e44");
    ImmutableList<ChangeWithApprovals> approvals = validator.mapAllStar(changes, ORGANIZATION);
    assertThat(approvals).hasSize(changes.size());
    assertThat(Iterables.getOnlyElement(approvals).getPredicates()).containsNoDuplicates();
    assertThat(Iterables.getOnlyElement(approvals).getPredicates())
        .containsExactly(
            new StatementPredicate(
                GitHubSecuritySettingsValidator.ALL_STAR_PREDICATE_TYPE,
                "Whether the organization that the change originated from has allstar installed",
                Iterables.getLast(changes).getChange().getRevision().getUrl()));
  }

  @Test
  public void testGitHubSecuritySettingsValidator_withoutAllStarInstalled() throws Exception {
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/orgs/google/installations?per_page=100"),
        GitTestUtil.mockResponse("{\"installations\":[{\"app_id\": -1}]}"));
    GitHubSecuritySettingsValidator validator =
        getUnitUnderTest(builder.github.newGitHubRestApi(PROJECT_ID));
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            PROJECT_ID, ImmutableListMultimap.of(), "3071d674373ab56d8a7f264d308b39b7773b9e44");
    ImmutableList<ChangeWithApprovals> approvals = validator.mapAllStar(changes, ORGANIZATION);
    assertThat(approvals).hasSize(changes.size());
    assertThat(Iterables.getOnlyElement(approvals).getPredicates()).isEmpty();
  }

  @Test
  public void
      testGitHubSecuritySettingsValidator_unableToConfirmAllStarInstallationWithoutAuthorization()
          throws Exception {
    gitTestUtil.mockApi(
        eq("GET"),
        eq("https://api.github.com/orgs/google/installations?per_page=100"),
        GitTestUtil.mockGitHubUnauthorized());
    GitHubSecuritySettingsValidator validator =
        getUnitUnderTest(builder.github.newGitHubRestApi(PROJECT_ID));
    ImmutableList<ChangeWithApprovals> changes =
        generateChangeList(
            PROJECT_ID, ImmutableListMultimap.of(), "3071d674373ab56d8a7f264d308b39b7773b9e44");
    ValidationException expectedException =
        assertThrows(ValidationException.class, () -> validator.mapAllStar(changes, ORGANIZATION));
    assertThat(expectedException)
        .hasMessageThat()
        .contains("Please review your copybara app permissions, this request requires admin:read");
  }

  private ImmutableList<ChangeWithApprovals> generateChangeList(
      String project, ImmutableListMultimap<String, String> labels, String... shas)
      throws Exception {
    ImmutableList.Builder<ChangeWithApprovals> changes = ImmutableList.builder();
    GitRepository gitRepositoryLocal = gitRepository;
    for (String sha : shas) {
      GitRevision revision =
          new GitRevision(
              gitRepositoryLocal,
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
