/*
 * Copyright (C) 2024 Google LLC.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.stream.Collectors.joining;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.credentials.Credential;
import com.google.copybara.credentials.CredentialIssuer;
import com.google.copybara.credentials.CredentialIssuingException;
import com.google.copybara.credentials.CredentialRetrievalException;
import com.google.copybara.exception.RepoException;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Holder to handle https access tokens for Git Repos. */
public class CredentialFileHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String scheme;
  private final String host;
  private final String path;
  private final CredentialIssuer username;
  private final CredentialIssuer password;
  private volatile Credential currentPassword;
  private volatile Credential currentUsername;

  private final boolean disabled;

  public CredentialFileHandler(
      String scheme,
      String host,
      String path,
      CredentialIssuer username,
      CredentialIssuer password,
      boolean disabled) {
    this.scheme = checkNotNull(scheme);
    this.host = checkNotNull(host);
    this.path = checkNotNull(path);
    this.username = checkNotNull(username);
    this.password = checkNotNull(password);
    this.disabled = disabled;
  }

  public CredentialFileHandler(
      String host,
      String path,
      CredentialIssuer username,
      CredentialIssuer password,
      boolean disabled) {
    this("https", host, path, username, password, disabled);
  }

  public CredentialFileHandler(
      String host, String path, CredentialIssuer username, CredentialIssuer password) {
    this("https", host, path, username, password, false);
  }

  public CredentialFileHandler(
      String scheme,
      String host,
      String path,
      CredentialIssuer username,
      CredentialIssuer password) {
    this(scheme, host, path, username, password, false);
  }

  /** Obtain a token for the username field from the username Issuer. */
  public String getUsername() throws CredentialIssuingException, CredentialRetrievalException {
    return getUsernameCred().provideSecret();
  }

  /** Obtain a token for the password field from the password Issuer. */
  public String getPassword() throws CredentialIssuingException, CredentialRetrievalException {
    return getPasswordCred().provideSecret();
  }

  private synchronized Credential getPasswordCred()
      throws CredentialIssuingException, CredentialRetrievalException {
    if (currentPassword == null || !currentPassword.valid()) {
      currentPassword = password.issue();
      logger.atInfo().log("Refreshing password %s", currentPassword.printableValue());
    }
    return currentPassword;
  }

  private synchronized Credential getUsernameCred() throws CredentialIssuingException {
    if (currentUsername == null || !currentUsername.valid()) {
      logger.atInfo().log("Refreshing username");
      currentUsername = username.issue();
    }
    return currentUsername;
  }

  public void install(GitRepository repo, Path credentialHelper) throws RepoException {
    if (disabled) {
      return;
    }
    repo.replaceLocalConfigField("credential", "useHttpPath", "true");
    writeTokenToCredFile(credentialHelper);
    repo.withCredentialHelper("store --file=" + credentialHelper);
  }

  /**
   * Writes an entry for the token into the given creds file. If the token has expired, calling this
   * again will update the token. Make sure that file is not in a cached dir (like the checkout dir)
   * and in one that is deleted after job completion; ideally the request dir. This implementation
   * does not manage timeouts automatically and generally tokens are expected to be valid for the
   * entire runtime. Note: credential.$host.useHttpPath has to be set for true if using more than
   * one GH repo.
   */
  public void writeTokenToCredFile(Path file) throws RepoException {
    // File is shared
    synchronized (this.getClass()) {
      try {
        List<String> lines = new ArrayList<>();
        if (Files.exists(file)) {
          lines.addAll(Files.readAllLines(file));
        }
        String entry = null;
        Pattern pattern;
        try {
          entry = getCredentialEntry(getPasswordCred().provideSecret());
          pattern = Pattern.compile(Pattern.quote(
              getCredentialEntry("PASSWORD_PLACEHOLDER"))
              .replace("PASSWORD_PLACEHOLDER", "[^@]+"));
        } catch (CredentialRetrievalException | CredentialIssuingException e) {
          throw new RepoException("Issue minting token", e);
        }
        boolean missing = true;
        for (int i = 0; i < lines.size(); i++) {
          String line = lines.get(i);
          if (line.equals(entry)) {
            logger.atInfo().log("Token for %s/%s already present, not writing file.", host, path);
            return;
          }
          Matcher matcher = pattern.matcher(line);
          if (matcher.matches()) {
            logger.atInfo().log("Updating token for %s/%s in creds file.", host, path);
            lines.set(i, entry);
            missing = false;
          } else {
            logger.atWarning().log("%s\n%s", line, pattern);
          }
        }
        if (missing) {
          logger.atInfo().log("Adding token for %s/%s in creds file.", host, path);
          lines.add(entry);
        }
        logger.atInfo().log("Wrote creds file %s.", file);
        Files.writeString(
            file,
            (lines.stream().filter(s -> !Strings.isNullOrEmpty(s)).collect(joining("\n")) + "\n"),
            TRUNCATE_EXISTING,
            CREATE);
      } catch (IOException e) {
        throw new RepoException(
            String.format("Error writing access token for %s/%s", host, path), e);
      }
    }
  }

  private String getCredentialEntry(String pw)
      throws CredentialIssuingException, CredentialRetrievalException {
    return String.format(
        "%1$s://%2$s:%3$s@%4$s/%5$s",
        scheme, URLEncoder.encode(getUsername(), UTF_8), URLEncoder.encode(pw, UTF_8), host, path);
  }

  /**
   * Helper to print a cred files without exposing tokens. Do not use this output for anything but
   * debugging
   */
  public String getScrubbedFileContentForDebug(Path file) {
    if (!Files.exists(file)) {
      return "<does not exist>";
    }
    try {
      String username = getUsername();
      String fileContent = Files.readString(file);
      return fileContent.replaceAll(Pattern.quote(username) + ":[^@]+@", username + ":<scrubbed>@");
    } catch (IOException e) {
      return "<IOException: " + e + ">";
    } catch (CredentialIssuingException | CredentialRetrievalException e) {
      return "<CredentialException: " + e + ">";
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("host", host)
        .add("path", path)
        .add("password", password.describe().toString())
        .add("username", username.describe().toString())
        .toString();
  }

  public ImmutableList<ImmutableSetMultimap<String, String>> describeCredentials() {
    return ImmutableList.of(username.describe(), password.describe());
  }
}
