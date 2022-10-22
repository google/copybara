/*
 * Copyright (C) 2022 Google Inc.
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

import com.google.api.client.http.EmptyContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitCredential.UserPassword;
import com.google.copybara.git.GitRepository;
import com.google.copybara.util.console.Console;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * An implementation of {@link GitLabApiTransport} that uses Google http client and gson for doing
 * the requests.
 */
public class GitLabApiTransportImpl implements GitLabApiTransport {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final JsonFactory JSON_FACTORY = new GsonFactory();
  private static final String API_PREFIX = "/api/v4/";
  private final GitRepository repo;
  private final HttpTransport httpTransport;
  private final String storePath;
  private final Console console;
  private final String gitlabUrl;

  public GitLabApiTransportImpl(GitRepository repo, HttpTransport httpTransport,
                                String storePath, Console console, String gitlabUrl) {
    this.repo = Preconditions.checkNotNull(repo);
    this.httpTransport = Preconditions.checkNotNull(httpTransport);
    this.storePath = storePath;
    this.console = Preconditions.checkNotNull(console);
    this.gitlabUrl = Preconditions.checkNotNull(gitlabUrl);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(String path, Type responseType, ImmutableListMultimap<String, String> headers,
                   ImmutableListMultimap<String, String> params)
      throws RepoException, ValidationException {
    HttpRequestFactory requestFactory = getHttpRequestFactory(getCredentialsIfPresent(), headers);
    GenericUrl url = new GenericUrl(URI.create(gitlabUrl + API_PREFIX + path));
    url.putAll(params.asMap());

    try {
      HttpRequest httpRequest = requestFactory.buildGetRequest(url);
      HttpResponse response = httpRequest.execute();
      Object responseObj = response.parseAs(responseType);
      return (T) responseObj;
    } catch (IOException e) {
      throw new RepoException("Error running GitLab API operation " + path, e);
    }
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private static String maybeGetLinkHeader(HttpResponse response) {
    HttpHeaders headers = response.getHeaders();
    List<String> link = (List<String>) headers.get("Link");
    if (link == null) {
      return null;
    }
    return Iterables.getOnlyElement(link);
  }

  /**
   * Credentials for API should be optional for any read operation (GET).
   */
  @Nullable
  private UserPassword getCredentialsIfPresent() throws RepoException {
    try {
      return getCredentials();
    } catch (ValidationException e) {
      String msg = String
          .format("GitHub credentials not found in %s. Assuming the repository is public.",
              storePath);
      logger.atInfo().log("%s", msg);
      console.info(msg);
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T post(String path, Type responseType, ImmutableListMultimap<String, String> params)
      throws RepoException, ValidationException {
    HttpRequestFactory requestFactory =
        getHttpRequestFactory(getCredentials(), ImmutableListMultimap.of());

    GenericUrl url = new GenericUrl(URI.create(gitlabUrl + API_PREFIX + path));
    url.putAll(params.asMap());

    try {
      HttpRequest httpRequest = requestFactory.buildPostRequest(url, new EmptyContent());
      HttpResponse response = httpRequest.execute();
      Object responseObj = response.parseAs(responseType);
      return (T) responseObj;

    } catch (IOException e) {
      throw new RepoException("Error running GitLab API operation " + path, e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T put(String path, Type responseType, ImmutableListMultimap<String, String> params)
      throws RepoException, ValidationException {
    HttpRequestFactory requestFactory =
        getHttpRequestFactory(getCredentials(), ImmutableListMultimap.of());

    GenericUrl url = new GenericUrl(URI.create(gitlabUrl + API_PREFIX + path));
    url.putAll(params.asMap());

    try {
      HttpRequest httpRequest = requestFactory.buildPutRequest(url, new EmptyContent());
      HttpResponse response = httpRequest.execute();
      Object responseObj = response.parseAs(responseType);
      return (T) responseObj;

    } catch (IOException e) {
      throw new RepoException("Error running GitLab API operation " + path, e);
    }
  }


  @Override
  public void delete(String path) throws RepoException, ValidationException {
    HttpRequestFactory requestFactory =
        getHttpRequestFactory(getCredentials(), ImmutableListMultimap.of());

    GenericUrl url = new GenericUrl(URI.create(gitlabUrl + API_PREFIX + path));
    try {
      requestFactory.buildDeleteRequest(url).execute();
    } catch (IOException e) {
      throw new RepoException("Error running GitLab API operation " + path, e);
    }
  }

  private HttpRequestFactory getHttpRequestFactory(
      @Nullable UserPassword userPassword, ImmutableListMultimap<String, String> headers) {
    return httpTransport.createRequestFactory(
        request -> {
          request.setConnectTimeout((int) Duration.ofMinutes(1).toMillis());
          request.setReadTimeout((int) Duration.ofMinutes(1).toMillis());
          HttpHeaders httpHeaders = new HttpHeaders();
          if (userPassword != null) {
            httpHeaders.set("PRIVATE-TOKEN", userPassword.getPassword_BeCareful());
          }
          for (Map.Entry<String, Collection<String>> header : headers.asMap().entrySet()) {
            httpHeaders.put(header.getKey(), header.getValue());
          }
          request.setHeaders(httpHeaders);
          request.setParser(new JsonObjectParser(JSON_FACTORY));
        });
  }

  /**
   * Gets the credentials from git credential helper. First we try
   * to get it for the gitlab.com host, just in case the user has an specific token for that
   * url
   */
  private UserPassword getCredentials() throws RepoException, ValidationException {
    try {
      return repo.credentialFill(gitlabUrl + API_PREFIX);
    } catch (ValidationException e) {
      try {
        return repo.credentialFill(gitlabUrl);
      } catch (ValidationException e1) {
        // Ugly, but helpful...
        throw new ValidationException(String.format(
            "Cannot get credentials for host https://gitlab.com/api from"
                + " credentials helper. Make sure either your credential helper has the username"
                + " and password/token or if you don't use one, that file '%s'"
                + " contains one of the two lines: \nEither:\n"
                + "https://USERNAME:TOKEN@gitlab.com\n"
                + "Note that spaces or other special characters need to be escaped. For example"
                + " ' ' should be %%20 and '@' should be %%40 (For example when using the email"
                + " as username)", storePath), e1);
      }
    }
  }
}
