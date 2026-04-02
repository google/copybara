/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.git.github.api;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.common.collect.ImmutableListMultimap;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitRepository;
import com.google.copybara.util.console.Console;
import java.io.IOException;

/**
 * An implementation of {@link GitHubApiTransport} that uses Google http client and gson for doing
 * the requests.
 */
public class GitHubApiTransportImpl extends AbstractGitHubApiTransport {

  public GitHubApiTransportImpl(
      GitRepository repo,
      HttpTransport httpTransport,
      String storePath,
      boolean bearerAuth,
      Console console,
      String webUrl) {
    super(repo, httpTransport, storePath, bearerAuth, console, webUrl);
  }

  @Override
  protected HttpResponse executeRequest(HttpRequestFactory factory, HttpRequest request)
      throws IOException {
    return request.execute();
  }

  @Override
  public void delete(String path, String requestType) throws RepoException, ValidationException {
    HttpRequestFactory requestFactory =
        getHttpRequestFactory(getCredentials(), ImmutableListMultimap.of());
    GenericUrl url = getFullEndpointUrl(path);
    try {
      console.verboseFmt("Executing %s", requestType);
      requestFactory.buildDeleteRequest(url).execute();
    } catch (HttpResponseException e) {
      throw new GitHubApiException(
          e.getStatusCode(),
          parseErrorOrIgnore(e),
          "DELETE",
          path,
          /* request= */ null,
          e.getContent());
    } catch (IOException e) {
      throw new RepoException("Error running GitHub API operation " + path, e);
    }
  }
}
