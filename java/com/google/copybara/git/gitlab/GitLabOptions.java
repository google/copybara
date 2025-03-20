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

package com.google.copybara.git.gitlab;

import com.beust.jcommander.Parameter;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.base.Suppliers;
import com.google.copybara.Option;
import com.google.copybara.git.gitlab.api.GitLabApiTransport;
import com.google.copybara.git.gitlab.api.GitLabApiTransportImpl;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.util.console.Console;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Options related to GitLab endpoints. */
public class GitLabOptions implements Option {
  @Parameter(
      names = "--gitlab-destination-delete-mr-branch",
      description = "Overwrite git.gitlab_destination delete_pr_branch field",
      arity = 1,
      hidden = true)
  public @Nullable Boolean gitlabDeleteMrBranch = null;

  private final Supplier<HttpTransport> httpTransportSupplier =
      Suppliers.memoize(NetHttpTransport::new);

  /**
   * Obtains a supplier that returns a global instance of an HttpTransport to be used for
   * GitLab-related traffic.
   *
   * @return the supplier
   */
  public Supplier<HttpTransport> getHttpTransportSupplier() {
    return httpTransportSupplier;
  }

  /**
   * Creates a {@link GitLabApiTransport} using the provided parameters.
   *
   * @param repoUrl the repo/project URL
   * @param httpTransport the HttpTransport object to use
   * @param console The console to use for logging
   * @param authInterceptor the interceptor used for injecting authentication headers to GitLab API
   *     calls
   * @return the transport
   */
  public static GitLabApiTransport getApiTransport(
      String repoUrl,
      HttpTransport httpTransport,
      Console console,
      AuthInterceptor authInterceptor) {
    return new GitLabApiTransportImpl(repoUrl, httpTransport, console, authInterceptor);
  }
}
