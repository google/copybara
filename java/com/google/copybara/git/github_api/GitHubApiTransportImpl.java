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

package com.google.copybara.git.github_api;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.base.Preconditions;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.git.GitCredential.UserPassword;
import com.google.copybara.git.GitRepository;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;
import javax.annotation.Nullable;

/**
 * An implementation of {@link GitHubApiTransport} that uses Google http client and gson for doing
 * the requests.
 */
public class GitHubApiTransportImpl implements GitHubApiTransport {

  private static final JsonFactory JSON_FACTORY = new GsonFactory();
  private static final String API_URL = "https://api.github.com";
  private static final String GITHUB_WEB_URL = "https://github.com";

  private final GitRepository repo;
  private final HttpTransport httpTransport;
  private final String storePath;

  public GitHubApiTransportImpl(GitRepository repo, HttpTransport httpTransport,
      String storePath) {
    this.repo = Preconditions.checkNotNull(repo);
    this.httpTransport = Preconditions.checkNotNull(httpTransport);
    this.storePath = storePath;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(String path, Type responseType) throws RepoException, ValidationException {
    HttpRequestFactory requestFactory = getHttpRequestFactory(getCredentialsIfPresent());

    GenericUrl url = new GenericUrl(URI.create(API_URL + "/" + path));
    try {
      HttpRequest httpRequest = requestFactory.buildGetRequest(url);
      HttpResponse response = httpRequest.execute();
      return (T) response.parseAs(responseType);
    } catch (IOException e) {
      throw new RepoException("Error running GitHub API operation " + path, e);
    }
  }

  /**
   * Credentials for API should be optional for any read operation (GET).
   */
  @Nullable
  private UserPassword getCredentialsIfPresent() throws RepoException {
    try {
      return getCredentials();
    } catch (ValidationException e) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T post(String path, Object request, Type responseType)
      throws RepoException, ValidationException {
    HttpRequestFactory requestFactory = getHttpRequestFactory(getCredentials());

    GenericUrl url = new GenericUrl(URI.create(API_URL + "/" + path));
    try {
      HttpRequest httpRequest = requestFactory.buildPostRequest(url,
          new JsonHttpContent(JSON_FACTORY, request));
      HttpResponse response = httpRequest.execute();
      return (T) response.parseAs(responseType);
    } catch (IOException e) {
      throw new RepoException("Error running GitHub API operation " + path, e);
    }
  }

  private HttpRequestFactory getHttpRequestFactory(@Nullable UserPassword userPassword)
      throws RepoException, ValidationException {
    return httpTransport.createRequestFactory(
        request -> {
          request.setConnectTimeout((int) Duration.ofSeconds(60).toMillis());
          request.setReadTimeout((int) Duration.ofSeconds(60).toMillis());
          HttpHeaders httpHeaders = new HttpHeaders();
          if (userPassword != null) {
            httpHeaders.setBasicAuthentication(userPassword.getUsername(),
                userPassword.getPassword_BeCareful());
          }
          request.setHeaders(httpHeaders);
          request.setParser(new JsonObjectParser(JSON_FACTORY));
        });
  }

  /**
   * Gets the credentials from git credential helper. First we try
   * to get it for the api.github.com host, just in case the user has an specific token for that
   * url, otherwise we use the github.com host one.
   */
  private UserPassword getCredentials() throws RepoException, ValidationException {
    try {
      return repo.credentialFill(API_URL);
    } catch (ValidationException e) {
      try {
        return repo.credentialFill(GITHUB_WEB_URL);
      } catch (ValidationException e1) {
        // Ugly, but helpful...
        throw new ValidationException(
            "Cannot get credentials for host https://api.github.com or https://github.com from"
                + " credentials helper. Make sure either your credential helper has the username"
                + " and password/token or if you don't use one, that file '" + storePath
                + "' contains one of the two lines:\n"
                + "https://USERNAME:TOKEN@api.github.com\n"
                + "or:\n"
                + "https://USERNAME:TOKEN@github.com\n"
                + "\n"
                + "Note that spaces or other special characters need to be escaped. For example"
                + " ' ' should be %20 and '@' should be %40 (For example when using the email"
                + " as username)"
            , e1);
      }
    }
  }
}
