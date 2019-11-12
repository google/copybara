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

package com.google.copybara.git;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.Option;
import com.google.copybara.checks.ApiChecker;
import com.google.copybara.checks.Checker;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.gerritapi.GerritApi;
import com.google.copybara.git.gerritapi.GerritApiTransport;
import com.google.copybara.git.gerritapi.GerritApiTransportImpl;
import com.google.copybara.git.gerritapi.GerritApiTransportWithChecker;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Arguments for {@link GerritDestination}. */
@Parameters(separators = "=")
public class GerritOptions implements Option {

  private static final Pattern CHANGE_ID_PATTERN = Pattern.compile("I[0-9a-f]{40}");
  protected final GeneralOptions generalOptions;
  protected GitOptions gitOptions;

  public GerritOptions(GeneralOptions generalOptions, GitOptions gitOptions) {
    this.generalOptions = generalOptions;
    this.gitOptions = gitOptions;
  }

  /** Validate that the argument is a valid Gerrit Change-id */
  public static final class ChangeIdValidator implements IParameterValidator {

    @Override
    public void validate(String name, String value) throws ParameterException {
      if (!Strings.isNullOrEmpty(value) && !CHANGE_ID_PATTERN.matcher(value).matches()) {
        throw new ParameterException(
            String.format("%s value '%s' does not match Gerrit Change ID pattern: %s",
                name, value, CHANGE_ID_PATTERN.pattern()));
      }
    }
  }

  @Parameter(names = "--gerrit-change-id",
      description = "ChangeId to use in the generated commit message. Use this flag if you want "
          + "to reuse the same Gerrit review for an export.",
      validateWith = ChangeIdValidator.class)
  protected String gerritChangeId = "";

  @Parameter(names = "--gerrit-new-change",
      description = "Create a new change instead of trying to reuse an existing one.")
  protected boolean newChange = false;

  @Parameter(names = "--gerrit-topic", description = "Gerrit topic to use")
  protected String gerritTopic = "";

  @Parameter(names = "--nogerrit-rev-id-label", description = "DEPRECATED. Use workflow set_rev_id"
      + " field instead.", hidden = true)
  @Deprecated
  protected boolean noRevIdDEPRECATED = false;

  /**
   * Returns a lazy supplier of {@link GerritApi}.
   */
  LazyResourceLoader<GerritApi> newGerritApiSupplier(String url, @Nullable Checker checker) {
    return (console) ->
        checker == null ? newGerritApi(url) : newGerritApi(url, new ApiChecker(checker, console));
  }

  /**
   * Override this method in a class for a specific Gerrit implementation.
   */
  public GerritApi newGerritApi(String url) throws RepoException, ValidationException {
    return new GerritApi(newGerritApiTransport(hostUrl(url)),
                         generalOptions.profiler());
  }

  /**
   * Creates a new {@link GerritApi} enforcing the given {@link Checker}.
   */
  private GerritApi newGerritApi(String url, ApiChecker checker)
      throws ValidationException, RepoException {
    return new GerritApi(newGerritApiTransport(hostUrl(url), checker), generalOptions.profiler());
  }

  /**
   * Return the url removing the path part, since the API needs the host.
   */
  private static URI hostUrl(String url) throws ValidationException {
    URI result = asUri(url);
    try {
      checkCondition(result.getHost() != null, "Wrong url: %s", url);
      checkCondition(result.getScheme() != null, "Wrong url: %s", url);
      return new URI(result.getScheme(), result.getUserInfo(), result.getHost(), result.getPort(),
                     /*path=*/null, /*query=*/null, /*fragment=*/null);
    } catch (URISyntaxException e) {
      // Shouldn't happen
      throw new IllegalStateException(e);
    }
  }

  private static URI asUri(String url) throws ValidationException {
    try {
      return URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Invalid URL " + url);
    }
  }

  /**
   * Given a repo url, return the project part.
   *
   * <p>Not static on prupose, since we might introduce different behavior based on
   * other flags in the future.
   */
  @SuppressWarnings("MethodMayBeStatic")
  String getProject(String url) throws ValidationException {
    String file = asUri(url).getPath();
    if (file.startsWith("/")) {
      file = file.substring(1);
    }
    if (file.endsWith("/")) {
      file = file.substring(0, file.length() - 1);
    }
    return file.replaceAll("[ \"'&]", "");
  }

  /**
   * Create a Gerrit http transport for a URI.
   */
  protected GerritApiTransport newGerritApiTransport(URI uri)
      throws RepoException, ValidationException {
    return new GerritApiTransportImpl(getCredentialsRepo(), uri, getHttpTransport());
  }

  /**
   * Create a Gerrit http transport for a URI and {@link Checker}.
   */
  private GerritApiTransport newGerritApiTransport(URI uri, ApiChecker checker)
      throws RepoException, ValidationException {
    return new GerritApiTransportWithChecker(newGerritApiTransport(uri), checker);
  }

  @VisibleForTesting
  protected GitRepository getCredentialsRepo() throws RepoException {
    return gitOptions.cachedBareRepoForUrl("just_for_github_api");
  }

  @VisibleForTesting
  protected HttpTransport getHttpTransport() {
    return new NetHttpTransport();
  }


  /** Validate if a {@link Checker} is valid to use with a Gerrit endpoint for repoUrl. */
  public void validateEndpointChecker(@Nullable Checker checker, String repoUrl)
      throws ValidationException {
    // Accept any by default
  }
}
