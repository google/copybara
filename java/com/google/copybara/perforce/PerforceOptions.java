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
import com.google.common.flogger.FluentLogger;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.option.server.TrustOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.ServerFactory;
import java.net.URISyntaxException;
import java.util.Properties;
import javax.annotation.Nullable;

/** Connection arguments for Perforce, resolving from flags and falling back to P4 env vars. */
@Parameters(separators = "=")
public class PerforceOptions implements Option {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
          "Perforce password. Exchanged for a ticket via login. Defaults to the P4PASSWD"
              + " environment variable. Ignored if a token is provided.")
  String password = null;

  @Parameter(
      names = "--perforce-token",
      description =
          "Perforce login ticket to authenticate with directly (as issued by 'p4 login -p'),"
              + " instead of exchanging a password. Defaults to the P4TICKET environment variable."
              + " Takes precedence over --perforce-password.")
  String token = null;

  @Parameter(
      names = "--perforce-charset",
      description =
          "Charset for a Unicode-mode Perforce server (P4CHARSET), e.g. 'utf8'. Defaults to the"
              + " P4CHARSET environment variable, or 'utf8' when the server is Unicode-enabled.")
  String charset = null;

  @Parameter(
      names = "--perforce-ssl-fingerprint",
      description =
          "Expected SSL fingerprint of the Perforce server to pin (for 'ssl:' ports). If unset, the"
              + " server's fingerprint is trusted on first use. Defaults to the P4FINGERPRINT"
              + " environment variable.")
  String sslFingerprint = null;

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
    String resolvedToken = firstNonEmpty(token, env("P4TICKET"));
    String resolvedPassword = firstNonEmpty(password, env("P4PASSWD"));
    String resolvedCharset = firstNonEmpty(charset, env("P4CHARSET"));
    String resolvedFingerprint = firstNonEmpty(sslFingerprint, env("P4FINGERPRINT"));

    if (Strings.isNullOrEmpty(resolvedPort)) {
      throw new ValidationException(
          "No Perforce server address: set --perforce-port or the P4PORT environment variable");
    }

    try {
      IOptionsServer server = ServerFactory.getOptionsServer(toUri(resolvedPort), new Properties());

      // SSL servers require their certificate fingerprint to be trusted before connecting.
      if (resolvedPort.startsWith("ssl:")) {
        if (!Strings.isNullOrEmpty(resolvedFingerprint)) {
          server.addTrust(resolvedFingerprint, new TrustOptions());
        } else {
          logger.atWarning().log(
              "Trusting the Perforce SSL fingerprint of %s on first use; pin it with"
                  + " --perforce-ssl-fingerprint to guard against man-in-the-middle.",
              resolvedPort);
          server.addTrust(new TrustOptions().setAutoAccept(true));
        }
      }

      server.connect();

      if (!Strings.isNullOrEmpty(resolvedUser)) {
        server.setUserName(resolvedUser);
      }

      // Unicode-mode servers require a charset before any content is exchanged.
      if (server.supportsUnicode()) {
        String unicodeCharset = Strings.isNullOrEmpty(resolvedCharset) ? "utf8" : resolvedCharset;
        if (!server.setCharsetName(unicodeCharset)) {
          logger.atWarning().log(
              "Perforce charset '%s' was not accepted by the client", unicodeCharset);
        }
      } else if (!Strings.isNullOrEmpty(resolvedCharset)) {
        server.setCharsetName(resolvedCharset);
      }

      if (!Strings.isNullOrEmpty(resolvedToken)) {
        // A pre-issued ticket: use it directly, no password-for-ticket exchange.
        server.setAuthTicket(resolvedToken);
      } else if (!Strings.isNullOrEmpty(resolvedPassword)) {
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
