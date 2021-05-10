/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.hg;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.Origin.Reader.ChangesResponse.noChanges;
import static com.google.copybara.util.Glob.affectsRoots;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Change;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.Origin.Reader.ChangesResponse.EmptyReason;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.hg.HgRepository.HgLogEntry;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * A class for manipulating Hg repositories
 */
public class HgOrigin implements Origin<HgRevision> {

  public static final Pattern COMPLETE_SHA1_PATTERN = Pattern.compile("[a-f0-9]{40}");

  private final GeneralOptions generalOptions;
  private final HgOptions hgOptions;
  private final String repoUrl;
  @Nullable private final String configRef;
  private final HgOriginOptions hgOriginOptions;

  private HgOrigin(
      GeneralOptions generalOptions,
      HgOptions hgOptions,
      String repoUrl,
      @Nullable String ref,
      HgOriginOptions hgOriginOptions) {
    this.generalOptions = generalOptions;
    this.hgOptions = hgOptions;
    this.repoUrl = CharMatcher.is('/').trimTrailingFrom(checkNotNull(repoUrl));
    this.configRef = ref;
    this.hgOriginOptions = hgOriginOptions;
  }

  @VisibleForTesting
  public HgRepository getRepository() throws RepoException {
    return hgOptions.cachedBareRepoForUrl(repoUrl);
  }

  /**
   * Resolves a hg changeset reference to a revision. Pulls revision into repo.
   */
  @Override
  public HgRevision resolve(@Nullable String reference) throws RepoException, ValidationException {
    HgRepository repo = getRepository();
    String ref = reference;
    if (Strings.isNullOrEmpty(ref)) {
      if (Strings.isNullOrEmpty(configRef)) {
        throw new CannotResolveRevisionException("No source reference was passed through the"
            + " command line and the default reference is empty");
      }
      ref = configRef;
    }

    // Avoid fetch if a SHA-1 is passed and we already have in our local repo.
    if (COMPLETE_SHA1_PATTERN.matcher(ref).matches()) {
      try {
        return repo.identify(ref);
      } catch (CannotResolveRevisionException ignore) {
        // Not present locally. Pull from remote instead.
      }
    }
    // Fetch all instead of an specific ref:
    //  - It is usually faster
    //  - fetching an specific ref creates a local tag 'tip' instead of the original name. Then
    //  the next hg identify call needs to be aware of using tip (but not always, for example
    //  if user tries to resolve '0'
    repo.pullFromRef(repoUrl, /*ref=*/ null);
    return repo.identify(ref);
  }

  static class ReaderImpl implements Reader<HgRevision> {

    private final String repoUrl;
    private final HgOptions hgOptions;
    private final Authoring authoring;
    private final GeneralOptions generalOptions;
    private final HgOriginOptions hgOriginOptions;
    final Glob originFiles;

    ReaderImpl(String repoUrl, HgOptions hgOptions, Authoring authoring,
        GeneralOptions generalOptions, HgOriginOptions hgOriginOptions, Glob originFiles) {
      this.repoUrl = checkNotNull(repoUrl);
      this.hgOptions = hgOptions;
      this.authoring = authoring;
      this.generalOptions = generalOptions;
      this.hgOriginOptions = hgOriginOptions;
      this.originFiles = originFiles;
    }

    private ChangeReader.Builder changeReaderBuilder() throws RepoException {
      return ChangeReader.Builder.forOrigin(getRepository(), authoring, generalOptions.console());
    }

    protected HgRepository getRepository() throws RepoException {
      return hgOptions.cachedBareRepoForUrl(repoUrl);
    }

    @Override
    public void checkout(HgRevision revision, Path workDir)
        throws RepoException, ValidationException {
      HgRepository repo = getRepository();
      String revId = revision.getGlobalId();
      repo.pullFromRef(repoUrl, revId);
      repo.cleanUpdate(revId);
      try {
        FileUtil.deleteRecursively(workDir);
        repo.archive(workDir.toString()); // update the working directory
      } catch (RepoException e) {
        if (e.getMessage().contains("abort: no files match the archive pattern")) {
          throw new ValidationException("The origin repository is empty", e);
        }
        throw e;
      } catch (IOException e) {
        throw new RepoException("Error checking out " + repoUrl, e);
      }

      hgOriginOptions.maybeRunCheckoutHook(workDir, generalOptions);
    }

    @Override
    public ChangesResponse<HgRevision> changes(@Nullable HgRevision fromRef, HgRevision toRef)
      throws RepoException {
      String fromRefExpression = fromRef == null ? "null" : fromRef.getGlobalId();
      // The "<from>::<to>" part is to filter out unrelated history. The "only()" bit is
      // so we include commits merged in from a side branch.
      String refRange =
          String.format(
              "only(%s::%s, %s)", fromRefExpression, toRef.getGlobalId(), fromRefExpression);

      try {
        ChangeReader reader = changeReaderBuilder().build();
        ImmutableList<Change<HgRevision>> changes = reader.run(refRange);

        if (!changes.isEmpty()) {
          return ChangesResponse.forChangesWithMerges(changes);
        }
        if (fromRef == null) {
          return noChanges(EmptyReason.NO_CHANGES);
        }
        return noChanges(getEmptyReason(fromRef.getGlobalId(), toRef.getGlobalId()));
      } catch (ValidationException e) {
        throw new RepoException(
            String.format("Error querying changes: %s", e.getMessage()), e.getCause());
      }
    }

    private EmptyReason getEmptyReason(String fromRef, String toRef)
        throws RepoException, ValidationException {
      Preconditions.checkNotNull(fromRef);
      Preconditions.checkNotNull(toRef);
      ImmutableList<HgLogEntry> logEntries = getRepository().log()
          .withReferenceExpression(String.format("ancestor(%s, %s)", fromRef, toRef))
          .run();

      if (logEntries.isEmpty()) {
        /* If fromRef equals toRef and there are no common ancestors, there must be no changes */
        if (fromRef.equals(toRef)) {
          return EmptyReason.NO_CHANGES;
        }

        /* No common ancestors */
        return EmptyReason.UNRELATED_REVISIONS;
      }

      /* fromRef is an ancestor of toRef but changes are irrelevant */
      if (logEntries.get(0).getGlobalId().equals(fromRef)) {
        return EmptyReason.NO_CHANGES;
      }

      /* toRef is an ancestor of fromRef*/
      if (logEntries.get(0).getGlobalId().equals(toRef)) {
        return EmptyReason.TO_IS_ANCESTOR;
      }

      /* fromRef and toRef share an ancestor but are not directly related to each other*/
      return EmptyReason.UNRELATED_REVISIONS;
    }

    @Override
    public Change<HgRevision> change(HgRevision ref) throws RepoException, EmptyChangeException {
      ImmutableList<Change<HgRevision>> changes;

      try {
        ChangeReader reader = changeReaderBuilder().setLimit(1).build();
        changes = reader.run(ref.getGlobalId());
      } catch (ValidationException e){
        throw new RepoException(String.format("Error getting change: %s", e.getMessage()));
      }

      if (changes.isEmpty()) {
        throw new EmptyChangeException(
            String.format("%s reference cannot be found", ref.asString()));
      }

      Change<HgRevision> rev = changes.get(0);

      return new Change<>(ref, rev.getAuthor(), rev.getMessage(), rev.getDateTime(),
          rev.getLabels(), rev.getChangeFiles(), rev.isMerge(), rev.getParents());
    }

    @Override
    public void visitChanges(HgRevision start, ChangesVisitor visitor) throws RepoException {
      ChangeReader.Builder queryChanges = changeReaderBuilder();
      ImmutableSet<String> roots = originFiles.roots();

      HgVisitorUtil.visitChanges(start,
          input -> affectsRoots(roots, input.getChangeFiles())
          ? visitor.visit(input)
          : VisitResult.CONTINUE,
          queryChanges,
          generalOptions,
          /*type*/"origin",
          hgOptions.visitChangeDepth);
    }
  }

  @Override
  public Reader<HgRevision> newReader(Glob originFiles, Authoring authoring) {
    return new ReaderImpl(
        repoUrl, hgOptions, authoring, generalOptions, hgOriginOptions, originFiles);
  }

  @Override
  public String toString() {
    return String.format("HgOrigin{url = %s, ref = %s}", repoUrl, configRef);
  }

  @Override
  public String getLabelName() {
    return HgRepository.HG_ORIGIN_REV_ID;
  }

  @Override
  public String getType() {
    return "hg.origin";
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
            .put("type", getType())
            .put("url", repoUrl);
    if (configRef != null) {
      builder.put("ref", configRef);
    }
    return builder.build();
  }

  /**
   * Builds a new {@link HgOrigin}
   */
  static HgOrigin newHgOrigin(Options options, String url, String ref) {
    return new HgOrigin(options.get(GeneralOptions.class), options.get(HgOptions.class), url,
        ref, options.get(HgOriginOptions.class));
  }

}
