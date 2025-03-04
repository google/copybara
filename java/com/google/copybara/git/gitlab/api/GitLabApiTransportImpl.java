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

package com.google.copybara.git.gitlab.api;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.collect.ImmutableListMultimap;
import com.google.copybara.credentials.CredentialIssuingException;
import com.google.copybara.credentials.CredentialRetrievalException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.gitlab.api.entities.GitLabApiEntity;
import com.google.copybara.git.gitlab.api.entities.PaginatedPageList;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.json.GsonParserUtil;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * An implementation of {@link GitLabApiTransport} that communicates with a GitHub API Endpoint
 * using an {@link HttpTransport}. Credentials are obtained from the provided {@link
 * AuthInterceptor}.
 */
public class GitLabApiTransportImpl implements GitLabApiTransport {
  private static final String API_PATH = "api/v4";
  private static final GsonFactory GSON_FACTORY = new GsonFactory();
  private final String hostUrl;
  private final HttpTransport httpTransport;
  private final Console console;
  private final AuthInterceptor authInterceptor;

  public GitLabApiTransportImpl(
      String repoUrl,
      HttpTransport httpTransport,
      Console console,
      AuthInterceptor authInterceptor) {
    this.httpTransport = httpTransport;
    this.hostUrl = getGitLabHostUrl(repoUrl);
    this.console = console;
    this.authInterceptor = authInterceptor;
  }

  @Override
  public <T> Optional<T> get(
      String path, Type responseType, ImmutableListMultimap<String, String> headers)
      throws GitLabApiException, ValidationException {
    GenericUrl url = getFullEndpointGenericUrl(path);
    try {
      console.verboseFmt("Sending GET request to %s", url);
      HttpResponse httpResponse = getGetHttpResponse(headers, url);
      T response = GsonParserUtil.parseHttpResponse(httpResponse, responseType, false);
      if (response instanceof PaginatedPageList<?> paginatedPageList) {
        @SuppressWarnings("unchecked") // This PaginatedPageList is guaranteed to cast back to a T.
        T responseWithNextUrl =
            (T) paginatedPageList.withPaginatedInfo(getApiUrl(), httpResponse.getHeaders());
        response = responseWithNextUrl;
      }
      return Optional.ofNullable(response);
    } catch (HttpResponseException e) {
      throw new GitLabApiException(
          String.format("Error calling GET on %s", url), e.getStatusCode(), e);
    } catch (IOException e) {
      throw new GitLabApiException(String.format("Error calling GET on %s", url), e);
    } catch (IllegalArgumentException e) {
      throw new GitLabApiException(
          String.format(
              "Error calling GET on %s. Failed to parse response. Cause: %s", url, e.getMessage()),
          e);
    }
  }

  private HttpResponse getGetHttpResponse(
      ImmutableListMultimap<String, String> headers, GenericUrl url)
      throws IOException, ValidationException {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.putAll(headers.asMap());
    return getHttpRequest(url, "GET", httpHeaders, null).execute();
  }

  private HttpRequest getHttpRequest(
      GenericUrl url, String method, HttpHeaders httpHeaders, @Nullable HttpContent content)
      throws IOException, ValidationException {
    try {
      return httpTransport
          .createRequestFactory()
          .buildRequest(method, url, content)
          .setInterceptor(authInterceptor.interceptor())
          .setHeaders(httpHeaders);
    } catch (CredentialRetrievalException | CredentialIssuingException e) {
      throw new ValidationException(
          String.format("There was an issue obtaining credentials for %s: %s", url, e.getMessage()),
          e);
    }
  }

  @Override
  public <T> Optional<T> post(
      String path,
      GitLabApiEntity request,
      Type responseType,
      ImmutableListMultimap<String, String> headers)
      throws GitLabApiException, ValidationException {
    GenericUrl url = getFullEndpointGenericUrl(path);
    try {
      console.verboseFmt("Sending POST request to %s", url);
      HttpResponse httpResponse = getPostHttpResponse(request, url);
      return Optional.ofNullable(
          GsonParserUtil.parseHttpResponse(httpResponse, responseType, false));
    } catch (HttpResponseException e) {
      throw new GitLabApiException(
          String.format("Error calling POST on %s", url), e.getStatusCode(), e);
    } catch (IOException e) {
      throw new GitLabApiException(String.format("Error calling %s", url), e);
    }
  }

  private HttpResponse getPostHttpResponse(GitLabApiEntity request, GenericUrl url)
      throws IOException, ValidationException {
    return getHttpRequest(
            url, "POST", new HttpHeaders(), new JsonHttpContent(GSON_FACTORY, request))
        .execute();
  }

  @Override
  public void delete(String path) throws RepoException, ValidationException {
    GenericUrl url = getFullEndpointGenericUrl(path);
    try {
      console.verboseFmt("Sending DELETE request to %s", url);
      executeDeleteHttpRequest(url);
    } catch (HttpResponseException e) {
      throw new GitLabApiException(
          String.format("Error calling DELETE on %s", url), e.getStatusCode(), e);
    } catch (IOException e) {
      throw new GitLabApiException(String.format("Error calling %s", url), e);
    }
  }

  private void executeDeleteHttpRequest(GenericUrl url) throws IOException, ValidationException {
    var unused = getHttpRequest(url, "DELETE", new HttpHeaders(), null).execute();
  }

  private GenericUrl getFullEndpointGenericUrl(String path) {
    String trimmedPath = path.startsWith("/") ? path.substring(1) : path;
    return new GenericUrl(URI.create(getApiUrl() + "/" + trimmedPath));
  }

  private String getApiUrl() {
    return hostUrl + "/" + API_PATH;
  }

  private static String getGitLabHostUrl(String repoUrl) {
    URI parsed = URI.create(repoUrl);
    return parsed.getScheme() + "://" + getGitLabHost(parsed);
  }

  private static String getGitLabHost(URI uri) {
    return uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
  }
}
