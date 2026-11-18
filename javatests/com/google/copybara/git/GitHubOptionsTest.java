/*
 * Copyright (C) 2025 Google LLC.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.copybara.testing.OptionsBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitHubOptionsTest {
  private GitHubOptions githubOptions;

  @Before
  public void setUp() throws Exception {
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    githubOptions = new GitHubOptions(optionsBuilder.general, optionsBuilder.git);
  }

  @Test
  public void getGitRepositoryHook_returnsGitHubRepositoryHookInstance() throws Exception {
    GitRepositoryHook hook =
        githubOptions.getGitRepositoryHook(
            new GitRepositoryHook.GitRepositoryData("123", "https://github.com/foo/bar"),
            mock(CredentialFileHandler.class));

    assertThat(hook).isInstanceOf(GitHubRepositoryHook.class);
  }

  @Test
  public void getCredentialsRepo_doesNotPassFetchUrl() throws Exception {
    OptionsBuilder optionsBuilder = new OptionsBuilder().setOutputRootToTmpDir();
    GitOptions gitOptions = spy(optionsBuilder.git);
    GitHubOptions options = new GitHubOptions(optionsBuilder.general, gitOptions);

    GitRepository repo = options.getCredentialsRepo(/* creds= */ null);

    assertThat(repo).isNotNull();
    verify(gitOptions).cachedBareRepoForUrl("just_for_github_api", (String) null);
  }

  @Test
  public void repositoryCredentials_areUsedForRestAndGraphQL() throws Exception {
    OptionsBuilder options = new OptionsBuilder().setOutputRootToTmpDir();
    Path credentials = Files.createTempFile("repository-credentials", "test");
    Files.writeString(credentials,
        "https://x-access-token:read-token@github.com/source/project\n"
            + "https://x-access-token:write-token@github.com/destination/project\n");
    options.git.credentialHelperStorePath = credentials.toString();
    new GitHubOptions(options.general, options.git).getCredentialsRepo(null)
        .replaceLocalConfigField("credential", "useHttpPath", "true");
    // Reuse the native API repository cache across both project orderings.
    for (String project : new String[] {
        "source/project", "destination/project", "source/project"}) {
      for (boolean graphql : new boolean[] {false, true}) {
        MockLowLevelHttpRequest request = new MockLowLevelHttpRequest();
        request.setResponse(new MockLowLevelHttpResponse().setStatusCode(200)
            .setContentType("application/json").setContent(graphql
                ? "{\"data\":{\"repository\":{\"object\":{\"checkSuites\":{\"nodes\":[],"
                    + "\"pageInfo\":{\"hasNextPage\":false}}}}}}"
                : "{}"));
        GitHubOptions github = new GitHubOptions(options.general, options.git) {
          @Override
          protected HttpTransport newHttpTransport() {
            return new MockHttpTransport.Builder().setLowLevelHttpRequest(request).build();
          }
        };
        github.gitHubApiBearerAuth = true;
        if (graphql) {
          String[] parts = project.split("/");
          github.newGitHubGraphQLApi("github.com", project, null, null, options.general.console())
              .getCheckRunsByNameFilter(parts[0], parts[1], "a".repeat(40), Set.of("CI"));
        } else {
          github.newGitHubRestApi("github.com", project, null, null, options.general.console())
              .getPullRequest(project, 42);
        }
        String expected = project.startsWith("source/") ? "read-token" : "write-token";
        assertThat(request.getFirstHeaderValue("Authorization")).isEqualTo("Bearer " + expected);
      }
    }
  }
}
