package com.google.copybara.git;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.net.PercentEscaper;
import com.google.copybara.Change;
import com.google.copybara.GeneralOptions;
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
import java.util.Iterator;
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

  GitOrigin(Console console, GitRepository repository, String repoUrl, @Nullable String configRef) {
    this.console = console;
    this.repository = Preconditions.checkNotNull(repository);
    this.repoUrl = Preconditions.checkNotNull(repoUrl);
    this.configRef = Preconditions.checkNotNull(configRef);
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

    return buildChanges(repository.simpleCommand(
        "log", "--no-color", "--date=iso-strict", "--first-parent", refRange).getStdout());
  }

  @Override
  public Change<GitOrigin> change(Reference<GitOrigin> ref) throws RepoException {
    // Throws CannotFindReferenceException if ref is invalid
    String log =
        repository.simpleCommand("log", "--no-color", "--date=iso-strict", "-1", ref.asString())
            .getStdout();
    // The -1 flag guarantees that only one change is returned
    return Iterables.getOnlyElement(buildChanges(log));
  }

  private ImmutableList<Change<GitOrigin>> buildChanges(String log) {
    // No changes. We cannot know until we run git log since fromRef can be null (HEAD)
    if (log.isEmpty()) {
      return ImmutableList.of();
    }

    Iterator<String> rawLines = Splitter.on('\n').split(log).iterator();
    ImmutableList.Builder<Change<GitOrigin>> builder = ImmutableList.builder();

    while (rawLines.hasNext()) {
      String rawCommit = rawLines.next();
      String commit = removePrefix(log, rawCommit, "commit ");
      String line = rawLines.next();
      String author = null;
      DateTime date = null;
      while (!line.isEmpty()) {
        if (line.startsWith("Author: ")) {
          author = line.substring("Author: ".length()).trim();
        } else if (line.startsWith("Date: ")) {
          date = dateFormatter.parseDateTime(line.substring("Date: ".length()).trim());
        }
        line = rawLines.next();
      }
      Preconditions.checkState(author != null || date != null,
          "Could not find author and/or date for commit %s in log\n:%s", rawCommit, log);
      StringBuilder message = new StringBuilder();
      while (rawLines.hasNext()) {
        String s = rawLines.next();
        if (!s.startsWith("    ")) {
          break;
        }
        message.append(s, 4, s.length()).append("\n");
      }
      builder.add(new Change<>(new GitReference(commit), author, message.toString(), date));
    }
    // Return older commit first. This operation is O(1)
    return builder.build().reverse();
  }

  @Override
  public String getLabelName() {
    return "GitOrigin-RevId";
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
    public GitOrigin withOptions(Options options) throws ConfigValidationException {
      return withOptions(options, GitRepository.CURRENT_PROCESS_ENVIRONMENT);
    }

    @VisibleForTesting
    GitOrigin withOptions(Options options, Map<String, String> environment)
        throws ConfigValidationException {
      ConfigValidationException.checkNotMissing(url, "url");

      GitOptions gitConfig = options.get(GitOptions.class);

      Path gitRepoStorage = FileSystems.getDefault().getPath(gitConfig.gitRepoStorage);
      Path gitDir = gitRepoStorage.resolve(PERCENT_ESCAPER.escape(url));
      Console console = options.get(GeneralOptions.class).console();
      return new GitOrigin(console, GitRepository.bareRepo(gitDir, options, environment), url, ref);
    }
  }
}
