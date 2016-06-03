// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin.Reference;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.git.GitDestination.ProcessPushOutput;
import com.google.copybara.util.console.Console;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Gerrit repository destination.
 */
public final class GerritDestination implements Destination {

  private static final class CommitGenerator implements GitDestination.CommitGenerator {

    private final GerritOptions gerritOptions;

    CommitGenerator(GerritOptions gerritOptions) {
      this.gerritOptions = Preconditions.checkNotNull(gerritOptions);
    }

    /**
     * Generates a message with a trailing Gerrit change id in the form:
     *
     * <pre>
     * Change-Id: I{SHA1 hash}
     * </pre>
     *
     * Where the hash is generated from the data in the current tree and other data, including the
     * values of the git variables {@code GIT_AUTHOR_IDENT} and {@code GIT_COMMITTER_IDENT}.
     */
    @Override
    public String message(String commitMsg, GitRepository repo, Reference<?> originRef)
        throws RepoException {
      return String.format("%s\n%s: %s\nChange-Id: %s\n",
          commitMsg,
          originRef.getLabelName(),
          originRef.asString(),
          changeId(repo)
      );
    }

    private String maybeParentHash(GitRepository repo) {
      try {
        return repo.simpleCommand("rev-parse", "HEAD^0").getStdout();
      } catch (RepoException e) {
        return "";
      }
    }

    private String changeId(GitRepository repo) throws RepoException {
      if (!Strings.isNullOrEmpty(gerritOptions.gerritChangeId)) {
        return gerritOptions.gerritChangeId;
      }

      return "I" + Hashing.sha1().newHasher()
          .putString(repo.simpleCommand("write-tree").getStdout(), Charsets.UTF_8)
          .putString(maybeParentHash(repo), Charsets.UTF_8)
          .putString(repo.simpleCommand("var", "GIT_AUTHOR_IDENT").getStdout(), Charsets.UTF_8)
          .putString(repo.simpleCommand("var", "GIT_COMMITTER_IDENT").getStdout(), Charsets.UTF_8)
          .hash();
    }

  }

  private final GitDestination gitDestination;

  private GerritDestination(GitDestination gitDestination) {
    this.gitDestination = Preconditions.checkNotNull(gitDestination);
  }

  @Override
  public void process(Path workdir, Reference<?> originRef, long timestamp,
      String changesSummary, Console console) throws RepoException {
    gitDestination.process(workdir, originRef, timestamp, changesSummary, console);
  }

  @Nullable
  @Override
  public String getPreviousRef(String labelName) throws RepoException {
    // This doesn't make sense for Gerrit since we do not plan to use previous ref for
    // pull requests.
    return null;
  }

  @DocElement(yamlName = "!GerritDestination",
      description = "Creates a change in Gerrit using the transformed worktree",
      elementKind = Destination.class, flags = {GerritOptions.class, GitOptions.class})
  public static final class Yaml extends AbstractDestinationYaml {
    @Override
    public GerritDestination withOptions(Options options, String configName)
        throws ConfigValidationException {
      checkRequiredFields();
      GeneralOptions generalOptions = options.get(GeneralOptions.class);
      return new GerritDestination(
          new GitDestination(
              configName,
              url, fetch, "refs/for/master", author,
              options.get(GitOptions.class),
              generalOptions.isVerbose(),
              new CommitGenerator(options.get(GerritOptions.class)),
              new GerritProcessPushOutput(generalOptions.console())));
    }

    static class GerritProcessPushOutput extends ProcessPushOutput {

      private static final Pattern GERRIT_URL_LINE = Pattern.compile(
          ".*: *(http(s)?://[^ ]+)( .*)?");
      private final Console console;

      GerritProcessPushOutput(Console console) {
        this.console = console;
      }

      @Override
      void process(String output) {
        List<String> lines = Splitter.on("\n").splitToList(output);
        for (Iterator<String> iterator = lines.iterator(); iterator.hasNext(); ) {
          String line = iterator.next();
          if ((line.contains("New Changes") || line.contains("Updated Changes"))
              && iterator.hasNext()) {
            String next = iterator.next();
            Matcher matcher = GERRIT_URL_LINE.matcher(next);
            if (matcher.matches()) {
              console.info("New Gerrit review created at " + matcher.group(1));
            }
          }
        }
      }
    }
  }
}
