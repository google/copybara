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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.copybara.Option;
import com.google.copybara.credentials.CredentialModule.UsernamePasswordIssuer;
import com.google.copybara.git.CredentialFileHandler;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.GitLabApiTransport;
import com.google.copybara.git.gitlab.api.GitLabApiTransportImpl;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.util.console.Console;
import java.net.URI;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
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
  private Function<GitLabApiTransport, GitLabApi> gitLabApiSupplier = GitLabApi::new;
  private BiFunction<URI, UsernamePasswordIssuer, CredentialFileHandler>
      credentialFileHandlerSupplier =
          (url, issuer) ->
              new CredentialFileHandler(
                  url.getHost(), url.getPath(), issuer.username(), issuer.password());

  public GitLabOptions() {}

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
   * Creates a object for interacting with the GitLab API that communicates via the given transport.
   *
   * @param transport the transport to use
   * @return the API object
   */
  public GitLabApi getGitLabApi(GitLabApiTransport transport) {
    return gitLabApiSupplier.apply(transport);
  }

  /**
   * Creates a credential file handler for the given GitLab URL and credential issuer.
   *
   * @param url the GitLab URL
   * @param issuer the credential issuer
   * @return the credential file handler
   */
  public CredentialFileHandler getCredentialFileHandler(URI url, UsernamePasswordIssuer issuer) {
    return credentialFileHandlerSupplier.apply(url, issuer);
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
      Optional<AuthInterceptor> authInterceptor) {
    return new GitLabApiTransportImpl(repoUrl, httpTransport, console, authInterceptor);
  }

  /**
   * Sets the function responsible for supplying a new {@link GitLabApi} object from a {@link
   * GitLabApiTransport}.
   *
   * @param function the function to use
   */
  @VisibleForTesting
  public void setGitLabApiSupplier(Function<GitLabApiTransport, GitLabApi> function) {
    gitLabApiSupplier = function;
  }

  /**
   * Sets the function responsible for supplying a new {@link CredentialFileHandler} object from a
   * {@link URI} and {@link UsernamePasswordIssuer}.
   *
   * @param function the function to use
   */
  @VisibleForTesting
  public void setCredentialFileHandlerSupplier(
      BiFunction<URI, UsernamePasswordIssuer, CredentialFileHandler> function) {
    credentialFileHandlerSupplier = function;
  }
}
