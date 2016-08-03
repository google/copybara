// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.net.PercentEscaper;
import com.google.copybara.Author;
import com.google.copybara.Change;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.util.console.Console;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * A class for manipulating Git repositories
 */
public final class GitOrigin implements Origin<GitReference> {

  private static final PercentEscaper PERCENT_ESCAPER = new PercentEscaper(
      "-_", /*plusForSpace=*/ true);

  private static final DateTimeFormatter dateFormatter = DateTimeFormat.forPattern(
      "yyyy-MM-dd'T'HH:mm:ssZ");

  private static final String GIT_LOG_COMMENT_PREFIX = "    ";
  private final GitRepository repository;

  /**
   * Url of the repository
   */
  private final String repoUrl;

  /**
   * Default reference to track
   */
  @Nullable
  private final String configRef;
  private final Console console;
  private final GitRepoType repoType;

  private GitOrigin(Console console, GitRepository repository, String repoUrl,
      @Nullable String configRef, GitRepoType repoType) {
    this.console = Preconditions.checkNotNull(console);
    this.repository = Preconditions.checkNotNull(repository);
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.configRef = configRef;
    this.repoType = Preconditions.checkNotNull(repoType);
  }

  public GitRepository getRepository() {
    return repository;
  }

  /**
   * Creates a worktree with the contents of the git reference
   *
   * <p>Any content in the workdir is removed/overwritten.
   */
  @Override
  public void checkout(GitReference ref, Path workdir) throws RepoException {
    repository.withWorkTree(workdir).simpleCommand("checkout", "-q", "-f", ref.asString());
  }

  @Override
  public GitReference resolve(@Nullable String reference) throws RepoException {
    console.progress("Git Origin: Initializing local repo");
    repository.initGitDir();
    String ref;
    if (Strings.isNullOrEmpty(reference)) {
      if (configRef == null) {
        throw new RepoException("No reference was pass for " + repoUrl
            + " and no default reference was configured in the config file");
      }
      ref = configRef;
    } else {
      ref = reference;
    }
    return repoType.resolveRef(repository, repoUrl, ref, console);
  }

  @Override
  public ImmutableList<Change<GitReference>> changes(@Nullable GitReference fromRef,
      GitReference toRef) throws RepoException {

    String refRange = fromRef == null
        ? toRef.asString()
        : fromRef.asString() + ".." + toRef.asString();

    return asChanges(new QueryChanges().run(refRange));
  }


  @Override
  public Change<GitReference> change(GitReference ref) throws RepoException {
    // The limit=1 flag guarantees that only one change is returned
    return Iterables.getOnlyElement(asChanges(new QueryChanges().limit(1).run(ref.asString())));
  }

  @Override
  public void visitChanges(GitReference start, ChangesVisitor visitor) throws RepoException {
    QueryChanges queryChanges = new QueryChanges().limit(1);

    ImmutableList<GitChange> result = queryChanges.run(start.asString());
    if (result.isEmpty()) {
      throw new CannotFindReferenceException("Cannot find reference " + start.asString());
    }
    GitChange current = Iterables.getOnlyElement(result);
    while (current != null) {
      if (visitor.visit(current.change) == VisitResult.TERMINATE
          || current.parents.isEmpty()) {
        break;
      }
      current = Iterables.getOnlyElement(queryChanges.run(current.parents.get(0).asString()));
    }
  }

  private class QueryChanges {

    private int limit = -1;

    /**
     * Limit the number of results
     */
    QueryChanges limit(int limit) {
      Preconditions.checkArgument(limit > 0);
      this.limit = limit;
      return this;
    }

    public ImmutableList<GitChange> run(String refExpression)
        throws RepoException {
      List<String> params = new ArrayList<>(
          Arrays.asList("log", "--no-color", "--date=iso-strict"));

      if (limit != -1) {
        params.add("-" + limit);
      }

      params.add("--parents");
      params.add("--first-parent");

      params.add(refExpression);
      return parseChanges(
          repository.simpleCommand(params.toArray(new String[params.size()])).getStdout());
    }

    private ImmutableList<GitChange> parseChanges(String log) {
      // No changes. We cannot know until we run git log since fromRef can be null (HEAD)
      if (log.isEmpty()) {
        return ImmutableList.of();
      }

      Iterator<String> rawLines = Splitter.on('\n').split(log).iterator();
      ImmutableList.Builder<GitChange> builder = ImmutableList.builder();

      while (rawLines.hasNext()) {
        String rawCommitLine = rawLines.next();
        Iterator<String> commitReferences = Splitter.on(" ")
            .split(removePrefix(log, rawCommitLine, "commit")).iterator();

        GitReference ref = repository.createReferenceFromCompleteSha1(commitReferences.next());
        ImmutableList.Builder<GitReference> parents = ImmutableList.builder();
        while (commitReferences.hasNext()) {
          parents.add(repository.createReferenceFromCompleteSha1(commitReferences.next()));
        }
        String line = rawLines.next();
        OriginalAuthor originalAuthor = null;
        DateTime date = null;
        while (!line.isEmpty()) {
          if (line.startsWith("Author: ")) {
            String authorStr = line.substring("Author: ".length()).trim();
            originalAuthor = new GitOriginalAuthor(authorStr);
          } else if (line.startsWith("Date: ")) {
            date = dateFormatter.parseDateTime(line.substring("Date: ".length()).trim());
          }
          line = rawLines.next();
        }
        Preconditions.checkState(originalAuthor != null || date != null,
            "Could not find author and/or date for commitReferences %s in log\n:%s", rawCommitLine,
            log);
        StringBuilder message = new StringBuilder();
        // Maintain labels in order just in case we print them back in the destination.
        Map<String, String> labels = new LinkedHashMap<>();
        while (rawLines.hasNext()) {
          String s = rawLines.next();
          if (!s.startsWith(GIT_LOG_COMMENT_PREFIX)) {
            break;
          }
          LabelFinder labelFinder = new LabelFinder(
              s.substring(GIT_LOG_COMMENT_PREFIX.length()));
          if (labelFinder.isLabel()) {
            String previous = labels.put(labelFinder.getName(), labelFinder.getValue());
            if (previous != null) {
              console.warn(String.format("Possible duplicate label '%s' happening multiple times"
                      + " in commit. Keeping only the last value: '%s'\n  Discarded value: '%s'",
                  labelFinder.getName(), labelFinder.getValue(), previous));
            }
          }
          message.append(s, GIT_LOG_COMMENT_PREFIX.length(), s.length()).append("\n");
        }
        Change<GitReference> change = new Change<>(
            ref, originalAuthor, message.toString(), date, ImmutableMap.copyOf(labels));
        builder.add(new GitChange(change, parents.build()));
      }
      // Return older commit first.
      return builder.build().reverse();
    }
  }

  private ImmutableList<Change<GitReference>> asChanges(ImmutableList<GitChange> gitChanges) {
    ImmutableList.Builder<Change<GitReference>> result = ImmutableList.builder();
    for (GitChange gitChange : gitChanges) {
      result.add(gitChange.change);
    }
    return result.build();
  }

  @Override
  public String getLabelName() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  private String removePrefix(String log, String line, String prefix) {
    Preconditions.checkState(line.startsWith(prefix), "Cannot find '%s' in:\n%s", prefix, log);
    return line.substring(prefix.length()).trim();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("repoUrl", repoUrl)
        .add("ref", configRef)
        .add("repoType", repoType)
        .toString();
  }

  @DocElement(yamlName = "!GitOrigin", description = "A origin that represents a git repository",
      elementKind = Origin.class, flags = GitOptions.class)
  public final static class Yaml implements Origin.Yaml<GitReference> {
    private String url;
    private String ref;
    private GitRepoType type = GitRepoType.GIT;

    @DocField(description = "Indicates the URL of the git repository")
    public void setUrl(String url) {
      this.url = url;
    }

    @DocField(description = "Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'", required = false)
    public void setRef(String ref) {
      this.ref = ref;
    }

    @DocField(description = "Repository type. This knowledge allow Copybara to provide better"
        + " experience/integration.", required = false)
    public void setType(GitRepoType type) {
      this.type = type;
    }

    @Override
    public GitOrigin withOptions(Options options)
        throws ConfigValidationException {
      return withOptions(options, GitRepository.CURRENT_PROCESS_ENVIRONMENT);
    }

    @VisibleForTesting
    GitOrigin withOptions(Options options, Map<String, String> environment)
        throws ConfigValidationException {
      ConfigValidationException.checkNotMissing(url, "url");
      return newGitOrigin(options, url, ref, type, environment);
    }
  }

  /**
   * Builds a new {@link GitOrigin}.
   *
   * <p>This method will be invoked both from Yaml and Skylark configurations.
   */
  static GitOrigin newGitOrigin(Options options, String url, String ref, GitRepoType type,
      Map<String, String> environment) {
    GitOptions gitConfig = options.get(GitOptions.class);

    Path gitRepoStorage = FileSystems.getDefault().getPath(gitConfig.gitRepoStorage);
    Path gitDir = gitRepoStorage.resolve(PERCENT_ESCAPER.escape(url));
    Console console = options.get(GeneralOptions.class).console();

    return new GitOrigin(
        console, GitRepository.bareRepo(gitDir, options, environment), url, ref, type);
  }

  /**
   * An enhanced version of Change that contains the git parents.
   */
  private class GitChange {

    private final Change<GitReference> change;
    private final ImmutableList<GitReference> parents;

    public GitChange(Change<GitReference> change, ImmutableList<GitReference> parents) {
      this.change = change;
      this.parents = parents;
    }
  }

  /**
   * Git implementation of {@link OriginalAuthor} that parses the author from the Git logs.
   */
  private static class GitOriginalAuthor implements OriginalAuthor {
    private final Author author;

    private GitOriginalAuthor(String authorStr) {
      this.author = GitAuthorParser.parse(authorStr);
    }

    @Override
    public String getId() {
      return author.getEmail();
    }

    @Override
    public Author resolve() {
      return author;
    }
  }
}
