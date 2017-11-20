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

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.RepoException;
import com.google.copybara.git.gerritapi.GerritApi;
import com.google.copybara.git.gerritapi.GerritApiTransportImpl;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Arguments for {@link GerritDestination}. */
@Parameters(separators = "=")
public class GerritOptions implements Option {

  private static final Pattern CHANGE_ID_PATTERN = Pattern.compile("I[0-9a-f]{40}");
  protected final Supplier<GeneralOptions> generalOptionsSupplier;
  protected GitOptions gitOptions;

  public GerritOptions(Supplier<GeneralOptions> generalOptionsSupplier, GitOptions gitOptions) {
    this.generalOptionsSupplier = generalOptionsSupplier;
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

  // TODO(malcon): Remove this
  @Parameter(names = "--gerrit-use-new-api", description = "Use the new Gerrit API code",
      hidden = true)
  protected boolean newGerritApi = false;

  @Parameter(names = "--gerrit-topic", description = "Gerrit topic to use")
  protected String gerritTopic = "";

  @Parameter(names = "--nogerrit-rev-id-label", description = "Don't add origin rev-id to the"
      + " created/updated review.", hidden = true)
  public boolean noRevId = false;

  public boolean addRevId() {
    return !noRevId;
  }

  synchronized Supplier<GerritChangeFinder> getChangeFinder() {
    return Suppliers.memoize(this::newChangeFinder);
  }

  /**
   * TODO(malcon): Remove this
   */
  @Nullable
  @Deprecated
  protected GerritChangeFinder newChangeFinder() {
    return null;
  }

  /**
   * Override this method in a class for a specific Gerrit implementation.
   */
  protected final GerritApi newGerritApi(String url) throws RepoException {
    return new GerritApi(getGerritApiTransport(url), generalOptionsSupplier.get().profiler());
  }

  protected GerritApiTransportImpl getGerritApiTransport(String url) throws RepoException {
    GitRepository repo = gitOptions.cachedBareRepoForUrl("just_for_github_api");
    return new GerritApiTransportImpl(repo, url, new NetHttpTransport());
  }
}
