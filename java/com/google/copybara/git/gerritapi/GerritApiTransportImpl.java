/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.copybara.git.gerritapi;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.base.Preconditions;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitCredential.UserPassword;
import com.google.copybara.git.GitRepository;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;

import javax.annotation.Nullable;

/**
 * Implementation of {@link GerritApiTransport} that uses direct http calls.
 */
@SuppressWarnings("TypeParameterUnusedInFormals")
public class GerritApiTransportImpl implements GerritApiTransport {

  private static final JsonFactory JSON_FACTORY = new GsonFactory();

  private final GitRepository repo;
  private final URI uri;
  private final HttpTransport httpTransport;

  public GerritApiTransportImpl(GitRepository repo, URI uri, HttpTransport httpTransport) {
    this.repo = repo;
    this.uri = Preconditions.checkNotNull(uri);
    this.httpTransport = Preconditions.checkNotNull(httpTransport);
  }

  @Override
  public <T> T get(String path, Type responseType)
      throws RepoException, ValidationException {
    HttpRequestFactory requestFactory = getHttpRequestFactory(
        getCredentialsIfPresent(uri.toString()));
    GenericUrl url = getUrl(path);
    try {
      return execute(responseType, requestFactory.buildGetRequest(url));
    } catch (IOException e) {
      throw new RepoException("Error running Gerrit API operation " + url, e);
    }
  }

  @Override
  public <T> T post(String path, Object request, Type responseType)
      throws RepoException, ValidationException {
    HttpRequestFactory requestFactory = getHttpRequestFactory(getCredentials(uri.toString()));
    GenericUrl url = getUrl(path);
    try {
      return execute(responseType, requestFactory.buildPostRequest(
          url, new JsonHttpContent(JSON_FACTORY, request)));
    } catch (IOException e) {
      throw new RepoException("Error running Gerrit API operation " + url, e);
    }
  }

  @Override
  public <T> T put(String path, Object request, Type responseType)
      throws RepoException, ValidationException {
    HttpRequestFactory requestFactory = getHttpRequestFactory(getCredentials(uri.toString()));
    GenericUrl url = getUrl(path);
    try {
      return execute(responseType, requestFactory.buildPutRequest(
          url, new JsonHttpContent(JSON_FACTORY, request)));
    } catch (IOException e) {
      throw new RepoException("Error running Gerrit API operation " + url, e);
    }
  }

  public GenericUrl getUrl(String path) {
    Preconditions.checkArgument(path.startsWith("/"), path);
    return new GenericUrl(uri.resolve(uri.getPath() + path));
  }

  @SuppressWarnings("unchecked")
  public static <T> T execute(Type responseType, HttpRequest httpRequest)
      throws IOException, GerritApiException {
    HttpResponse response;
    try {
      response = httpRequest.execute();
    } catch (HttpResponseException e) {
      throw new GerritApiException(e.getStatusCode(), e.getContent(), e.getContent());
    }
    try {
      return (T) response.parseAs(responseType);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format("Cannot parse response as type %s.\n"
                  + "Request: %s\n"
                  + "Response:\n%s", responseType,
              httpRequest.getUrl(), response.parseAsString()), e);
    }
  }

  /**
   * TODO(malcon): Consolidate GitHub and this one in one class
   */
  private HttpRequestFactory getHttpRequestFactory(@Nullable UserPassword userPassword)
      throws RepoException, ValidationException {
    return httpTransport.createRequestFactory(
        request -> {
          request.setConnectTimeout((int) Duration.ofMinutes(1).toMillis());
          request.setReadTimeout((int) Duration.ofMinutes(1).toMillis());
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
   * Credentials for API should be optional for any read operation (GET).
   *
   * TODO(malcon): Consolidate GitHub and this one in one class
   */
  @Nullable
  private UserPassword getCredentialsIfPresent(String url) throws RepoException {
    try {
      return getCredentials(url);
    } catch (ValidationException e) {
      return null;
    }
  }

  /**
   * Gets the credentials from git credential helper. First we try to get it for the api.github.com
   * host, just in case the user has an specific token for that url, otherwise we use the github.com
   * host one.
   *
   * TODO(malcon): Consolidate GitHub and this one in one class
   */
  private UserPassword getCredentials(String url) throws ValidationException, RepoException {
    try {
      return repo.credentialFill(url);
    } catch (ValidationException e) {
      throw new ValidationException(
          String.format("Cannot get credentials for host %s, from credentials helper", url), e);
    }
  }
}
