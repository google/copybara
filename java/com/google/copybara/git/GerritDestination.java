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

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.hash.Hashing;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.Revision;
import com.google.copybara.TransformResult;
import com.google.copybara.git.GerritChangeFinder.Response;
import com.google.copybara.git.GitDestination.MessageInfo;
import com.google.copybara.git.GitDestination.ProcessPushOutput;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;


/**
 * Gerrit repository destination.
 */
public final class GerritDestination implements Destination<GitRevision> {

  private static final class CommitGenerator implements GitDestination.CommitGenerator {

    private final GerritOptions gerritOptions;
    private final String repoUrl;
    private final Console console;

    private CommitGenerator(GerritOptions gerritOptions, String repoUrl, Console console) {
      this.gerritOptions = Preconditions.checkNotNull(gerritOptions);
      this.repoUrl = Preconditions.checkNotNull(repoUrl);
      this.console = Preconditions.checkNotNull(console);
    }

    /**
     * Returns message with a trailing Gerrit change id and if the change already exists or not. The
     * Gerrit change id has the form:
     *
     * <pre>
     * Change-Id: I{SHA1 hash}
     * </pre>
     *
     * Where the hash is generated from the data in the current tree and other data, including the
     * values of the git variables {@code GIT_AUTHOR_IDENT} and {@code GIT_COMMITTER_IDENT}. Checks
     * if the change exists or not by querying Gerrit. It needs to return if the change exists or
     * not because copybara prints a different output based on if the change already exists in
     * Gerrit or not.
     */
    @Override
    public MessageInfo message(TransformResult result, GitRepository repo)
        throws RepoException {
      MessageInfo changeIdAndNew = changeId(repo, result);

      Revision rev = result.getCurrentRevision();
      ChangeMessage msg = ChangeMessage.parseMessage(result.getSummary())
          .addOrReplaceLabel(rev.getLabelName(), ": ", rev.asString())
          .addOrReplaceLabel("Change-Id", ": ", changeIdAndNew.text);

      return new MessageInfo(msg.toString(), changeIdAndNew.newPush);
    }

    private String maybeParentHash(GitRepository repo) {
      try {
        return repo.simpleCommand("rev-parse", "HEAD^0").getStdout();
      } catch (RepoException e) {
        return "";
      }
    }

    /**
     * Returns the change id and if the change is new or not. Reuse the {@link MessageInfo} type for
     * a lack of better alternative.
     */
    private MessageInfo changeId(GitRepository repo, TransformResult transformResult)
        throws RepoException {
      if (!Strings.isNullOrEmpty(gerritOptions.gerritChangeId)) {
        return new MessageInfo(gerritOptions.gerritChangeId, /*newPush */ false);
      }
      // Try to see if there is already a Gerrit change for the origin ref and with the same author.
      String query =
          String.format(
              "owner:self status:open message:`%s: %s`",
              transformResult.getCurrentRevision().getLabelName(),
              transformResult.getCurrentRevision().asString());
      Response response = gerritOptions.getChangeFinder().get().find(repoUrl, query, console);
      if (!response.getParts().isEmpty()) {
        // Just take the first one.
        return new MessageInfo(response.getParts().get(0).getChangeId(), /*newPush */ false);
      }
      return new MessageInfo(
          "I"
              + Hashing.sha1()
                  .newHasher()
                  .putString(repo.simpleCommand("write-tree").getStdout(), Charsets.UTF_8)
                  .putString(maybeParentHash(repo), Charsets.UTF_8)
                  .putString(
                      repo.simpleCommand("var", "GIT_AUTHOR_IDENT").getStdout(), Charsets.UTF_8)
                  .putString(
                      repo.simpleCommand("var", "GIT_COMMITTER_IDENT").getStdout(), Charsets.UTF_8)
                  .hash(),
          /*newPush */ true);
    }
  }

  private final GitDestination gitDestination;

  private GerritDestination(GitDestination gitDestination) {
    this.gitDestination = Preconditions.checkNotNull(gitDestination);
  }

  public GitDestination getGitDestination() {
    return gitDestination;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("gitDestination", gitDestination)
        .toString();
  }

  @Override
  public Writer newWriter(Glob destinationFiles) {
    // TODO(matvore): Return a writer that doesn't support getPreviousRef()?
    // That method doesn't make sense for Gerrit since we do not plan to use previous ref for pull
    // requests.
    return gitDestination.newWriter(destinationFiles);
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  static GerritDestination newGerritDestination(
      Options options, String url, String fetch, String pushToRefsFor,
      boolean firstMigration, Map<String, String> environment) {
    GeneralOptions generalOptions = options.get(GeneralOptions.class);
    if (pushToRefsFor.isEmpty()) {
      pushToRefsFor = fetch;
    }
    GerritOptions gerritOptions = options.get(GerritOptions.class);
    String push =
        Strings.isNullOrEmpty(gerritOptions.gerritTopic)
            ? String.format("refs/for/%s", pushToRefsFor)
            : String.format("refs/for/%s%%topic=%s", pushToRefsFor, gerritOptions.gerritTopic);
    return new GerritDestination(
        new GitDestination(
            url,
            fetch,
            push.toString(),
            options.get(GitDestinationOptions.class),
            generalOptions.isVerbose(),
            firstMigration,
            /*skipPush=*/ false,
            new CommitGenerator(gerritOptions, url, generalOptions.console()),
            new GerritProcessPushOutput(generalOptions.console()),
            environment,
            generalOptions.console(),
            generalOptions.getTmpDirectoryFactory()));
  }

  static class GerritProcessPushOutput extends ProcessPushOutput {

    private static final Pattern GERRIT_URL_LINE = Pattern.compile(
        ".*: *(http(s)?://[^ ]+)( .*)?");
    private final Console console;

    GerritProcessPushOutput(Console console) {
      this.console = console;
    }

    @Override
    void process(String output, boolean newReview) {
      List<String> lines = Splitter.on("\n").splitToList(output);
      for (Iterator<String> iterator = lines.iterator(); iterator.hasNext(); ) {
        String line = iterator.next();
        if ((line.contains("New Changes") || line.contains("Updated Changes"))
            && iterator.hasNext()) {
          String next = iterator.next();
          Matcher matcher = GERRIT_URL_LINE.matcher(next);
          if (matcher.matches()) {
            String message = newReview
                ? "New Gerrit review created at "
                : "Updated existing Gerrit review at ";
            console.info(message + matcher.group(1));
          }
        }
      }
    }
  }

  @Override
  public Reader<GitRevision> newReader(Glob destinationFiles) {
    return gitDestination.newReader(destinationFiles);
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(@Nullable Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>();
    for (Entry<String, String> entry : gitDestination.describe(originFiles).entries()) {
      if (entry.getKey().equals("type")) {
        continue;
      }
      builder.put(entry);
    }
    return builder
        .put("type", "gerrit.destination")
        .build();

  }
}
