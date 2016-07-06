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
import com.google.copybara.Authoring;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * A class for manipulating Git repositories
 */
public final class GitOrigin implements Origin<GitOrigin> {

  private static final PercentEscaper PERCENT_ESCAPER = new PercentEscaper(
      "-_", /*plusForSpace=*/ true);

  private static final DateTimeFormatter dateFormatter = DateTimeFormat.forPattern(
      "yyyy-MM-dd'T'HH:mm:ssZ");

  private static final Pattern SHA1_PATTERN = Pattern.compile("[a-f0-9]{7,40}");
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
  private final Authoring authoring;

  private GitOrigin(Console console, GitRepository repository, String repoUrl,
      @Nullable String configRef, Authoring authoring) {
    this.console = Preconditions.checkNotNull(console);
    this.repository = Preconditions.checkNotNull(repository);
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.configRef = Preconditions.checkNotNull(configRef);
    this.authoring = Preconditions.checkNotNull(authoring);
  }

  public GitRepository getRepository() {
    return repository;
  }

  @Override
  public ReferenceFiles<GitOrigin> resolve(@Nullable String reference) throws RepoException {
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
    console.progress("Git Origin: Fetching from " + repoUrl);
    Matcher sha1Matcher = SHA1_PATTERN.matcher(ref);
    if (sha1Matcher.matches()) {
      // TODO(malcon): For now we get the default refspec, but we should make this
      // configurable. Otherwise it is not going to work with Gerrit.
      repository.simpleCommand("fetch", "-f", repoUrl);
      return new GitReference(repository.revParse(ref));
    }
    repository.simpleCommand("fetch", "-f", repoUrl, ref);
    return new GitReference(repository.revParse("FETCH_HEAD"));
  }

  @Override
  public ImmutableList<Change<GitOrigin>> changes(@Nullable Reference<GitOrigin> fromRef,
      Reference<GitOrigin> toRef) throws RepoException {

    String refRange = fromRef == null
        ? toRef.asString()
        : fromRef.asString() + ".." + toRef.asString();

    return asChanges(new QueryChanges().run(refRange));
  }


  @Override
  public Change<GitOrigin> change(Reference<GitOrigin> ref) throws RepoException {
    // The limit=1 flag guarantees that only one change is returned
    return Iterables.getOnlyElement(asChanges(new QueryChanges().limit(1).run(ref.asString())));
  }

  @Override
  public void visitChanges(Reference<GitOrigin> start, ChangesVisitor visitor)
      throws RepoException {
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

        ReferenceFiles<GitOrigin> ref = new GitReference(commitReferences.next());
        ImmutableList.Builder<Reference<GitOrigin>> parents = ImmutableList.builder();
        while (commitReferences.hasNext()) {
          parents.add(new GitReference(commitReferences.next()));
        }
        String line = rawLines.next();
        Author author = null;
        DateTime date = null;
        while (!line.isEmpty()) {
          if (line.startsWith("Author: ")) {
            author = GitAuthorParser.parse(line.substring("Author: ".length()).trim());
          } else if (line.startsWith("Date: ")) {
            date = dateFormatter.parseDateTime(line.substring("Date: ".length()).trim());
          }
          line = rawLines.next();
        }
        Preconditions.checkState(author != null || date != null,
            "Could not find author and/or date for commitReferences %s in log\n:%s", rawCommitLine,
            log);
        StringBuilder message = new StringBuilder();
        ImmutableMap.Builder<String, String> labels = ImmutableMap.builder();
        while (rawLines.hasNext()) {
          String s = rawLines.next();
          if (!s.startsWith(GIT_LOG_COMMENT_PREFIX)) {
            break;
          }
          LabelFinder labelFinder = new LabelFinder(
              s.substring(GIT_LOG_COMMENT_PREFIX.length()));
          if (labelFinder.isLabel()) {
            labels.put(labelFinder.getName(), labelFinder.getValue());
          }
          message.append(s, GIT_LOG_COMMENT_PREFIX.length(), s.length()).append("\n");
        }
        Change<GitOrigin> change = new Change<>(ref, resolveAuthor(author), message.toString(),
            date, labels.build());
        builder.add(new GitChange(change, parents.build()));
      }
      // Return older commit first.
      return builder.build().reverse();
    }
  }

  private ImmutableList<Change<GitOrigin>> asChanges(ImmutableList<GitChange> gitChanges) {
    ImmutableList.Builder<Change<GitOrigin>> result = ImmutableList.builder();
    for (GitChange gitChange : gitChanges) {
      result.add(gitChange.change);
    }
    return result.build();
  }

  private Author resolveAuthor(Author author) {
    switch (authoring.getMode()) {
      case PASS_THRU:
        return author;
      case USE_DEFAULT:
        return authoring.getDefaultAuthor();
      case WHITELIST:
        return authoring.getWhitelist().contains(author.getEmail())
            ? author
            : authoring.getDefaultAuthor();
      default:
        throw new IllegalStateException(
            String.format("Mode '%s' not implemented.", authoring.getMode()));
    }
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
        .add("repository", repository)
        .add("repoUrl", repoUrl)
        .add("ref", configRef)
        .toString();
  }

  private final class GitReference implements ReferenceFiles<GitOrigin> {

    private final String reference;

    private GitReference(String reference) {
      this.reference = reference;
    }

    @Override
    public Long readTimestamp() throws RepoException {
      // -s suppresses diff output
      // --format=%at indicates show the author timestamp as the number of seconds from UNIX epoch
      String stdout = repository.simpleCommand("show", "-s", "--format=%at", reference).getStdout();
      try {
        return Long.parseLong(stdout.trim());
      } catch (NumberFormatException e) {
        throw new RepoException("Output of git show not a valid long", e);
      }
    }

    /**
     * Creates a worktree with the contents of the git reference
     *
     * <p>Any content in the workdir is removed/overwritten.
     */
    @Override
    public void checkout(Path workdir) throws RepoException {
      repository.withWorkTree(workdir).simpleCommand("checkout", "-q", "-f", reference);
    }

    @Override
    public String asString() {
      return reference;
    }

    @Override
    public String getLabelName() {
      return GitOrigin.this.getLabelName();
    }

    @Override
    public String toString() {
      return "GitReference{reference='" + reference + "', repoUrl=" + repoUrl + '}';
    }
  }

  @DocElement(yamlName = "!GitOrigin", description = "A origin that represents a git repository",
      elementKind = Origin.class, flags = GitOptions.class)
  public final static class Yaml implements Origin.Yaml<GitOrigin> {

    private String url;
    private String ref;

    @DocField(description = "Indicates the URL of the git repository")
    public void setUrl(String url) {
      this.url = url;
    }

    @DocField(description = "Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'", required = false)
    public void setRef(String ref) {
      this.ref = ref;
    }

    @Override
    public GitOrigin withOptions(Options options, Authoring authoring)
        throws ConfigValidationException {
      return withOptions(options, authoring, GitRepository.CURRENT_PROCESS_ENVIRONMENT);
    }

    @VisibleForTesting
    GitOrigin withOptions(Options options, Authoring authoring, Map<String, String> environment)
        throws ConfigValidationException {
      ConfigValidationException.checkNotMissing(url, "url");

      GitOptions gitConfig = options.get(GitOptions.class);

      Path gitRepoStorage = FileSystems.getDefault().getPath(gitConfig.gitRepoStorage);
      Path gitDir = gitRepoStorage.resolve(PERCENT_ESCAPER.escape(url));
      Console console = options.get(GeneralOptions.class).console();
      return new GitOrigin(
          console, GitRepository.bareRepo(gitDir, options, environment), url, ref, authoring);
    }
  }

  /**
   * An enhanced version of Change that contains the git parents.
   */
  private class GitChange {

    private final Change<GitOrigin> change;
    private final ImmutableList<Reference<GitOrigin>> parents;

    public GitChange(Change<GitOrigin> change,
        ImmutableList<Reference<GitOrigin>> parents) {
      this.change = change;
      this.parents = parents;
    }
  }
}
