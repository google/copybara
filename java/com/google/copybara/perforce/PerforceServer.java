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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.ChangeMessage;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.revision.Change;
import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.ChangelistStatus;
import com.perforce.p4java.core.IChangelist;
import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.IUser;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.generic.core.Changelist;
import com.perforce.p4java.impl.generic.core.User;
import com.perforce.p4java.impl.mapbased.client.Client;
import com.perforce.p4java.option.client.ReconcileFilesOptions;
import com.perforce.p4java.option.client.RevertFilesOptions;
import com.perforce.p4java.option.client.SyncOptions;
import com.perforce.p4java.option.server.GetChangelistsOptions;
import com.perforce.p4java.server.IOptionsServer;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * The single boundary between Copybara and the P4Java SDK.
 *
 * <p>Every {@code com.perforce.*} call lives here. The rest of the Perforce module deals only in
 * Copybara types ({@link Change}, {@link PerforceRevision}), which keeps the SDK contained and lets
 * the origin be unit-tested by injecting a mocked {@link IOptionsServer}.
 */
public class PerforceServer {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // How far back to scan submitted changelists when looking for the origin baseline label.
  private static final int LABEL_SCAN_LIMIT = 200;

  private final IOptionsServer server;
  // Perforce changelists only carry a username; full name and email come from the user spec, which
  // we look up at most once per author.
  private final Map<String, Author> authorCache = new HashMap<>();
  // Authors we've already created/verified as Perforce users this session (write side).
  private final Set<String> ensuredUsers = new HashSet<>();

  PerforceServer(IOptionsServer server) {
    this.server = server;
  }

  /** Returns the most recent submitted changelist affecting {@code stream}. */
  int latestChange(String stream) throws RepoException, ValidationException {
    List<Integer> ids = listChangelistIds(rangeSpec(stream, 1, -1), /* max= */ 1);
    if (ids.isEmpty()) {
      throw new CannotResolveRevisionException(
          String.format("No submitted changelists found for stream '%s'", stream));
    }
    return ids.get(0);
  }

  /** Returns true if {@code changelist} is a submitted changelist on {@code stream}. */
  boolean changeExists(String stream, int changelist) throws RepoException {
    return !listChangelistIds(rangeSpec(stream, changelist, changelist), /* max= */ 1).isEmpty();
  }

  /**
   * Returns the changes in the half-open interval {@code (from, to]} affecting {@code stream},
   * ordered oldest first.
   */
  ImmutableList<Change<PerforceRevision>> changes(String stream, int fromExclusive, int to)
      throws RepoException, ValidationException {
    int low = fromExclusive + 1;
    if (low > to) {
      return ImmutableList.of();
    }
    // 'p4 changes' lists newest first; walk it backwards so callers get chronological order. The
    // summary only carries the id, so each change is fully described (which also fetches its files).
    List<Integer> ids = listChangelistIds(rangeSpec(stream, low, to), /* max= */ -1);
    ImmutableList.Builder<Change<PerforceRevision>> changes = ImmutableList.builder();
    for (int i = ids.size() - 1; i >= 0; i--) {
      changes.add(describe(stream, ids.get(i)));
    }
    return changes.build();
  }

  /** Returns the single change identified by {@code changelist}, scoped to {@code stream}. */
  Change<PerforceRevision> describe(String stream, int changelist)
      throws RepoException, ValidationException {
    try {
      IChangelist cl = server.getChangelist(changelist);
      if (cl == null) {
        throw new CannotResolveRevisionException(
            String.format("Changelist %d cannot be found", changelist));
      }
      return toChange(stream, cl);
    } catch (P4JavaException e) {
      throw new RepoException(String.format("Error describing changelist %d", changelist), e);
    }
  }

  /**
   * Syncs {@code stream} at {@code changelist} into {@code checkoutDir} via an ephemeral
   * stream-bound client, which the server uses to materialise the stream's view. The client is
   * removed afterwards so no workspace state leaks between runs.
   */
  void syncStreamTo(String stream, int changelist, Path checkoutDir) throws RepoException {
    String clientName =
        String.format("copybara_%d_%d", ProcessHandle.current().pid(), System.nanoTime());
    boolean created = false;
    try {
      Client client = new Client(server);
      client.setName(clientName);
      client.setRoot(checkoutDir.toString());
      client.setStream(stream);
      client.setOwnerName(server.getUserName());
      server.createClient(client);
      created = true;

      IClient bound = server.getClient(clientName);
      server.setCurrentClient(bound);
      List<IFileSpec> synced =
          bound.sync(
              FileSpecBuilder.makeFileSpecList(stream + "/...@" + changelist),
              new SyncOptions().setForceUpdate(true));
      logFileSpecErrors(synced);
    } catch (P4JavaException e) {
      throw new RepoException(
          String.format("Error syncing stream '%s' at changelist %d", stream, changelist), e);
    } finally {
      if (created) {
        try {
          server.deleteClient(clientName, /* force= */ true);
        } catch (P4JavaException e) {
          logger.atWarning().withCause(e).log("Could not delete temporary client %s", clientName);
        }
      }
    }
  }

  // ===========================================================================================
  // Write side (perforce.destination)
  // ===========================================================================================

  /**
   * Returns the most recent value of {@code labelName} stamped into a submitted changelist
   * description on {@code stream}, or null if none of the recent changelists carry it. This is how
   * the destination rediscovers the last migrated origin revision for incremental syncs.
   */
  @Nullable
  String findOriginLabel(String stream, String labelName) throws RepoException {
    Pattern pattern =
        Pattern.compile("^" + Pattern.quote(labelName) + ": *(.+)$", Pattern.MULTILINE);
    GetChangelistsOptions options =
        new GetChangelistsOptions()
            .setType(IChangelist.Type.SUBMITTED)
            .setLongDesc(true)
            .setMaxMostRecent(LABEL_SCAN_LIMIT);
    try {
      for (IChangelistSummary summary :
          server.getChangelists(FileSpecBuilder.makeFileSpecList(stream + "/..."), options)) {
        String description = summary.getDescription();
        if (description == null) {
          continue;
        }
        Matcher matcher = pattern.matcher(description);
        if (matcher.find()) {
          return matcher.group(1).trim();
        }
      }
      return null;
    } catch (P4JavaException e) {
      throw new RepoException("Error scanning destination changelists for " + stream, e);
    }
  }

  /** Creates a stream-bound client named {@code clientName} rooted at {@code root} if absent. */
  void ensureClient(String clientName, Path root, String stream) throws RepoException {
    try {
      IClient existing = server.getClient(clientName);
      if (existing == null || existing.getRoot() == null) {
        Client client = new Client(server);
        client.setName(clientName);
        client.setRoot(root.toString());
        client.setStream(stream);
        client.setOwnerName(server.getUserName());
        server.createClient(client);
      }
    } catch (P4JavaException e) {
      throw new RepoException("Error creating Perforce client " + clientName, e);
    }
  }

  /**
   * Reverts any files left open in {@code clientName} (e.g. from a previously interrupted submit)
   * and syncs it to the head of {@code stream}, giving each write a clean, head-aligned workspace.
   */
  void cleanWorkspace(String clientName, String stream) throws RepoException {
    try {
      IClient client = server.getClient(clientName);
      server.setCurrentClient(client);
      client.revertFiles(
          FileSpecBuilder.makeFileSpecList(stream + "/..."), new RevertFilesOptions());
      logFileSpecErrors(
          client.sync(FileSpecBuilder.makeFileSpecList(stream + "/...#head"), new SyncOptions()));
    } catch (P4JavaException e) {
      throw new RepoException("Error preparing client " + clientName, e);
    }
  }

  /**
   * Reconciles the workspace of {@code clientName} against the depot (open adds/edits/deletes) and
   * submits them as a single changelist with {@code description}. When {@code author} is provided
   * and {@code submitAsAuthor} is set, the changelist is attributed to that author (a Perforce user
   * is created on demand) instead of the connected user. Returns the submitted changelist number.
   *
   * @throws EmptyChangeException if the workspace matches the depot, i.e. nothing to submit.
   */
  int reconcileAndSubmit(
      String clientName,
      String stream,
      String description,
      @Nullable Author author,
      boolean submitAsAuthor)
      throws RepoException, ValidationException {
    String previousUser = null;
    try {
      if (submitAsAuthor && author != null) {
        previousUser = server.getUserName();
        server.setUserName(ensureUser(author));
      }
      IClient client = server.getClient(clientName);
      server.setCurrentClient(client);

      IChangelist pending = client.createChangelist(Changelist.newChangelist(client, description));
      client.reconcileFiles(
          FileSpecBuilder.makeFileSpecList(stream + "/..."),
          new ReconcileFilesOptions()
              .setOutsideAdd(true)
              .setOutsideEdit(true)
              .setRemoved(true)
              .setChangelistId(pending.getId()));

      pending.refresh();
      if (pending.getFiles(/* refresh= */ true).isEmpty()) {
        tryDeletePending(pending.getId());
        throw new EmptyChangeException(
            "No changes to submit: the destination already matches the transformed tree");
      }

      int changelist = pending.getId();
      String submitErrors = errorMessages(pending.submit(/* reOpen= */ false));

      // A 'p4 submit' can return without throwing yet leave the changelist pending (e.g. the
      // unlicensed-server file/user cap, or a server-side error). Verify it actually landed and
      // surface the real Perforce message so the failure is loud rather than a silent drift.
      IChangelist submitted = server.getChangelist(changelist);
      if (submitted == null || submitted.getStatus() != ChangelistStatus.SUBMITTED) {
        client.revertFiles(
            FileSpecBuilder.makeFileSpecList(stream + "/..."), new RevertFilesOptions());
        tryDeletePending(changelist);
        throw new RepoException(
            String.format(
                "Perforce submit of changelist %d did not complete%s",
                changelist, submitErrors.isEmpty() ? "" : ": " + submitErrors));
      }
      return changelist;
    } catch (P4JavaException e) {
      throw new RepoException("Error submitting changelist to " + stream, e);
    } finally {
      if (previousUser != null) {
        server.setUserName(previousUser);
      }
    }
  }

  /** Ensures a Perforce user exists for {@code author}, returning the (sanitised) login name. */
  private String ensureUser(Author author) throws RepoException {
    String login = sanitizeUser(author.getEmail());
    if (ensuredUsers.add(login)) {
      try {
        server.createUser(User.newUser(login, author.getEmail(), author.getName(), ""), true);
      } catch (P4JavaException e) {
        // Most likely the user already exists from a previous run; tolerate and reuse it.
        logger.atFine().withCause(e).log("createUser(%s) failed; assuming it exists", login);
      }
    }
    return login;
  }

  private void tryDeletePending(int changelist) {
    try {
      server.deletePendingChangelist(changelist);
    } catch (P4JavaException e) {
      logger.atWarning().withCause(e).log("Could not delete empty changelist %d", changelist);
    }
  }

  private static String sanitizeUser(String email) {
    if (Strings.isNullOrEmpty(email)) {
      return "unknown";
    }
    String sanitized = email.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    return sanitized.isEmpty() ? "unknown" : sanitized;
  }

  private List<Integer> listChangelistIds(String fileSpec, int max) throws RepoException {
    GetChangelistsOptions options =
        new GetChangelistsOptions().setType(IChangelist.Type.SUBMITTED).setLongDesc(true);
    if (max > 0) {
      options.setMaxMostRecent(max);
    }
    try {
      List<IChangelistSummary> summaries =
          server.getChangelists(FileSpecBuilder.makeFileSpecList(fileSpec), options);
      List<Integer> ids = new ArrayList<>(summaries.size());
      for (IChangelistSummary summary : summaries) {
        ids.add(summary.getId());
      }
      return ids;
    } catch (P4JavaException e) {
      throw new RepoException(String.format("Error querying changelists for '%s'", fileSpec), e);
    }
  }

  private Change<PerforceRevision> toChange(String stream, IChangelist cl) {
    ZonedDateTime date = toZonedDateTime(cl.getDate());
    PerforceRevision revision = new PerforceRevision(cl.getId(), stream, date);
    String description = Strings.nullToEmpty(cl.getDescription());
    return new Change<>(
        revision,
        resolveAuthor(cl.getUsername()),
        description,
        date,
        ChangeMessage.parseAllAsLabels(description).labelsAsMultimap(),
        changeFiles(stream, cl));
  }

  private ImmutableSet<String> changeFiles(String stream, IChangelist cl) {
    String prefix = stream + "/";
    try {
      ImmutableSet.Builder<String> files = ImmutableSet.builder();
      for (IFileSpec spec : cl.getFiles(/* refresh= */ true)) {
        String depotPath = spec.getDepotPathString();
        if (depotPath == null) {
          continue;
        }
        files.add(depotPath.startsWith(prefix) ? depotPath.substring(prefix.length()) : depotPath);
      }
      return files.build();
    } catch (P4JavaException e) {
      logger.atWarning().withCause(e).log("Could not list files for changelist %d", cl.getId());
      return ImmutableSet.of();
    }
  }

  private Author resolveAuthor(String username) {
    return authorCache.computeIfAbsent(username, this::lookupAuthor);
  }

  private Author lookupAuthor(String username) {
    try {
      IUser user = server.getUser(username);
      if (user != null && !Strings.isNullOrEmpty(user.getEmail())) {
        String name = Strings.isNullOrEmpty(user.getFullName()) ? username : user.getFullName();
        return new Author(name, user.getEmail());
      }
    } catch (P4JavaException e) {
      logger.atWarning().withCause(e).log("Could not resolve Perforce user %s", username);
    }
    // No email on record: fall back to the username on both sides so authoring rules still have
    // something stable to match against.
    return new Author(username, username);
  }

  private static String rangeSpec(String stream, int low, int high) {
    // high < 0 means "no upper bound": rely on maxMostRecent to cap the result to the newest
    // change(s). Mixing a changelist lower bound with a revision upper bound (e.g. @1,#head) is
    // rejected by the server, so leave the path unqualified in that case.
    if (high < 0) {
      return stream + "/...";
    }
    return stream + "/...@" + low + "," + high;
  }

  private static ZonedDateTime toZonedDateTime(Date date) {
    return date == null ? null : ZonedDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
  }

  private static void logFileSpecErrors(List<IFileSpec> specs) {
    String errors = errorMessages(specs);
    if (!errors.isEmpty()) {
      logger.atWarning().log("Perforce reported: %s", errors);
    }
  }

  /** Joins the messages of any error/non-info file specs, for surfacing in exceptions and logs. */
  private static String errorMessages(List<IFileSpec> specs) {
    StringBuilder result = new StringBuilder();
    for (IFileSpec spec : specs) {
      if (spec == null || spec.getOpStatus() == null) {
        continue;
      }
      FileSpecOpStatus status = spec.getOpStatus();
      if (status != FileSpecOpStatus.VALID && status != FileSpecOpStatus.INFO) {
        String message = spec.getStatusMessage();
        if (!Strings.isNullOrEmpty(message)) {
          if (result.length() > 0) {
            result.append("; ");
          }
          result.append(message);
        }
      }
    }
    return result.toString();
  }

  @VisibleForTesting
  IOptionsServer getUnderlyingServer() {
    return server;
  }
}
