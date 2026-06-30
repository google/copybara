/*
 * Copyright (C) 2026 Google Inc.
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

package com.google.copybara.perforce;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.ServerFactory;
import java.net.URISyntaxException;
import java.util.Properties;
import javax.annotation.Nullable;

/** Connection arguments for Perforce, resolving from flags and falling back to P4 env vars. */
@Parameters(separators = "=")
public class PerforceOptions implements Option {

  private final GeneralOptions generalOptions;

  @Parameter(
      names = "--perforce-port",
      description =
          "Perforce server address (P4PORT), e.g. 'ssl:helix.example.com:1666'. Defaults to the"
              + " P4PORT environment variable.")
  String port = null;

  @Parameter(
      names = "--perforce-user",
      description = "Perforce user (P4USER). Defaults to the P4USER environment variable.")
  String user = null;

  @Parameter(
      names = "--perforce-password",
      description =
          "Perforce password or login ticket. Defaults to the P4PASSWD environment variable. If"
              + " empty, an existing ticket from 'p4 login' is used.")
  String password = null;

  // Lazily created and cached: a migration only ever talks to one server.
  @Nullable private PerforceServer cachedServer;

  public PerforceOptions(GeneralOptions generalOptions) {
    this.generalOptions = generalOptions;
  }

  /** Returns a connected {@link PerforceServer}, building and caching it on first use. */
  public PerforceServer server() throws RepoException, ValidationException {
    if (cachedServer == null) {
      cachedServer = new PerforceServer(connect());
    }
    return cachedServer;
  }

  private IOptionsServer connect() throws RepoException, ValidationException {
    String resolvedPort = firstNonEmpty(port, env("P4PORT"));
    String resolvedUser = firstNonEmpty(user, env("P4USER"));
    String resolvedPassword = firstNonEmpty(password, env("P4PASSWD"));

    if (Strings.isNullOrEmpty(resolvedPort)) {
      throw new ValidationException(
          "No Perforce server address: set --perforce-port or the P4PORT environment variable");
    }

    try {
      IOptionsServer server = ServerFactory.getOptionsServer(toUri(resolvedPort), new Properties());
      server.connect();
      if (!Strings.isNullOrEmpty(resolvedUser)) {
        server.setUserName(resolvedUser);
      }
      if (!Strings.isNullOrEmpty(resolvedPassword)) {
        server.login(resolvedPassword);
      }
      return server;
    } catch (URISyntaxException e) {
      throw new ValidationException("Invalid Perforce server address: " + resolvedPort, e);
    } catch (P4JavaException e) {
      throw new RepoException("Could not connect to Perforce server " + resolvedPort, e);
    }
  }

  /** Maps a P4PORT value onto a P4Java connection URI, honouring the 'ssl:' prefix. */
  private static String toUri(String p4port) {
    if (p4port.startsWith("ssl:")) {
      return "p4javassl://" + p4port.substring("ssl:".length());
    }
    return "p4java://" + p4port;
  }

  @Nullable
  private String env(String name) {
    return generalOptions.getEnvironment().get(name);
  }

  @Nullable
  private static String firstNonEmpty(@Nullable String a, @Nullable String b) {
    return Strings.isNullOrEmpty(a) ? b : a;
  }

  @VisibleForTesting
  public void setServerForTest(PerforceServer server) {
    this.cachedServer = server;
  }
}
